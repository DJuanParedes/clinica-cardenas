package pe.clinicacardenas.pacientes.data.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import pe.clinicacardenas.pacientes.data.local.LocalStore
import pe.clinicacardenas.pacientes.data.remote.*
import pe.clinicacardenas.pacientes.data.remote.AccountDto
import pe.clinicacardenas.pacientes.data.remote.ActivateRequest
import pe.clinicacardenas.pacientes.data.remote.ApiClient
import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.remote.AppointmentDto
import pe.clinicacardenas.pacientes.data.remote.AppointmentsDto
import pe.clinicacardenas.pacientes.data.remote.CodeRequest
import pe.clinicacardenas.pacientes.data.remote.CodeResponse
import pe.clinicacardenas.pacientes.data.remote.DentistDto
import pe.clinicacardenas.pacientes.data.remote.LoginRequest
import pe.clinicacardenas.pacientes.data.remote.PingDto
import pe.clinicacardenas.pacientes.db.Cita
import pe.clinicacardenas.pacientes.platform.PlatformActions
import pe.clinicacardenas.pacientes.platform.Reminder

/** Citas que se muestran en la app y de dónde salieron. */
data class AppointmentsResult(val data: AppointmentsDto, val offline: Boolean, val reminders: Int)

/**
 * Repositorio único de la app (patrón Repository): combina el API REST, la base local y las funciones del teléfono.
 * Si el servidor responde 401 se cierra la sesión.
 */
