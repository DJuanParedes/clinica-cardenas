package pe.clinicacardenas.pacientes.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Recibe la alarma programada y muestra el recordatorio de la cita como notificación local. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (title.isEmpty()) return
        AndroidPlatformActions.notify(context, intent.getIntExtra(EXTRA_ID, 0), title, intent.getStringExtra(EXTRA_BODY).orEmpty())
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
    }
}
