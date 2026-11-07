package pe.clinicacardenas.pacientes.platform

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import pe.clinicacardenas.pacientes.MainActivity
import pe.clinicacardenas.pacientes.R
import java.io.File

class AndroidPlatformActions(private val context: Context) : PlatformActions {
    override val name = "Android"

    /** 10.0.2.2 es la computadora anfitriona vista desde el emulador de Android Studio. */
    override val defaultServerUrl = "http://10.0.2.2:9081"

    private fun start(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No hay una aplicación para abrir esto.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun openUrl(url: String) = start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    override fun dial(phone: String) = start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' })))

    override fun addToCalendar(title: String, startEpochMillis: Long, endEpochMillis: Long, location: String, details: String) {
        start(
            Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startEpochMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endEpochMillis)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                .putExtra(CalendarContract.Events.DESCRIPTION, details),
        )
    }

    /** Reemplaza los recordatorios anteriores por los nuevos usando AlarmManager. */
    override fun scheduleReminders(reminders: List<Reminder>): Int {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences("recordatorios", Context.MODE_PRIVATE)
        prefs.getStringSet("ids", emptySet())!!.forEach { id ->
            alarms.cancel(pending(id.toInt(), "", ""))
        }
        reminders.forEach { r ->
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.atEpochMillis, pending(r.id, r.title, r.body))
        }
        prefs.edit().putStringSet("ids", reminders.map { it.id.toString() }.toSet()).apply()
        return reminders.size
    }

    override fun notify(id: Int, title: String, body: String) = notify(context, id, title, body)

    override fun pickFile(source: PickSource, onResult: (PickedFile?) -> Unit) {
        val activity = MainActivity.current
        if (activity == null) {
            onResult(null)
            return
        }
        activity.pick(source, onResult)
    }

    override fun openFile(name: String, mimeType: String, bytes: ByteArray) {
        val dir = File(context.cacheDir, "compartidos").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val safe = name.replace(Regex("[^\\w.\\- áéíóúñÁÉÍÓÚÑ]"), "_").ifBlank { "archivo" }
        val file = File(dir, safe)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".archivos", file)
        start(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    override val canUseBiometrics: Boolean
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            return keyguard.isDeviceSecure
        }

    override fun authenticate(reason: String, onResult: (Boolean) -> Unit) {
        val activity = MainActivity.current
        if (activity == null || !canUseBiometrics) {
            onResult(false)
            return
        }
        activity.authenticate(reason, onResult)
    }

    private fun pending(id: Int, title: String, body: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, ReminderReceiver::class.java)
                .putExtra(ReminderReceiver.EXTRA_ID, id)
                .putExtra(ReminderReceiver.EXTRA_TITLE, title)
                .putExtra(ReminderReceiver.EXTRA_BODY, body),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val CHANNEL_ID = "recordatorios"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH),
                )
            }
        }

        fun notify(context: Context, id: Int, title: String, body: String) {
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setColor(0xFF00B0E0.toInt())
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(id, n)
            } catch (e: SecurityException) {
                // El usuario no dio permiso de notificaciones.
            }
        }
    }
}
