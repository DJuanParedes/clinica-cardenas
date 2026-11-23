package pe.clinicacardenas.pacientes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import pe.clinicacardenas.pacientes.domain.error.DomainError
import pe.clinicacardenas.pacientes.domain.model.Appointment
import pe.clinicacardenas.pacientes.domain.model.OperationState
import pe.clinicacardenas.pacientes.domain.model.OperationType
import pe.clinicacardenas.pacientes.domain.model.PendingOperation
import pe.clinicacardenas.pacientes.domain.repository.AppointmentLocalRepository
import pe.clinicacardenas.pacientes.domain.repository.AppointmentRemoteRepository
import pe.clinicacardenas.pacientes.domain.repository.Clock
import pe.clinicacardenas.pacientes.domain.repository.KeyGenerator

/** Base local en memoria con la misma semántica que la implementación SQLDelight. */
class FakeLocal : AppointmentLocalRepository {
    val appointments = MutableStateFlow<List<Appointment>>(emptyList())
    val operations = MutableStateFlow<List<PendingOperation>>(emptyList())
    val lastSync = MutableStateFlow<Long?>(null)
    private var nextOpId = 1L

    override fun observeAppointments(): Flow<List<Appointment>> = appointments
    override fun observeOperations(): Flow<List<PendingOperation>> = operations
    override fun observeLastSync(): Flow<Long?> = lastSync

    override suspend fun replaceSynced(items: List<Appointment>) {
        val pending = appointments.value.filter { it.pending }
        val pendingIds = pending.map { it.id }.toSet()
        appointments.value = pending + items.filter { it.id !in pendingIds }
    }

    override suspend fun markConfirmedPending(appointmentId: Long) {
        appointments.value = appointments.value.map { if (it.id == appointmentId) it.copy(status = "CONFIRMADA", canConfirm = false, pending = true, syncError = null) else it }
    }

    override suspend fun insertProvisional(appointment: Appointment) {
        appointments.value = appointments.value + appointment.copy(pending = true)
    }

    override suspend fun enqueue(type: OperationType, appointmentId: Long, payload: String, key: String, now: Long): Long {
        val op = PendingOperation(nextOpId++, type, appointmentId, payload, key, OperationState.PENDIENTE, 0, 0, null, now)
        operations.value = operations.value + op
        return op.id
    }

    override suspend fun readyOperations(now: Long) =
        operations.value.filter { it.state == OperationState.PENDIENTE && it.nextAttemptAt <= now }.sortedBy { it.id }

    override suspend fun completeOperation(op: PendingOperation, serverId: Long?) {
        operations.value = operations.value.filter { it.id != op.id }
        appointments.value = appointments.value.filter { serverId == null || it.id != serverId }.map {
            if (it.id == op.appointmentId) it.copy(id = serverId ?: it.id, pending = false, syncError = null) else it
        }
    }

    override suspend fun scheduleRetry(op: PendingOperation, attempts: Int, nextAttemptAt: Long, error: String) {
        operations.value = operations.value.map { if (it.id == op.id) it.copy(attempts = attempts, nextAttemptAt = nextAttemptAt, error = error) else it }
    }

    override suspend fun failOperation(op: PendingOperation, attempts: Int, error: String) {
        operations.value = operations.value.map { if (it.id == op.id) it.copy(state = OperationState.FALLIDA, attempts = attempts, error = error) else it }
        appointments.value = appointments.value.map { if (it.id == op.appointmentId) it.copy(syncError = error) else it }
    }

    override suspend fun reactivate(operationId: Long) {
        operations.value = operations.value.map { if (it.id == operationId) it.copy(state = OperationState.PENDIENTE, attempts = 0, nextAttemptAt = 0, error = null) else it }
    }

    override suspend fun discard(operationId: Long) {
        val op = operations.value.first { it.id == operationId }
        operations.value = operations.value.filter { it.id != operationId }
        appointments.value = if (op.appointmentId < 0) appointments.value.filter { it.id != op.appointmentId } else appointments.value
    }

    override suspend fun setLastSync(at: Long) { lastSync.value = at }
}

/** Servicio remoto simulado: registra el orden de las llamadas y respeta la clave de idempotencia. */
class FakeRemote : AppointmentRemoteRepository {
    val log = mutableListOf<String>()
    var offline = false
    var failNextWith: DomainError? = null
    val server = mutableListOf<Appointment>()
    private val byKey = mutableMapOf<String, Long>()
    private var nextId = 100L

    private fun check() {
        if (offline) throw DomainError.SinConexion()
        failNextWith?.let { failNextWith = null; throw it }
    }

    override suspend fun fetchAppointments(): List<Appointment> {
        check(); log += "fetch"; return server.toList()
    }

    override suspend fun confirm(appointmentId: Long) {
        check(); log += "confirm:$appointmentId"
        for (i in server.indices) if (server[i].id == appointmentId) server[i] = server[i].copy(status = "CONFIRMADA", canConfirm = false)
    }

    override suspend fun book(payload: String, key: String): Long {
        check(); log += "book:$key"
        return byKey.getOrPut(key) {
            val id = nextId++
            server += appointment(id, payload)
            id
        }
    }
}

class FakeClock(var time: Long = 1_700_000_000_000L) : Clock {
    override fun now(): Long = time
}

class SeqKeys : KeyGenerator {
    private var n = 0
    override fun newKey(): String = "k${++n}"
}

fun appointment(id: Long, startAt: String = "2026-12-01T10:00", status: String = "PROGRAMADA") = Appointment(
    id = id, startAt = startAt, endAt = startAt, durationMin = 30, reason = "Control", status = status,
    dentistName = "Dra. Prueba", dentistSpecialty = "General", dentistColor = "#00A3D4", canConfirm = status == "PROGRAMADA",
)
