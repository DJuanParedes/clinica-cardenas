package pe.clinicacardenas.pacientes.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.repository.ClinicRepository
import pe.clinicacardenas.pacientes.data.repository.SessionRepository

enum class LoginMode { INGRESAR, ACTIVAR, SERVIDOR }

data class LoginState(
    val mode: LoginMode = LoginMode.INGRESAR,
    val docType: String = "DNI",
    val docNumber: String = "",
    val password: String = "",
    val birthDate: String = "",
    val code: String = "",
    val newPassword: String = "",
    val codeSent: Boolean = false,
    val codeInfo: String? = null,
    val server: String = "",
    val serverInfo: String? = null,
    val serverOk: Boolean? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
)

/** HU del portal: ingreso con documento y contraseña, y activación de cuenta con código. */
class LoginViewModel(private val repo: ClinicRepository, private val session: SessionRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginState(server = session.serverUrl))
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun edit(f: LoginState.() -> LoginState) = _state.update { it.f().copy(error = null) }

    fun mode(m: LoginMode) = _state.update { it.copy(mode = m, error = null, fieldErrors = emptyMap(), server = session.serverUrl) }

    private fun validDoc(s: LoginState): String? = when {
        s.docType == "DNI" && !Regex("\\d{8}").matches(s.docNumber.trim()) -> "El DNI tiene 8 dígitos."
        s.docNumber.isBlank() -> "Ingresa tu número de documento."
        else -> null
    }

    fun login() {
        val s = _state.value
        val errors = buildMap {
            validDoc(s)?.let { put("docNumber", it) }
            if (s.password.isBlank()) put("password", "Ingresa tu contraseña.")
        }
        if (errors.isNotEmpty()) return _state.update { it.copy(fieldErrors = errors) }
        run { repo.login(s.docType, s.docNumber, s.password) }
    }

    fun requestCode() {
        val s = _state.value
        val errors = buildMap {
            validDoc(s)?.let { put("docNumber", it) }
            if (!Regex("\\d{2}/\\d{2}/\\d{4}").matches(s.birthDate.trim())) put("birthDate", "Usa el formato dd/mm/aaaa.")
        }
        if (errors.isNotEmpty()) return _state.update { it.copy(fieldErrors = errors) }
        val (d, m, y) = s.birthDate.trim().split("/")
        run {
            val r = repo.requestCode(s.docType, s.docNumber, "$y-$m-$d")
            val demo = r.demoCode?.let { "\nCódigo de demostración: $it" } ?: ""
            _state.update { it.copy(codeSent = true, codeInfo = r.message + demo, code = r.demoCode ?: it.code) }
        }
    }

    fun activate() {
        val s = _state.value
        val errors = buildMap {
            if (s.code.trim().length != 6) put("code", "El código tiene 6 dígitos.")
            if (s.newPassword.length < 8 || !s.newPassword.any { it.isDigit() } || !s.newPassword.any { it.isLetter() })
                put("newPassword", "Mínimo 8 caracteres, con letras y números.")
        }
        if (errors.isNotEmpty()) return _state.update { it.copy(fieldErrors = errors) }
        run { repo.activate(s.docType, s.docNumber, s.code, s.newPassword) }
    }

    /** Prueba la dirección del servidor antes de guardarla. */
    fun testServer() {
        val url = _state.value.server.trim()
        if (url.isBlank()) return _state.update { it.copy(fieldErrors = mapOf("server" to "Escribe la dirección del servidor.")) }
        run {
            val full = if (url.startsWith("http")) url else "http://$url"
            val ping = repo.ping(full)
            session.setServerUrl(full)
            _state.update { it.copy(serverOk = true, server = session.serverUrl, serverInfo = "Conectado con ${ping.clinic.name}. Dirección guardada.") }
        }
    }

    private fun run(block: suspend () -> Unit) {
        _state.update { it.copy(loading = true, error = null, fieldErrors = emptyMap(), serverInfo = null, serverOk = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message, fieldErrors = e.fields, serverOk = if (it.mode == LoginMode.SERVIDOR) false else null) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
