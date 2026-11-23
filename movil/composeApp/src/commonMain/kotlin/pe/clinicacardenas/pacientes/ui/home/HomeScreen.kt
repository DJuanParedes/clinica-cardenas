package pe.clinicacardenas.pacientes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import pe.clinicacardenas.pacientes.data.remote.PendingSurveyDto
import pe.clinicacardenas.pacientes.ui.booking.BookingScreen
import pe.clinicacardenas.pacientes.ui.booking.BookingTarget
import pe.clinicacardenas.pacientes.ui.components.Field
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import pe.clinicacardenas.pacientes.domain.model.Appointment
import pe.clinicacardenas.pacientes.domain.model.SyncStatus
import pe.clinicacardenas.pacientes.domain.repository.PermissionStatus
import pe.clinicacardenas.pacientes.data.remote.PatientDto
import pe.clinicacardenas.pacientes.data.repository.ClinicRepository
import pe.clinicacardenas.pacientes.platform.PlatformActions
import pe.clinicacardenas.pacientes.ui.components.Banner
import pe.clinicacardenas.pacientes.ui.components.Chip
import pe.clinicacardenas.pacientes.ui.components.ErrorState
import pe.clinicacardenas.pacientes.ui.components.Fmt
import pe.clinicacardenas.pacientes.ui.components.Loading
import pe.clinicacardenas.pacientes.ui.components.Panel
import pe.clinicacardenas.pacientes.ui.components.PrimaryButton
import pe.clinicacardenas.pacientes.ui.components.SecondaryButton
import pe.clinicacardenas.pacientes.ui.components.SectionTitle
import pe.clinicacardenas.pacientes.ui.theme.Brand

@Composable
fun HomeScreen(patient: PatientDto, vm: HomeViewModel = koinViewModel(), platform: PlatformActions = koinInject()) {
    val s by vm.state.collectAsState()
    var booking by remember { mutableStateOf<BookingTarget?>(null) }
    var cancelling by remember { mutableStateOf<Appointment?>(null) }
    booking?.let { t ->
        BookingScreen(t) { changed ->
            booking = null
            if (changed) vm.load()
        }
        return
    }
    cancelling?.let { a ->
        CancelDialog(a, onDismiss = { cancelling = null }) { reason ->
            cancelling = null
            vm.cancel(a.id, reason)
        }
    }
    val change: (Appointment) -> Unit = { a -> booking = BookingTarget(a.id, a.dentistId, a.durationMin, a.reason) }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshPermission() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Greeting(patient, onRefresh = vm::load) }
        if (s.offline) item { Banner("Sin conexión: trabajas con los datos guardados en tu teléfono. Lo que hagas se enviará al volver la red.", Brand.NaranjaSuave, Color(0xFF7A4600)) }
        item { SyncPanel(s.sync, s.offline, onSync = vm::load, onRetry = vm::retryFailed, onDiscard = vm::discardFailed) }
        if (s.permission != PermissionStatus.CONCEDIDO) item { PermissionCard(s.permission, vm::enableReminders) }
        s.message?.let { m -> item { Banner(m, Brand.VerdeSuave, Brand.Verde) } }
        s.survey?.let { p -> item { SurveyCard(p, s.busy, vm::sendSurvey) } }
        if (s.surveySent) item { Banner("¡Gracias por tu calificación! Nos ayuda a mejorar.", Brand.VerdeSuave, Brand.Verde) }
        when {
            s.loading -> item { Loading() }
            s.empty && s.error != null -> item { ErrorState(s.error ?: "No se pudieron cargar tus citas.", vm::load) }
            else -> {
                val next = s.next
                item {
                    if (next != null) NextCard(next, patient, s.confirming == next.id, vm::confirm, platform, change) { cancelling = it }
                    else EmptyNext(patient, platform)
                }
                item {
                    PrimaryButton("＋ Reservar una cita", { booking = BookingTarget() }, color = Brand.Naranja)
                }
                if (s.reminders > 0) item {
                    Text(
                        "🔔 ${s.reminders} recordatorio${if (s.reminders == 1) "" else "s"} programado${if (s.reminders == 1) "" else "s"} en este teléfono.",
                        color = Brand.Gris,
                        fontSize = 13.sp,
                    )
                }
                val others = s.upcoming.filter { it.id != next?.id }
                if (others.isNotEmpty()) {
                    item { SectionTitle("Próximas citas") }
                    items(others, key = { it.id }) { a -> AppointmentRow(a, s.confirming == a.id, vm::confirm, change) { cancelling = it } }
                }
                if (s.past.isNotEmpty()) {
                    item { SectionTitle("Historial") }
                    items(s.past.take(15), key = { "p${it.id}" }) { a -> AppointmentRow(a, false, null) }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun Greeting(patient: PatientDto, onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Hola, ${patient.firstName}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta)
            Text(patient.clinic.name, color = Brand.Tinta2, fontSize = 14.sp)
        }
        TextButton(onClick = onRefresh) { Text("Actualizar") }
    }
}

