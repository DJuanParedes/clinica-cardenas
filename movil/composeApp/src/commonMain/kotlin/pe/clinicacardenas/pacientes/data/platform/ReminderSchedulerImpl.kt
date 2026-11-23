package pe.clinicacardenas.pacientes.data.platform

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import pe.clinicacardenas.pacientes.domain.model.Appointment
import pe.clinicacardenas.pacientes.domain.repository.PermissionStatus
import pe.clinicacardenas.pacientes.domain.repository.ReminderScheduler
import pe.clinicacardenas.pacientes.platform.PlatformActions
import pe.clinicacardenas.pacientes.platform.Reminder

/** Implementación de los recordatorios sobre las acciones nativas de cada plataforma. */
class ReminderSchedulerImpl(private val platform: PlatformActions) : ReminderScheduler {

    override fun permission(): PermissionStatus = runCatching { PermissionStatus.valueOf(platform.notificationPermission()) }
        .getOrDefault(PermissionStatus.NO_PREGUNTADO)

    override fun requestPermission(onResult: (PermissionStatus) -> Unit) =
        platform.requestNotificationPermission { onResult(runCatching { PermissionStatus.valueOf(it) }.getOrDefault(PermissionStatus.DENEGADO)) }

    override fun openSettings() = platform.openAppSettings()

    override fun schedule(appointments: List<Appointment>, clinicName: String): Int {
        val now = Clock.System.now().toEpochMilliseconds()
        val list = mutableListOf<Reminder>()
        for (a in appointments) {
            val start = LocalDateTime.parse(a.startAt.take(19)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            val hour = a.startAt.substringAfter('T').take(5)
            for ((hours, label, slot) in listOf(Triple(24, "mañana", 1), Triple(2, "en 2 horas", 2))) {
                val at = start - hours * 3_600_000L
                if (at <= now + 60_000) continue
                list += Reminder(
                    id = (a.id * 10 + slot).toInt(),
                    atEpochMillis = at,
                    title = "Tu cita es $label · $hour",
                    body = "${a.reason} con ${a.dentistName} en $clinicName.",
                )
            }
        }
        return platform.scheduleReminders(list)
    }
}
