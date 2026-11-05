package pe.clinicacardenas.pacientes.platform

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDateComponents
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

class IosPlatformActions : PlatformActions {
    override val name = "iOS"

    /** En el simulador de iOS, localhost es la propia Mac. */
    override val defaultServerUrl = "http://localhost:9081"

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override fun openUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(nsUrl, emptyMap<Any?, Any?>(), null)
    }

    override fun dial(phone: String) = openUrl("tel:" + phone.filter { it.isDigit() || it == '+' })

    /** iOS no permite abrir el formulario de calendario sin EventKitUI; usamos Google Calendar en el navegador. */
    override fun addToCalendar(title: String, startEpochMillis: Long, endEpochMillis: Long, location: String, details: String) {
        fun stamp(ms: Long): String {
            val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC)
            fun p(n: Int) = n.toString().padStart(2, '0')
            return "${t.year}${p(t.monthNumber)}${p(t.dayOfMonth)}T${p(t.hour)}${p(t.minute)}00Z"
        }
        fun enc(s: String) = s.encodeToByteArray().joinToString("") { b ->
            val c = b.toInt() and 0xFF
            if (c.toChar().isLetterOrDigit() && c < 128) c.toChar().toString() else "%" + c.toString(16).uppercase().padStart(2, '0')
        }
        openUrl(
            "https://calendar.google.com/calendar/render?action=TEMPLATE&text=${enc(title)}" +
                "&dates=${stamp(startEpochMillis)}/${stamp(endEpochMillis)}&location=${enc(location)}&details=${enc(details)}",
        )
    }

    override fun scheduleReminders(reminders: List<Reminder>): Int {
        center.removeAllPendingNotificationRequests()
        if (reminders.isEmpty()) return 0
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge) { granted, _ ->
            if (!granted) return@requestAuthorizationWithOptions
            reminders.forEach { r ->
                val t = Instant.fromEpochMilliseconds(r.atEpochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
                val date = NSDateComponents().apply {
                    year = t.year.toLong()
                    month = t.monthNumber.toLong()
                    day = t.dayOfMonth.toLong()
                    hour = t.hour.toLong()
                    minute = t.minute.toLong()
                }
                val content = UNMutableNotificationContent().apply {
                    title = r.title
                    body = r.body
                    sound = UNNotificationSound.defaultSound
                }
                val request = UNNotificationRequest.requestWithIdentifier(
                    "cita-${r.id}",
                    content,
                    UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(date, repeats = false),
                )
                center.addNotificationRequest(request, null)
            }
        }
        return reminders.size
    }
}