@Composable
private fun NextCard(
    a: Appointment,
    patient: PatientDto,
    confirming: Boolean,
    onConfirm: (Long) -> Unit,
    platform: PlatformActions,
    onChange: (Appointment) -> Unit,
    onCancel: (Appointment) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Brand.Azul, Brand.Celeste)), RoundedCornerShape(22.dp))
            .padding(20.dp),
    ) {
        Text("Tu próxima cita", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(Fmt.dayLabel(a.startAt).replaceFirstChar { it.uppercase() }, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("${Fmt.hour(a.startAt)} · ${a.durationMin} min", color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        Text(a.reason, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("${a.dentistName} · ${a.dentistSpecialty}", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        val (bg, fg) = Fmt.statusColors(a.status)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(Fmt.statusLabel[a.status] ?: a.status, bg, fg)
            if (a.pending) Chip("⏳ Pendiente de envío", Color.White, Brand.AzulOscuro)
        }
        a.syncError?.let { Text("No se pudo enviar: $it", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(16.dp))
        if (a.canConfirm) {
            PrimaryButton("Confirmar asistencia", { onConfirm(a.id) }, loading = confirming, color = Brand.Naranja)
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WhiteButton("📅 Calendario", Modifier.weight(1f)) {
                platform.addToCalendar(
                    title = "Cita dental: ${a.reason}",
                    startEpochMillis = ClinicRepository.epochMillis(a.startAt),
                    endEpochMillis = ClinicRepository.epochMillis(a.endAt),
                    location = "${patient.clinic.name}, ${patient.clinic.address}",
                    details = "Con ${a.dentistName}. Tel. ${patient.clinic.phone}",
                )
            }
            WhiteButton("💬 Escribir", Modifier.weight(1f)) {
                val msg = "Hola, soy ${patient.fullName}. Consulto por mi cita del ${Fmt.dayLabel(a.startAt).lowercase()} a las ${Fmt.hour(a.startAt)}."
                platform.openUrl(whatsapp(patient.clinic.phone, msg))
            }
        }
        if (a.canReschedule || a.canCancel) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (a.canReschedule) WhiteButton("🔁 Cambiar horario", Modifier.weight(1f)) { onChange(a) }
                if (a.canCancel) WhiteButton("✕ Cancelar", Modifier.weight(1f)) { onCancel(a) }
            }
        }
    }
}

@Composable
private fun WhiteButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
    ) { Text(text, color = Color.White, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun EmptyNext(patient: PatientDto, platform: PlatformActions) {
    Panel {
        Text("No tienes citas programadas", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Brand.Tinta)
        Text("Resérvala aquí abajo, o escríbenos o llámanos.", color = Brand.Tinta2)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("💬 WhatsApp", { platform.openUrl(whatsapp(patient.clinic.phone, "Hola, soy ${patient.fullName} y quiero reservar una cita.")) }, Modifier.weight(1f))
            SecondaryButton("📞 Llamar", { platform.dial(patient.clinic.phone) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppointmentRow(
    a: Appointment,
    confirming: Boolean,
    onConfirm: ((Long) -> Unit)?,
    onChange: ((Appointment) -> Unit)? = null,
    onCancel: ((Appointment) -> Unit)? = null,
) {
    Panel(padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.width(54.dp).background(Brand.CelesteSuave, RoundedCornerShape(12.dp)).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(Fmt.dayNumber(a.startAt), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Brand.AzulOscuro)
                Text(Fmt.monthShort(a.startAt), fontSize = 11.sp, color = Brand.AzulOscuro)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.reason, fontWeight = FontWeight.SemiBold, color = Brand.Tinta)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Fmt.color(a.dentistColor), RoundedCornerShape(50)))
                    Spacer(Modifier.width(6.dp))
                    Text("${Fmt.hour(a.startAt)} · ${a.dentistName}", color = Brand.Tinta2, fontSize = 13.sp)
                }
            }
            val (bg, fg) = Fmt.statusColors(a.status)
            Column(horizontalAlignment = Alignment.End) {
                Chip(Fmt.statusLabel[a.status] ?: a.status, bg, fg)
                if (a.pending) Text("⏳ por enviar", color = Brand.Gris, fontSize = 11.sp)
            }
        }
        a.syncError?.let { Text("No se pudo enviar: $it", color = Brand.Rojo, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        if (onConfirm != null && a.canConfirm) {
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Confirmar asistencia", { onConfirm(a.id) }, loading = confirming)
        }
        if ((onChange != null && a.canReschedule) || (onCancel != null && a.canCancel)) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onChange != null && a.canReschedule) SecondaryButton("Cambiar horario", { onChange(a) }, Modifier.weight(1f))
                if (onCancel != null && a.canCancel) SecondaryButton("Cancelar", { onCancel(a) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CancelDialog(a: Appointment, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Cancelar tu cita?") },
        text = {
            Column {
                Text("${a.reason} · ${Fmt.dayLabel(a.startAt)} ${Fmt.hour(a.startAt)} con ${a.dentistName}.", color = Brand.Tinta2)
                Spacer(Modifier.height(8.dp))
                Field(reason, { reason = it.take(200) }, "Motivo (opcional)")
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason) }) { Text("Sí, cancelar", color = Brand.Rojo) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("No") } },
    )
}

/** Encuesta: ¿cómo te atendimos? (1 a 5 estrellas y comentario). */
@Composable
private fun SurveyCard(p: PendingSurveyDto, busy: Boolean, onSend: (Int, String) -> Unit) {
    var score by remember(p.appointmentId) { mutableStateOf(0) }
    var comment by remember(p.appointmentId) { mutableStateOf("") }
    Panel {
        Text("¿Cómo te atendimos?", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Brand.Tinta)
        Text("${p.reason} con ${p.dentist} · ${Fmt.shortDate(p.startAt)}", color = Brand.Tinta2, fontSize = 13.sp)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { n ->
                Text(
                    if (n <= score) "★" else "☆",
                    fontSize = 34.sp,
                    color = if (n <= score) Brand.Naranja else Brand.Gris,
                    modifier = Modifier.clickable { score = n },
                )
            }
        }
        if (score > 0) {
            Field(comment, { comment = it.take(500) }, if (score <= 3) "¿Qué podemos mejorar?" else "Comentario (opcional)")
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Enviar calificación", { onSend(score, comment) }, loading = busy)
        }
    }
}

internal fun whatsapp(phone: String, text: String): String {
    var digits = phone.filter(Char::isDigit)
    if (digits.length == 9) digits = "51$digits"
    return "https://wa.me/$digits?text=" + encode(text)
}

private fun encode(s: String): String = buildString {
    s.encodeToByteArray().forEach { b ->
        val c = b.toInt() and 0xFF
        if ((c in 'a'.code..'z'.code) || (c in 'A'.code..'Z'.code) || (c in '0'.code..'9'.code) || c == '-'.code || c == '.'.code || c == '_'.code) append(c.toChar())
        else append('%').append(c.toString(16).uppercase().padStart(2, '0'))
    }
}

/** Estado de la sincronización: pendientes por enviar, antigüedad de los datos y lo que no se pudo enviar. */
@Composable
private fun SyncPanel(sync: SyncStatus, offline: Boolean, onSync: () -> Unit, onRetry: (Long) -> Unit, onDiscard: (Long) -> Unit) {
    val age = sync.lastSyncAt?.let { ageText(it) } ?: "todavía no se sincronizó"
    Panel(padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        sync.running -> "Sincronizando…"
                        sync.pending > 0 -> "⏳ ${sync.pending} cambio${if (sync.pending == 1) "" else "s"} por enviar"
                        offline -> "Sin conexión"
                        else -> "✓ Todo enviado"
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = if (sync.pending > 0 || offline) Color(0xFF9A5B00) else Brand.Verde,
                )
                Text("Datos actualizados $age", color = Brand.Gris, fontSize = 12.sp)
            }
            TextButton(onClick = onSync, enabled = !sync.running) { Text("Sincronizar") }
        }
        sync.failed.forEach { op ->
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().background(Brand.RojoSuave, RoundedCornerShape(10.dp)).padding(10.dp)) {
                Text(
                    (if (op.type.name == "RESERVAR") "Reserva" else "Confirmación") + " no enviada (${op.attempts} intento${if (op.attempts == 1) "" else "s"})",
                    color = Brand.Rojo,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                op.error?.let { Text(it, color = Brand.Rojo, fontSize = 12.sp) }
                Row {
                    TextButton(onClick = { onRetry(op.id) }) { Text("Reintentar") }
                    TextButton(onClick = { onDiscard(op.id) }) { Text("Descartar") }
                }
            }
        }
    }
}

