package pe.clinicacardenas.pacientes.domain.model

/** Tipos de escritura que la app puede hacer sin conexión. */
enum class OperationType {
    /** Confirmar asistencia: idempotente por naturaleza (confirmar dos veces deja la cita igual). */
    CONFIRMAR,

    /** Reservar una cita: se vuelve idempotente con la clave Idempotency-Key que guarda la operación. */
    RESERVAR,
}

enum class OperationState { PENDIENTE, FALLIDA }

/** Escritura guardada en el teléfono a la espera de llegar al servicio. */
data class PendingOperation(
    val id: Long,
    val type: OperationType,
    val appointmentId: Long,
    /** Datos de la operación (para RESERVAR, la solicitud serializada). */
    val payload: String,
    /** Clave única de la operación: el servicio la usa para no ejecutarla dos veces. */
    val key: String,
    val state: OperationState,
    val attempts: Int,
    val nextAttemptAt: Long,
    val error: String?,
    val createdAt: Long,
)

/** Lo que la pantalla principal muestra sobre la sincronización. */
data class SyncStatus(
    val pending: Int = 0,
    val failed: List<PendingOperation> = emptyList(),
    val lastSyncAt: Long? = null,
    val running: Boolean = false,
)

/** Resultado de una pasada de sincronización. */
data class SyncResult(val sent: Int, val retried: Int, val failed: Int, val pulled: Boolean, val offline: Boolean)
