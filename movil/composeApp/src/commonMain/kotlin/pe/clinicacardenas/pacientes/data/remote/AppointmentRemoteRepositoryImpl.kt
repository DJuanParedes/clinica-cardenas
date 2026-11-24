package pe.clinicacardenas.pacientes.data.remote

import kotlinx.serialization.Serializable
import pe.clinicacardenas.pacientes.domain.model.Appointment
import pe.clinicacardenas.pacientes.domain.model.BookingRequest
import pe.clinicacardenas.pacientes.domain.repository.AppointmentRemoteRepository

/** Solicitud de reserva guardada en la cola mientras no hay conexión (se serializa en la operación pendiente). */
@Serializable
data class BookingPayload(
    val dentistId: Long,
    val dentistName: String,
    val dentistSpecialty: String,
    val dentistColor: String,
    val startAt: String,
    val durationMin: Int,
    val reasonCode: String,
    val reasonLabel: String,
    val notes: String? = null,
)

fun BookingRequest.encode(): String = ApiJson.encodeToString(
    BookingPayload.serializer(),
    BookingPayload(dentistId, dentistName, dentistSpecialty, dentistColor, startAt, durationMin, reasonCode, reasonLabel, notes),
)

/** Implementación REST del repositorio remoto de citas (DTO → dominio, errores → DomainError). */
class AppointmentRemoteRepositoryImpl(private val api: ApiClient) : AppointmentRemoteRepository {

    override suspend fun fetchAppointments(): List<Appointment> = domainCall {
        val data = api.appointments()
        val upcomingIds = data.upcoming.map { it.id }.toSet()
        (data.upcoming + data.past).distinctBy { it.id }.map { it.toDomain(it.id in upcomingIds) }
    }

    override suspend fun confirm(appointmentId: Long) {
        domainCall { api.confirm(appointmentId) }
    }

    override suspend fun book(payload: String, key: String): Long = domainCall {
        val p = ApiJson.decodeFromString(BookingPayload.serializer(), payload)
        val r = api.book(BookRequest(p.dentistId, p.startAt, p.reasonCode, p.notes?.ifBlank { null }), key)
        r.id ?: throw ApiException("La clínica no devolvió el número de la cita.", status = 500)
    }
}

fun AppointmentDto.toDomain(upcoming: Boolean) = Appointment(
    id = id,
    startAt = startAt,
    endAt = endAt,
    durationMin = durationMin,
    reason = reason,
    status = status,
    dentistName = dentist.fullName,
    dentistSpecialty = dentist.specialty,
    dentistColor = dentist.color,
    dentistId = dentistId,
    canConfirm = canConfirm,
    canReschedule = canReschedule,
    canCancel = canCancel,
    upcoming = upcoming,
)
