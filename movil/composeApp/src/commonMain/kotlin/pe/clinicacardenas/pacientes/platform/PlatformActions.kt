package pe.clinicacardenas.pacientes.platform

/** Recordatorio local programado en el teléfono. */
data class Reminder(val id: Int, val atEpochMillis: Long, val title: String, val body: String)

/** Origen de un archivo que el personal sube a la ficha del paciente. */
enum class PickSource { CAMARA, GALERIA, DOCUMENTO }

/** Archivo elegido o fotografiado en el teléfono, listo para subir. */
class PickedFile(val name: String, val mimeType: String, val bytes: ByteArray)

/**
 * Funciones propias de cada sistema operativo. Android las implementa con Intents, AlarmManager,
 * notificaciones, la cámara y BiometricPrompt; iOS con UIApplication, UNUserNotificationCenter y LocalAuthentication.
 */
interface PlatformActions {
    val name: String

    /** Servidor por defecto: en el emulador de Android Studio la PC es 10.0.2.2. */
    val defaultServerUrl: String

    fun openUrl(url: String)

    fun dial(phone: String)

    fun addToCalendar(title: String, startEpochMillis: Long, endEpochMillis: Long, location: String, details: String)

    /** Reemplaza los recordatorios programados por esta lista. Devuelve cuántos quedaron activos. */
    fun scheduleReminders(reminders: List<Reminder>): Int

    /** Muestra una notificación ahora mismo (avisos nuevos de la clínica). */
    fun notify(id: Int, title: String, body: String)

    /** Toma una foto con la cámara, o elige una imagen o documento del teléfono. Devuelve null si se cancela. */
    fun pickFile(source: PickSource, onResult: (PickedFile?) -> Unit)

    /** Abre un archivo descargado (PDF o imagen) con la app del teléfono. */
    fun openFile(name: String, mimeType: String, bytes: ByteArray)

    /** ¿El teléfono tiene huella, rostro o bloqueo de pantalla configurado? */
    val canUseBiometrics: Boolean

    /** Pide la huella / rostro (o el PIN del teléfono). */
    fun authenticate(reason: String, onResult: (Boolean) -> Unit)
}
