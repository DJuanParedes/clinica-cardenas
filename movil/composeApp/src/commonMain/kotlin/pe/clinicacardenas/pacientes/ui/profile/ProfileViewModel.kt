package pe.clinicacardenas.pacientes.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.remote.PatientDto
import pe.clinicacardenas.pacientes.data.repository.ClinicRepository
import pe.clinicacardenas.pacientes.data.repository.SessionRepository
import pe.clinicacardenas.pacientes.platform.PlatformActions

data class ProfileState(val saving: String? = null, val message: String? = null, val loggingOut: Boolean = false)

/** HU-23: el paciente elige por dónde recibir los recordatorios; también datos de contacto de la clínica. */
class ProfileViewModel(
    private val repo: ClinicRepository,
    private val session: SessionRepository,
    val platform: PlatformActions,
) : ViewModel() {
    val patient: StateFlow<PatientDto?> = session.patient
    val serverUrl: String get() = session.serverUrl

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        viewModelScope.launch { runCatching { repo.refreshProfile() } }
    }

    fun setChannel(channel: String) {
        if (patient.value?.notifyChannel == channel) return
        _state.update { it.copy(saving = channel, message = null) }
        viewModelScope.launch {
            try {
                repo.updateChannel(channel)
                _state.update { it.copy(message = "Guardado. Te avisaremos por ${channelName(channel)}.") }
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.message) }
            } finally {
                _state.update { it.copy(saving = null) }
            }
        }
    }

    fun logout() {
        _state.update { it.copy(loggingOut = true) }
        viewModelScope.launch {
            runCatching { repo.logout() }
            session.clear()
        }
    }

    private fun channelName(c: String) = when (c) {
        "EMAIL" -> "correo"
        "AMBOS" -> "WhatsApp y correo"
        else -> "WhatsApp"
    }
}
