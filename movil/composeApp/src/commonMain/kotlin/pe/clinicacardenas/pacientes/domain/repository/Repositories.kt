package pe.clinicacardenas.pacientes.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.clinicacardenas.pacientes.domain.model.Appointment
import pe.clinicacardenas.pacientes.domain.model.OperationType
import pe.clinicacardenas.pacientes.domain.model.PendingOperation

/**
 * Citas guardadas en el teléfono: la fuente única de verdad. La implementación (SQLDelight) vive en la capa de datos;
 * el dominio solo conoce este contrato.
 */
interface AppointmentLocalRepository {
    /** Citas observables: la pantalla se redibuja sola cuando cambia la base local. */
    fun observeAppointments(): Flow<List<Appointment>>

    fun observeOperations(): Flow<List<PendingOperation>>

    fun observeLastSync(): Flow<Long?>

    /** Reemplaza las citas sincronizadas por las del servicio, sin tocar las que tienen cambios pendientes. */
    suspend fun replaceSynced(items: List<Appointment>)

    suspend fun markConfirmedPending(appointmentId: Long)

    suspend fun insertProvisional(appointment: Appointment)

    suspend fun enqueue(type: OperationType, appointmentId: Long, payload: String, key: String, now: Long): Long

    /** Operaciones listas para enviar ahora (estado PENDIENTE y espera cumplida), en orden de creación. */
    suspend fun readyOperations(now: Long): List<PendingOperation>

    /** La operación llegó al servicio. Para una reserva, la cita provisional toma el id definitivo. */
    suspend fun completeOperation(op: PendingOperation, serverId: Long?)

    suspend fun scheduleRetry(op: PendingOperation, attempts: Int, nextAttemptAt: Long, error: String)

    /** Se agotaron los intentos o el servicio la rechazó: queda marcada y visible. */
    suspend fun failOperation(op: PendingOperation, attempts: Int, error: String)

    /** El paciente pidió reintentar una operación fallida. */
    suspend fun reactivate(operationId: Long)

    /** El paciente descartó una operación fallida: se borra y, si era una reserva provisional, también su cita. */
    suspend fun discard(operationId: Long)

    suspend fun setLastSync(at: Long)
}

/** Citas en el servicio de la clínica (API REST). */
interface AppointmentRemoteRepository {
    suspend fun fetchAppointments(): List<Appointment>

    suspend fun confirm(appointmentId: Long)

    /** Crea la reserva; la misma clave nunca crea dos citas. Devuelve el id definitivo. */
    suspend fun book(payload: String, key: String): Long
}

/** Almacén seguro del token de sesión (Keystore en Android, Keychain en iOS). */
interface TokenStorage {
    fun read(key: String): String?
    fun write(key: String, value: String?)
}

/** Reloj y generador de claves inyectables, para poder probar los casos de uso con tiempos controlados. */
fun interface Clock {
    fun now(): Long
}

fun interface KeyGenerator {
    fun newKey(): String
}

/** Estado del permiso de notificaciones (los tres estados que exige la especificación, más "aún no se preguntó"). */
enum class PermissionStatus { NO_PREGUNTADO, CONCEDIDO, DENEGADO, DENEGADO_PARA_SIEMPRE }

/**
 * Capacidad nativa: recordatorios locales de las citas (AlarmManager + notificaciones en Android,
 * UNUserNotificationCenter en iOS). El dominio solo conoce esta interfaz.
 */
interface ReminderScheduler {
    fun permission(): PermissionStatus
    fun requestPermission(onResult: (PermissionStatus) -> Unit)
    fun openSettings()

    /** Reemplaza los recordatorios programados (24 h y 2 h antes de cada cita vigente). Devuelve cuántos quedaron. */
    fun schedule(appointments: List<Appointment>, clinicName: String): Int
}