private fun ageText(at: Long): String {
    val min = ((kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - at) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> "hace un momento"
        min < 60 -> "hace $min min"
        min < 1440 -> "hace ${min / 60} h"
        else -> "hace ${min / 1440} día${if (min / 1440 == 1L) "" else "s"}"
    }
}

/** Permiso de notificaciones en sus estados: sin preguntar, denegado y denegado para siempre. */
@Composable
private fun PermissionCard(p: PermissionStatus, onEnable: () -> Unit) {
    Panel(padding = PaddingValues(12.dp)) {
        Text("🔔 Recordatorios de tus citas", fontWeight = FontWeight.SemiBold, color = Brand.Tinta)
        Text(
            when (p) {
                PermissionStatus.DENEGADO_PARA_SIEMPRE -> "Las notificaciones están bloqueadas. Actívalas en los ajustes del teléfono para recibir los avisos 24 h y 2 h antes."
                PermissionStatus.DENEGADO -> "No diste permiso para avisarte. Sin él no te llegarán los recordatorios de tus citas."
                else -> "Te avisamos 24 h y 2 h antes de cada cita. Necesitamos tu permiso para enviar notificaciones."
            },
            color = Brand.Tinta2,
            fontSize = 13.sp,
        )
        TextButton(onClick = onEnable) {
            Text(if (p == PermissionStatus.DENEGADO_PARA_SIEMPRE) "Abrir ajustes" else "Permitir notificaciones")
        }
    }
}
