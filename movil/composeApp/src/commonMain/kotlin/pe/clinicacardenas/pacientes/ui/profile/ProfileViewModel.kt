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

data class ProfileState(val saving: String? = null, val message: String? = null, val loggingOut: Boolean = false,
    val editing: Boolean = false, val phone: String = "", val email: String = "", val errors: Map<String, String> = emptyMap(), val busy: Boolean = false)

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

    fun startEdit() = _state.update { it.copy(editing = true, phone = patient.value?.phone ?: "", email = patient.value?.email ?: "", errors = emptyMap(), message = null) }
    fun cancelEdit() = _state.update { it.copy(editing = false, errors = emptyMap()) }
    fun editPhone(v: String) = _state.update { it.copy(phone = v.filter(Char::isDigit).take(9)) }
    fun editEmail(v: String) = _state.update { it.copy(email = v.trim()) }

    /** El paciente actualiza su celular y correo; nombre, documento y fecha de nacimiento no (vienen de su DNI). */
    fun saveContact() {
        val s = _state.value
        _state.update { it.copy(busy = true, errors = emptyMap()) }
        viewModelScope.launch {
            try {
                val r = repo.updateContact(s.phone, s.email)
                runCatching { repo.refreshProfile() }
                _state.update { it.copy(editing = false, message = r.message) }
            } catch (e: ApiException) {
                _state.update { it.copy(errors = e.fields, message = e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
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
