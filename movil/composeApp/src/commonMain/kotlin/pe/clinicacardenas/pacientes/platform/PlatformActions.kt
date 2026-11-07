package pe.clinicacardenas.pacientes.platform

/** Recordatorio local programado en el teléfono. */
data class Reminder(val id: Int, val atEpochMillis: Long, val title: String, val body: String)

/**
 * Funciones propias de cada sistema operativo. Android las implementa con Intents, AlarmManager y
 * notificaciones; iOS con UIApplication y UNUserNotificationCenter.
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
}
