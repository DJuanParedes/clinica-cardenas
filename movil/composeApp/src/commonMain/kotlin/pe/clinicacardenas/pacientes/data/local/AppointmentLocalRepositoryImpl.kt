package pe.clinicacardenas.pacientes.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import pe.clinicacardenas.pacientes.db.Cita
import pe.clinicacardenas.pacientes.db.Operacion_pendiente
import pe.clinicacardenas.pacientes.domain.model.Appointment
import pe.clinicacardenas.pacientes.domain.model.OperationState
import pe.clinicacardenas.pacientes.domain.model.OperationType
import pe.clinicacardenas.pacientes.domain.model.PendingOperation
import pe.clinicacardenas.pacientes.domain.repository.AppointmentLocalRepository

/** Implementación SQLDelight del repositorio local de citas: fuente única de verdad de la app. */
class AppointmentLocalRepositoryImpl(private val store: LocalStore) : AppointmentLocalRepository {
    private val q = store.q
    private val lastSync = MutableStateFlow(store.get(KEY_LAST_SYNC)?.toLongOrNull())

    override fun observeAppointments(): Flow<List<Appointment>> =
        q.citas().asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { it.toDomain() } }

    override fun observeOperations(): Flow<List<PendingOperation>> =
        q.operaciones().asFlow().mapToList(Dispatchers.Default).map { rows -> rows.map { it.toDomain() } }

    override fun observeLastSync(): Flow<Long?> = lastSync.asStateFlow()

    override suspend fun replaceSynced(items: List<Appointment>) = io {
        store.db.transaction {
            val pending = q.idsPendientes().executeAsList().toSet()
            q.borrarCitasSincronizadas()
            items.filter { it.id !in pending }.forEach { q.insertarCita(it.toRow()) }
        }
    }

    override suspend fun markConfirmedPending(appointmentId: Long) = io {
        q.marcarCitaPendiente("CONFIRMADA", appointmentId)
    }

    override suspend fun insertProvisional(appointment: Appointment) = io {
        q.insertarCita(appointment.copy(pending = true).toRow())
    }

    override suspend fun enqueue(type: OperationType, appointmentId: Long, payload: String, key: String, now: Long): Long = io {
        store.db.transactionWithResult {
            q.encolarOperacion(type.name, appointmentId, payload, key, now)
            q.operaciones().executeAsList().last().id
        }
    }

    override suspend fun readyOperations(now: Long): List<PendingOperation> = io {
        q.operacionesListas(now).executeAsList().map { it.toDomain() }
    }

    override suspend fun completeOperation(op: PendingOperation, serverId: Long?) = io {
        store.db.transaction {
            q.borrarOperacion(op.id)
            if (serverId != null && op.appointmentId < 0) {
                q.borrarCita(serverId)
                q.reemplazarIdCita(serverId, op.appointmentId)
            } else {
                q.limpiarPendienteCita(op.appointmentId)
            }
        }
    }

    override suspend fun scheduleRetry(op: PendingOperation, attempts: Int, nextAttemptAt: Long, error: String) = io {
        q.reprogramarOperacion(attempts.toLong(), nextAttemptAt, error, op.id)
    }

    override suspend fun failOperation(op: PendingOperation, attempts: Int, error: String) = io {
        store.db.transaction {
            q.fallarOperacion(attempts.toLong(), error, op.id)
            q.marcarErrorCita(error, op.appointmentId)
        }
    }

    override suspend fun reactivate(operationId: Long) = io {
        val op = q.operacion(operationId).executeAsOneOrNull() ?: return@io
        store.db.transaction {
            q.reactivarOperacion(operationId)
            q.marcarErrorCita(null, op.cita_id)
        }
    }

    override suspend fun discard(operationId: Long) = io {
        val op = q.operacion(operationId).executeAsOneOrNull() ?: return@io
        store.db.transaction {
            q.borrarOperacion(operationId)
            if (op.cita_id < 0) q.borrarCita(op.cita_id) else q.limpiarPendienteCita(op.cita_id)
        }
    }

    override suspend fun setLastSync(at: Long) = io {
        store.put(KEY_LAST_SYNC, at.toString())
        lastSync.value = at
    }

    /** Al cerrar sesión. */
    fun resetLastSync() {
        store.put(KEY_LAST_SYNC, null)
        lastSync.value = null
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.Default) { block() }

    private companion object {
        const val KEY_LAST_SYNC = "sync.ultima"
    }
}

internal fun Cita.toDomain() = Appointment(
    id = id,
    startAt = inicio,
    endAt = fin,
    durationMin = duracion.toInt(),
    reason = motivo,
    status = estado,
    dentistName = odontologo,
    dentistSpecialty = especialidad,
    dentistColor = color,
    dentistId = odontologo_id,
    canConfirm = puede_confirmar == 1L && pendiente == 0L,
    canReschedule = puede_reprogramar == 1L && pendiente == 0L,
    canCancel = puede_cancelar == 1L && pendiente == 0L,
    upcoming = proxima == 1L,
    pending = pendiente == 1L,
    syncError = error_sync,
)

internal fun Appointment.toRow() = Cita(
    id = id,
    inicio = startAt,
    fin = endAt,
    duracion = durationMin.toLong(),
    motivo = reason,
    estado = status,
    odontologo = dentistName,
    especialidad = dentistSpecialty,
    color = dentistColor,
    puede_confirmar = if (canConfirm) 1L else 0L,
    proxima = if (upcoming) 1L else 0L,
    pendiente = if (pending) 1L else 0L,
    error_sync = syncError,
    puede_reprogramar = if (canReschedule) 1L else 0L,
    puede_cancelar = if (canCancel) 1L else 0L,
    odontologo_id = dentistId,
)

internal fun Operacion_pendiente.toDomain() = PendingOperation(
    id = id,
    type = OperationType.valueOf(tipo),
    appointmentId = cita_id,
    payload = datos,
    key = clave,
    state = if (estado == "FALLIDA") OperationState.FALLIDA else OperationState.PENDIENTE,
    attempts = intentos.toInt(),
    nextAttemptAt = proximo_intento,
    error = error,
    createdAt = creada,
)
