package pe.clinicacardenas.pacientes.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.remote.PendingSurveyDto
import pe.clinicacardenas.pacientes.data.repository.ClinicRepository
import pe.clinicacardenas.pacientes.data.repository.SessionRepository
import pe.clinicacardenas.pacientes.domain.error.DomainError
import pe.clinicacardenas.pacientes.domain.model.Appointment
import pe.clinicacardenas.pacientes.domain.model.SyncStatus
import pe.clinicacardenas.pacientes.domain.repository.PermissionStatus
import pe.clinicacardenas.pacientes.domain.repository.ReminderScheduler
import pe.clinicacardenas.pacientes.domain.usecase.ConfirmAppointmentUseCase
import pe.clinicacardenas.pacientes.domain.usecase.ObserveAppointmentsUseCase
import pe.clinicacardenas.pacientes.domain.usecase.ObserveSyncStatusUseCase
import pe.clinicacardenas.pacientes.domain.usecase.ResolveFailedOperationUseCase
import pe.clinicacardenas.pacientes.domain.usecase.ScheduleRemindersUseCase
import pe.clinicacardenas.pacientes.domain.usecase.SyncAppointmentsUseCase

/** Estado único de la pantalla principal (UiState). */
data class HomeState(
    val loading: Boolean = true,
    val next: Appointment? = null,
    val upcoming: List<Appointment> = emptyList(),
    val past: List<Appointment> = emptyList(),
    val sync: SyncStatus = SyncStatus(),
    val offline: Boolean = false,
    val reminders: Int = 0,
    val permission: PermissionStatus = PermissionStatus.NO_PREGUNTADO,
    val confirming: Long? = null,
    val error: String? = null,
    val message: String? = null,
    val survey: PendingSurveyDto? = null,
    val surveySent: Boolean = false,
    val busy: Boolean = false,
) {
    val empty: Boolean get() = next == null && upcoming.isEmpty() && past.isEmpty()
}

/**
 * Pantalla principal del paciente (MVVM). Lee las citas de la base local (nunca espera a la red para dibujar),
 * sincroniza al abrir, cada 30 s mientras haya cambios pendientes y al pulsar "Actualizar".
 */
class HomeViewModel(
    private val observeAppointments: ObserveAppointmentsUseCase,
    private val observeSync: ObserveSyncStatusUseCase,
    private val sync: SyncAppointmentsUseCase,
    private val confirmAppointment: ConfirmAppointmentUseCase,
    private val resolveFailed: ResolveFailedOperationUseCase,
    private val scheduleReminders: ScheduleRemindersUseCase,
    private val reminders: ReminderScheduler,
    private val repo: ClinicRepository,
    private val session: SessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState(permission = reminders.permission()))
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeAppointments().collect { list ->
                val upcoming = list.filter { it.upcoming }.sortedBy { it.startAt }
                val past = list.filter { !it.upcoming }.sortedByDescending { it.startAt }
                val clinic = session.patient.value?.clinic?.name ?: "la clínica"
                val count = runCatching { scheduleReminders(list, clinic) }.getOrDefault(0)
                _state.update {
                    it.copy(
                        next = upcoming.firstOrNull { a -> a.status != "CANCELADA" },
                        upcoming = upcoming,
                        past = past,
                        reminders = count,
                        // con datos locales ya se dibuja; si la base está vacía se espera la primera sincronización
                        loading = it.loading && list.isEmpty() && it.sync.lastSyncAt == null,
                    )
                }
            }
        }
        viewModelScope.launch { observeSync().collect { s -> _state.update { it.copy(sync = s) } } }
        load()
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                if (_state.value.sync.pending > 0) runSync(quiet = true)
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            runSync(quiet = false)
            runCatching { repo.pendingSurvey() }.onSuccess { p -> _state.update { it.copy(survey = p) } }
        }
    }

    private suspend fun runSync(quiet: Boolean) {
        try {
            val r = sync()
            _state.update { it.copy(offline = r.offline, error = null, loading = false) }
        } catch (e: DomainError.SesionVencida) {
            session.clear()
        } catch (e: DomainError) {
            if (!quiet) _state.update { it.copy(error = e.message, loading = false) }
        }
    }

    fun confirm(id: Long) {
        _state.update { it.copy(confirming = id, message = null) }
        viewModelScope.launch {
            try {
                val r = confirmAppointment(id)
                _state.update {
                    it.copy(
                        offline = r.offline,
                        message = when {
                            r.offline -> "Confirmación guardada en tu teléfono: se enviará cuando vuelva la conexión."
                            r.failed > 0 -> "No se pudo confirmar. Revisa el aviso en la cita."
                            else -> "¡Listo! Tu asistencia quedó confirmada."
                        },
                    )
                }
            } catch (e: DomainError) {
                _state.update { it.copy(message = e.message) }
            } finally {
                _state.update { it.copy(confirming = null) }
            }
        }
    }

    fun retryFailed(operationId: Long) = viewModelScope.launch {
        runCatching { resolveFailed.retry(operationId) }
    }

    fun discardFailed(operationId: Long) = viewModelScope.launch {
        resolveFailed.discard(operationId)
    }

    /** Recordatorios: pedir el permiso o, si se negó para siempre, abrir los ajustes del sistema. */
    fun enableReminders() {
        when (_state.value.permission) {
            PermissionStatus.DENEGADO_PARA_SIEMPRE -> reminders.openSettings()
            else -> reminders.requestPermission { p -> _state.update { it.copy(permission = p) } }
        }
    }

    fun refreshPermission() = _state.update { it.copy(permission = reminders.permission()) }

    /** Cancela la cita (hasta 12 h antes). Requiere conexión: es una operación que la clínica debe validar. */
    fun cancel(id: Long, reason: String) {
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val msg = repo.cancel(id, reason.ifBlank { "Cancelada por el paciente desde la app" })
                _state.update { it.copy(message = msg) }
                runSync(quiet = true)
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** Encuesta de satisfacción de la última cita atendida. */
    fun sendSurvey(score: Int, comment: String) {
        val p = _state.value.survey ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                repo.sendSurvey(p.appointmentId, score, comment)
                _state.update { it.copy(survey = null, surveySent = true) }
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
}
