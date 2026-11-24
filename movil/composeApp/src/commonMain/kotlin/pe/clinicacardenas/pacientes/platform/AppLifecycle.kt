package pe.clinicacardenas.pacientes.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

/**
 * Estado de la app (primer plano o segundo plano) y última vez que el usuario tocó la pantalla.
 * Lo usan el bloqueo con huella al volver a la app y el cierre de sesión por inactividad del personal.
 */
object AppLifecycle {
    private val _foreground = MutableStateFlow(true)
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()

    /** Momento (epoch ms) en que la app pasó a segundo plano; 0 si está en primer plano. */
    var backgroundSince: Long = 0L
        private set

    var lastTouch: Long = now()
        private set

    fun now(): Long = Clock.System.now().toEpochMilliseconds()

    fun onStart() {
        _foreground.value = true
    }

    fun onStop() {
        backgroundSince = now()
        _foreground.value = false
    }

    fun touched() {
        lastTouch = now()
    }

    fun consumeBackground(): Long {
        val t = backgroundSince
        backgroundSince = 0L
        return t
    }
}
