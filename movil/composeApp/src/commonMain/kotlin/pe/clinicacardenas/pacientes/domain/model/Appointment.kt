package pe.clinicacardenas.pacientes.domain.model

/**
 * Cita del paciente tal como la entiende la app, independiente del API y de la base local.
 * id < 0: reserva creada sin conexión, con identificador provisional (el servicio nunca emite ids negativos).
 */
data class Appointment(
    val id: Long,
    val startAt: String,
    val endAt: String,
    val durationMin: Int,
    val reason: String,
    val status: String,
    val dentistName: String,
    val dentistSpecialty: String,
    val dentistColor: String,
    val dentistId: Long? = null,
    val canConfirm: Boolean = false,
    val canReschedule: Boolean = false,
    val canCancel: Boolean = false,
    val upcoming: Boolean = true,
    /** Hay un cambio hecho en el teléfono que todavía no llegó al servicio. */
    val pending: Boolean = false,
    /** Motivo por el que el cambio no se pudo enviar (se muestra en la tarjeta). */
    val syncError: String? = null,
) {
    val isProvisional: Boolean get() = id < 0
}

/** Datos de una reserva pedida por el paciente. */
data class BookingRequest(
    val dentistId: Long,
    val dentistName: String,
    val dentistSpecialty: String,
    val dentistColor: String,
    val startAt: String,
    val durationMin: Int,
    val reasonCode: String,
    val reasonLabel: String,
    val notes: String?,
)