class ClinicRepository(
    private val api: ApiClient,
    private val store: LocalStore,
    private val session: SessionRepository,
    private val platform: PlatformActions,
) {
    private suspend fun <T> guarded(block: suspend () -> T): T = try {
        block()
    } catch (e: ApiException) {
        if (e.status == 401 && session.token != null) session.clear()
        throw e
    }

    suspend fun ping(url: String): PingDto = api.ping(url)

    suspend fun login(docType: String, docNumber: String, password: String) {
        session.start(api.login(LoginRequest(docType, docNumber.trim(), password)))
    }

    suspend fun requestCode(docType: String, docNumber: String, birthDate: String): CodeResponse =
        api.requestCode(CodeRequest(docType, docNumber.trim(), birthDate))

    suspend fun activate(docType: String, docNumber: String, code: String, password: String, dataConsent: Boolean) {
        session.start(api.activate(ActivateRequest(docType, docNumber.trim(), code.trim(), password, dataConsent)))
    }

    /** Descarga las citas, las guarda para usarlas sin conexión y programa los recordatorios del teléfono. */
    suspend fun appointments(): AppointmentsResult = try {
        val data = guarded { api.appointments() }
        cache(data)
        AppointmentsResult(data, offline = false, reminders = scheduleReminders(data))
    } catch (e: ApiException) {
        if (e.code != "SIN_CONEXION") throw e
        val cached = store.cachedAppointments()
        if (cached.isEmpty()) throw e
        val upcoming = cached.filter { it.proxima == 1L }.map { it.toDto() }
        AppointmentsResult(
            AppointmentsDto(next = upcoming.firstOrNull(), upcoming = upcoming, past = cached.filter { it.proxima == 0L }.map { it.toDto() }.reversed()),
            offline = true,
            reminders = 0,
        )
    }

    suspend fun confirm(id: Long): AppointmentsResult {
        val data = guarded { api.confirm(id) }
        cache(data)
        return AppointmentsResult(data, offline = false, reminders = scheduleReminders(data))
    }

    suspend fun account(): AccountDto = guarded { api.account() }

    // ---------------------------------------------------------------- reservas, avisos, pagos y documentos
    suspend fun bookingOptions(): BookingOptionsDto = guarded { api.bookingOptions() }

    suspend fun bookingSlots(dentistId: Long, date: String, reasonCode: String, excludeId: Long?): PortalSlotsDto =
        guarded { api.bookingSlots(dentistId, date, reasonCode, excludeId) }

    suspend fun book(dentistId: Long, startAt: String, reasonCode: String, notes: String?): String =
        guarded { api.book(BookRequest(dentistId, startAt, reasonCode, notes?.ifBlank { null })) }.message

    suspend fun reschedule(id: Long, dentistId: Long?, startAt: String): String =
        guarded { api.reschedule(id, RescheduleRequest(dentistId, startAt)) }.message

    suspend fun cancel(id: Long, reason: String): String = guarded { api.cancel(id, CancelRequest(reason)) }.message

    suspend fun notices(): NoticesDto = guarded { api.notices() }

    suspend fun markNoticesRead() = api.markNoticesRead()

    suspend fun pay(body: PayRequest): PayResultDto = guarded { api.pay(body) }

    suspend fun documents(): DocumentsDto = guarded { api.documents() }

    suspend fun fileUrl(id: Long): String = api.absolute(guarded { api.fileLink(id) }.url)

    suspend fun invoiceUrl(id: Long): String = api.absolute(guarded { api.invoiceLink(id) }.url)

    suspend fun pendingSurvey(): PendingSurveyDto? = guarded { api.pendingSurvey() }.pending

    suspend fun sendSurvey(appointmentId: Long, score: Int, comment: String?): String =
        guarded { api.sendSurvey(SurveyRequest(appointmentId, score, comment?.ifBlank { null })) }.message

    /** Avisos nuevos desde la última revisión: se muestran como notificación del teléfono. */
    suspend fun checkNewNotices(): Int {
        val list = guarded { api.notices() }
        val lastSeen = store.get(KEY_LAST_NOTICE)?.toLongOrNull() ?: 0L
        val fresh = list.items.filter { it.readAt == null && it.id > lastSeen }
        fresh.take(3).forEach { platform.notify((it.id % 100000).toInt() + 500000, it.title, it.body) }
        list.items.maxOfOrNull { it.id }?.let { store.put(KEY_LAST_NOTICE, it.toString()) }
        return list.unread.toInt()
    }

    suspend fun updateChannel(channel: String) {
        session.updatePatient(guarded { api.updateChannel(channel) })
    }

    suspend fun refreshProfile() {
        session.updatePatient(guarded { api.me() })
    }

    suspend fun logout() {
        api.logout()
        session.clear()
    }

    private fun cache(data: AppointmentsDto) {
        val upcomingIds = data.upcoming.map { it.id }.toSet()
        store.replaceAppointments((data.upcoming + data.past).distinctBy { it.id }.map { it.toCita(it.id in upcomingIds) })
    }

    private fun scheduleReminders(data: AppointmentsDto): Int {
        val clinic = session.patient.value?.clinic?.name ?: "la clínica"
        val now = Clock.System.now().toEpochMilliseconds()
        val list = mutableListOf<Reminder>()
        for (a in data.upcoming) {
            if (a.status != "PROGRAMADA" && a.status != "CONFIRMADA") continue
            val start = epochMillis(a.startAt)
            val hour = a.startAt.substringAfter('T').take(5)
            for ((hours, label, slot) in listOf(Triple(24, "mañana", 1), Triple(2, "en 2 horas", 2))) {
                val at = start - hours * 3_600_000L
                if (at <= now + 60_000) continue
                list += Reminder(
                    id = (a.id * 10 + slot).toInt(),
                    atEpochMillis = at,
                    title = "Tu cita es $label · $hour",
                    body = "${a.reason} con ${a.dentist.fullName} en $clinic.",
                )
            }
        }
        return platform.scheduleReminders(list)
    }

    companion object {
        private const val KEY_LAST_NOTICE = "avisos.ultimo"

        fun epochMillis(iso: String): Long =
            LocalDateTime.parse(iso.take(19)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }
}

private fun AppointmentDto.toCita(upcoming: Boolean) = Cita(
    id = id,
    inicio = startAt,
    fin = endAt,
    duracion = durationMin.toLong(),
    motivo = reason,
    estado = status,
    odontologo = dentist.fullName,
    especialidad = dentist.specialty,
    color = dentist.color,
    puede_confirmar = if (canConfirm) 1L else 0L,
    proxima = if (upcoming) 1L else 0L,
)

private fun Cita.toDto() = AppointmentDto(
    id = id,
    startAt = inicio,
    endAt = fin,
    durationMin = duracion.toInt(),
    reason = motivo,
    status = estado,
    dentist = DentistDto(odontologo, especialidad, color),
    canConfirm = false, // sin conexión no se puede confirmar
)
