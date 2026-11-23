package pe.clinicacardenas.pacientes.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.clinicacardenas.pacientes.domain.error.DomainError
import pe.clinicacardenas.pacientes.domain.model.Appointment
import pe.clinicacardenas.pacientes.domain.model.BookingRequest
import pe.clinicacardenas.pacientes.domain.model.OperationState
import pe.clinicacardenas.pacientes.domain.model.OperationType
import pe.clinicacardenas.pacientes.domain.model.SyncResult
import pe.clinicacardenas.pacientes.domain.model.SyncStatus
import pe.clinicacardenas.pacientes.domain.repository.AppointmentLocalRepository
import pe.clinicacardenas.pacientes.domain.repository.AppointmentRemoteRepository
import pe.clinicacardenas.pacientes.domain.repository.Clock
import pe.clinicacardenas.pacientes.domain.repository.KeyGenerator

/** Las citas que dibuja la pantalla salen siempre de la base local. */
class ObserveAppointmentsUseCase(private val local: AppointmentLocalRepository) {
    operator fun invoke(): Flow<List<Appointment>> = local.observeAppointments()
}

/**
 * Sincronización offline-first.
 *
 * 1. **Sube primero** lo pendiente, en orden de creación. Bajar antes borraría lo creado sin conexión.
 * 2. Solo si la subida no se cortó por falta de red, **baja** las citas del servicio y las guarda.
 *
 * Reintento acotado: como máximo [MAX_ATTEMPTS] intentos por operación, con espera creciente (30 s, 60 s, 120 s…).
 * Se reintenta solo lo que falló por la red o por el servicio; un rechazo de negocio (400/409) marca la operación
 * como fallida al instante, con su motivo visible. Política de conflictos: manda el servicio.
 */
class SyncAppointmentsUseCase(
    private val local: AppointmentLocalRepository,
    private val remote: AppointmentRemoteRepository,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    suspend operator fun invoke(): SyncResult = mutex.withLock {
        _running.value = true
        try {
            var sent = 0
            var retried = 0
            var failed = 0
            var offline = false
            for (op in local.readyOperations(clock.now())) {
                try {
                    val serverId = when (op.type) {
                        OperationType.CONFIRMAR -> { remote.confirm(op.appointmentId); null }
                        OperationType.RESERVAR -> remote.book(op.payload, op.key)
                    }
                    local.completeOperation(op, serverId)
                    sent++
                } catch (e: DomainError) {
                    val attempts = op.attempts + 1
                    if (e.retryable && attempts < MAX_ATTEMPTS) {
                        local.scheduleRetry(op, attempts, clock.now() + backoff(attempts), e.message)
                        retried++
                    } else {
                        local.failOperation(op, attempts, e.message)
                        failed++
                    }
                    if (e is DomainError.SinConexion) {
                        offline = true
                        break // sin red no tiene sentido intentar las demás ahora
                    }
                }
            }
            var pulled = false
            if (!offline) {
                try {
                    local.replaceSynced(remote.fetchAppointments())
                    local.setLastSync(clock.now())
                    pulled = true
                } catch (e: DomainError) {
                    if (e is DomainError.SinConexion) offline = true else throw e
                }
            }
            SyncResult(sent, retried, failed, pulled, offline)
        } finally {
            _running.value = false
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val BASE_DELAY_MS = 30_000L

        /** Espera antes del intento n: 30 s, 60 s, 120 s, 240 s. */
        fun backoff(attempt: Int): Long = BASE_DELAY_MS shl (attempt - 1).coerceIn(0, 10)
    }
}

/** Estado de la sincronización para la pantalla principal: pendientes, fallidas y antigüedad de los datos. */
class ObserveSyncStatusUseCase(private val local: AppointmentLocalRepository, private val sync: SyncAppointmentsUseCase) {
    operator fun invoke(): Flow<SyncStatus> = combine(local.observeOperations(), local.observeLastSync(), sync.running) { ops, last, running ->
        SyncStatus(
            pending = ops.count { it.state == OperationState.PENDIENTE },
            failed = ops.filter { it.state == OperationState.FALLIDA },
            lastSyncAt = last,
            running = running,
        )
    }
}

/** Confirmar asistencia: se guarda primero en el teléfono y luego se intenta enviar. */
class ConfirmAppointmentUseCase(
    private val local: AppointmentLocalRepository,
    private val sync: SyncAppointmentsUseCase,
    private val keys: KeyGenerator,
    private val clock: Clock,
) {
    suspend operator fun invoke(appointmentId: Long): SyncResult {
        require(appointmentId > 0) { "Solo se confirman citas ya registradas en la clínica." }
        local.markConfirmedPending(appointmentId)
        local.enqueue(OperationType.CONFIRMAR, appointmentId, "", keys.newKey(), clock.now())
        return sync()
    }
}

/**
 * Reservar una cita: se registra en el teléfono con un id provisional negativo y queda pendiente.
 * Si hay red se envía en el acto; si no, se envía en la próxima sincronización.
 */
class RequestAppointmentUseCase(
    private val local: AppointmentLocalRepository,
    private val sync: SyncAppointmentsUseCase,
    private val keys: KeyGenerator,
    private val clock: Clock,
    private val encode: (BookingRequest) -> String,
) {
    suspend operator fun invoke(req: BookingRequest): Pair<Long, SyncResult> {
        val now = clock.now()
        val provisionalId = -now
        local.insertProvisional(
            Appointment(
                id = provisionalId,
                startAt = req.startAt,
                endAt = req.startAt,
                durationMin = req.durationMin,
                reason = req.reasonLabel,
                status = "POR_ENVIAR",
                dentistName = req.dentistName,
                dentistSpecialty = req.dentistSpecialty,
                dentistColor = req.dentistColor,
                dentistId = req.dentistId,
                upcoming = true,
                pending = true,
            ),
        )
        local.enqueue(OperationType.RESERVAR, provisionalId, encode(req), keys.newKey(), now)
        return provisionalId to sync()
    }
}

/** Reintentar o descartar una operación que no se pudo enviar. */
class ResolveFailedOperationUseCase(private val local: AppointmentLocalRepository, private val sync: SyncAppointmentsUseCase) {
    suspend fun retry(operationId: Long): SyncResult {
        local.reactivate(operationId)
        return sync()
    }

    suspend fun discard(operationId: Long) = local.discard(operationId)
}

/** Programa los recordatorios del teléfono con las citas vigentes de la base local. */
class ScheduleRemindersUseCase(private val scheduler: pe.clinicacardenas.pacientes.domain.repository.ReminderScheduler) {
    operator fun invoke(appointments: List<Appointment>, clinicName: String): Int =
        if (scheduler.permission() == pe.clinicacardenas.pacientes.domain.repository.PermissionStatus.CONCEDIDO ||
            scheduler.permission() == pe.clinicacardenas.pacientes.domain.repository.PermissionStatus.NO_PREGUNTADO
        ) scheduler.schedule(appointments.filter { it.upcoming && !it.isProvisional && (it.status == "PROGRAMADA" || it.status == "CONFIRMADA") }, clinicName)
        else 0
}
