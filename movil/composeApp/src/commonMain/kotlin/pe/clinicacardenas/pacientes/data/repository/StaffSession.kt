package pe.clinicacardenas.pacientes.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.clinicacardenas.pacientes.data.local.LocalStore
import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.remote.ApiJson
import pe.clinicacardenas.pacientes.data.remote.StaffApi
import pe.clinicacardenas.pacientes.data.remote.StaffLoginRequest
import pe.clinicacardenas.pacientes.data.remote.StaffUserDto

/**
 * Sesión del personal de la clínica. Guarda el token y el usuario en SQLDelight y la preferencia de
 * desbloqueo con huella. Si el servidor responde 401 (token vencido), se cierra la sesión.
 */
class StaffSession(private val store: LocalStore) {
    private val _user = MutableStateFlow(read())
    val user: StateFlow<StaffUserDto?> = _user.asStateFlow()

    val token: String? get() = store.get(KEY_TOKEN)

    var biometric: Boolean
        get() = store.get(KEY_BIO) == "1"
        set(v) = store.put(KEY_BIO, if (v) "1" else null)

    fun can(vararg perms: String): Boolean = _user.value?.permissions?.any { it in perms } == true

    fun start(token: String, user: StaffUserDto) {
        store.put(KEY_TOKEN, token)
        update(user)
    }

    fun update(user: StaffUserDto) {
        store.put(KEY_USER, ApiJson.encodeToString(StaffUserDto.serializer(), user))
        _user.value = user
    }

    fun clear() {
        store.put(KEY_TOKEN, null)
        store.put(KEY_USER, null)
        _user.value = null
    }

    private fun read(): StaffUserDto? {
        if (store.get(KEY_TOKEN) == null) return null
        return store.get(KEY_USER)?.let { runCatching { ApiJson.decodeFromString(StaffUserDto.serializer(), it) }.getOrNull() }
    }

    private companion object {
        const val KEY_TOKEN = "personal.token"
        const val KEY_USER = "personal.usuario"
        const val KEY_BIO = "personal.huella"
    }
}

/** Envoltura de StaffApi que cierra la sesión si el token venció. */
class StaffRepository(val api: StaffApi, private val session: StaffSession) {
    suspend fun <T> guarded(block: suspend StaffApi.() -> T): T = try {
        api.block()
    } catch (e: ApiException) {
        if (e.status == 401 && session.token != null) session.clear()
        throw e
    }

    suspend fun login(username: String, password: String, code: String?) {
        val s = api.login(StaffLoginRequest(username.trim(), password, code?.trim()?.ifBlank { null }))
        session.start(s.token, s.user)
    }

    suspend fun refreshUser() {
        runCatching { session.update(guarded { me() }) }
    }

    fun logout() = session.clear()
}
