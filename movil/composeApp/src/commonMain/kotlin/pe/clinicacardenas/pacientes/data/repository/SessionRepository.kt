package pe.clinicacardenas.pacientes.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.clinicacardenas.pacientes.data.local.LocalStore
import pe.clinicacardenas.pacientes.data.remote.ApiJson
import pe.clinicacardenas.pacientes.data.remote.PatientDto
import pe.clinicacardenas.pacientes.data.remote.SessionDto
import pe.clinicacardenas.pacientes.domain.repository.TokenStorage
import pe.clinicacardenas.pacientes.platform.PlatformActions

/**
 * Sesión del paciente. El token va en el almacén seguro de la plataforma (TokenStorage: Keystore / Keychain);
 * en la base local solo quedan los datos visibles del paciente. Al reabrir la app la sesión se recupera sola.
 */
class SessionRepository(
    private val store: LocalStore,
    private val platform: PlatformActions,
    private val tokens: TokenStorage,
    private val onClear: () -> Unit = {},
) {
    init {
        // Versiones anteriores guardaban el token en SQLite: se mueve al almacén seguro y se borra de la base.
        store.get(KEY_TOKEN)?.let { old ->
            if (tokens.read(KEY_TOKEN) == null) tokens.write(KEY_TOKEN, old)
            store.put(KEY_TOKEN, null)
        }
    }

    private val _patient = MutableStateFlow(readPatient())
    val patient: StateFlow<PatientDto?> = _patient.asStateFlow()

    val token: String? get() = tokens.read(KEY_TOKEN)

    /** Servidor de la clínica: viene fijo dentro del APK, el usuario no tiene que escribirlo. */
    val serverUrl: String get() = platform.defaultServerUrl

    fun start(session: SessionDto) {
        tokens.write(KEY_TOKEN, session.token)
        updatePatient(session.patient)
    }

    fun updatePatient(p: PatientDto) {
        store.put(KEY_PATIENT, ApiJson.encodeToString(PatientDto.serializer(), p))
        _patient.value = p
    }

    fun clear() {
        tokens.write(KEY_TOKEN, null)
        store.put(KEY_PATIENT, null)
        store.clearPatientData()
        onClear()
        platform.scheduleReminders(emptyList())
        _patient.value = null
    }

    private fun readPatient(): PatientDto? {
        if (tokens.read(KEY_TOKEN) == null) return null
        val json = store.get(KEY_PATIENT) ?: return null
        return runCatching { ApiJson.decodeFromString(PatientDto.serializer(), json) }.getOrNull()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_PATIENT = "paciente"
    }
}
