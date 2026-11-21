package pe.clinicacardenas.pacientes.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.clinicacardenas.pacientes.data.local.LocalStore
import pe.clinicacardenas.pacientes.data.remote.ApiJson
import pe.clinicacardenas.pacientes.data.remote.PatientDto
import pe.clinicacardenas.pacientes.data.remote.SessionDto
import pe.clinicacardenas.pacientes.platform.PlatformActions

/** Sesión del paciente, guardada en SQLDelight. */
class SessionRepository(private val store: LocalStore, private val platform: PlatformActions) {

    private val _patient = MutableStateFlow(readPatient())
    val patient: StateFlow<PatientDto?> = _patient.asStateFlow()

    val token: String? get() = store.get(KEY_TOKEN)

    /** Servidor de la clínica: viene fijo dentro del APK, el usuario no tiene que escribirlo. */
    val serverUrl: String get() = platform.defaultServerUrl

    fun start(session: SessionDto) {
        store.put(KEY_TOKEN, session.token)
        updatePatient(session.patient)
    }

    fun updatePatient(p: PatientDto) {
        store.put(KEY_PATIENT, ApiJson.encodeToString(PatientDto.serializer(), p))
        _patient.value = p
    }

    fun clear() {
        store.put(KEY_TOKEN, null)
        store.put(KEY_PATIENT, null)
        store.clearAppointments()
        platform.scheduleReminders(emptyList())
        _patient.value = null
    }

    private fun readPatient(): PatientDto? {
        if (store.get(KEY_TOKEN) == null) return null
        val json = store.get(KEY_PATIENT) ?: return null
        return runCatching { ApiJson.decodeFromString(PatientDto.serializer(), json) }.getOrNull()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_PATIENT = "paciente"
    }
}
