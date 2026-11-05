package pe.clinicacardenas.pacientes.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.remote.AppointmentsDto
import pe.clinicacardenas.pacientes.data.repository.AppointmentsResult
import pe.clinicacardenas.pacientes.data.repository.ClinicRepository

data class HomeState(
    val loading: Boolean = true,
    val data: AppointmentsDto? = null,
    val offline: Boolean = false,
    val reminders: Int = 0,
    val confirming: Long? = null,
    val error: String? = null,
    val message: String? = null,
)

/** HU-16/HU-21/HU-22 desde el teléfono: ver próximas citas, confirmarlas y recibir recordatorios. */
class HomeViewModel(private val repo: ClinicRepository) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.data == null, error = null) }
        viewModelScope.launch {
            try {
                apply(repo.appointments())
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun confirm(id: Long) {
        _state.update { it.copy(confirming = id, message = null) }
        viewModelScope.launch {
            try {
                apply(repo.confirm(id))
                _state.update { it.copy(message = "¡Listo! Tu asistencia quedó confirmada.") }
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.message) }
            } finally {
                _state.update { it.copy(confirming = null) }
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun apply(r: AppointmentsResult) =
        _state.update { it.copy(data = r.data, offline = r.offline, reminders = r.reminders, error = null) }
}
