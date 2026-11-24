package pe.clinicacardenas.pacientes.data.repository

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import pe.clinicacardenas.pacientes.data.local.LocalStore
import pe.clinicacardenas.pacientes.data.remote.*
import pe.clinicacardenas.pacientes.data.remote.AccountDto
import pe.clinicacardenas.pacientes.data.remote.ActivateRequest
import pe.clinicacardenas.pacientes.data.remote.ApiClient
import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.remote.CodeRequest
import pe.clinicacardenas.pacientes.data.remote.CodeResponse
import pe.clinicacardenas.pacientes.data.remote.LoginRequest
import pe.clinicacardenas.pacientes.data.remote.PingDto
import pe.clinicacardenas.pacientes.platform.PlatformActions

/**
 * Repositorio de las funciones en línea del paciente (cuenta, pagos, documentos, reservas y reprogramación).
 * Las citas y su sincronización offline-first están en el dominio (casos de uso) y en AppointmentLocal/Remote.
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

    suspend fun treatments(): pe.clinicacardenas.pacientes.data.remote.TreatmentsDto = guarded { api.treatments() }
    suspend fun consents(): pe.clinicacardenas.pacientes.data.remote.ConsentsDto = guarded { api.consents() }
    suspend fun consentPdf(id: Long): ByteArray = guarded { api.consentPdf(id) }
    suspend fun ownFileUrl(id: Long): String = api.absolute(guarded { api.ownFileLink(id) }.url)
    suspend fun fileRequests(): List<pe.clinicacardenas.pacientes.data.remote.FileRequestDto> = guarded { api.fileRequests() }
    suspend fun uploadRequest(id: Long, name: String, mime: String, bytes: ByteArray) = guarded { api.uploadRequest(id, name, mime, bytes) }
    suspend fun updateContact(phone: String, email: String) = guarded { api.updateContact(pe.clinicacardenas.pacientes.data.remote.ContactRequest(phone, email)) }

    suspend fun legal(code: String): pe.clinicacardenas.pacientes.data.remote.LegalDto = api.legal(code)

    suspend fun requestCode(docType: String, docNumber: String, birthDate: String): CodeResponse =
        api.requestCode(CodeRequest(docType, docNumber.trim(), birthDate))

    suspend fun activate(docType: String, docNumber: String, code: String, password: String, dataConsent: Boolean) {
        session.start(api.activate(ActivateRequest(docType, docNumber.trim(), code.trim(), password, dataConsent)))
    }

    // Las citas ya no se piden aquí: las lee la pantalla desde la base local (ObserveAppointmentsUseCase)
    // y las actualiza la sincronización (SyncAppointmentsUseCase). Ver domain/usecase.

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

    suspend fun onlineConfig(): OnlineConfigDto = runCatching { api.onlineConfig() }.getOrDefault(OnlineConfigDto())

    suspend fun onlinePay(amount: Double): OnlineChargeDto = guarded { api.onlinePay(amount) }

    suspend fun onlineStatus(id: Long): OnlineChargeDto = guarded { api.onlineStatus(id) }

    suspend fun documents(): DocumentsDto = guarded { api.documents() }

    suspend fun fileUrl(id: Long): String = api.absolute(guarded { api.fileLink(id) }.url)

    suspend fun invoiceUrl(id: Long): String = api.absolute(guarded { api.invoiceLink(id) }.url)

    suspend fun promotions(): List<pe.clinicacardenas.pacientes.data.remote.PromoDto> = guarded { api.promotions() }

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

    companion object {
        private const val KEY_LAST_NOTICE = "avisos.ultimo"

        fun epochMillis(iso: String): Long =
            LocalDateTime.parse(iso.take(19)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }
}
