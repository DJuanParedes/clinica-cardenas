package pe.clinicacardenas.pacientes.ui.home

import androidx.compose.foundation.background
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
import pe.clinicacardenas.pacientes.data.remote.AppointmentDto
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
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Greeting(patient, onRefresh = vm::load) }
        if (s.offline) item { Banner("Sin conexión: mostramos las citas guardadas en tu teléfono.", Brand.NaranjaSuave, Color(0xFF7A4600)) }
        s.message?.let { m -> item { Banner(m, Brand.VerdeSuave, Brand.Verde) } }
        when {
            s.loading -> item { Loading() }
            s.data == null -> item { ErrorState(s.error ?: "No se pudieron cargar tus citas.", vm::load) }
            else -> {
                val data = s.data!!
                val next = data.next
                item {
                    if (next != null) NextCard(next, patient, s.confirming == next.id, vm::confirm, platform)
                    else EmptyNext(patient, platform)
                }
                if (s.reminders > 0) item {
                    Text(
                        "🔔 ${s.reminders} recordatorio${if (s.reminders == 1) "" else "s"} programado${if (s.reminders == 1) "" else "s"} en este teléfono.",
                        color = Brand.Gris,
                        fontSize = 13.sp,
                    )
                }
                val others = data.upcoming.filter { it.id != next?.id }
                if (others.isNotEmpty()) {
                    item { SectionTitle("Próximas citas") }
                    items(others, key = { it.id }) { a -> AppointmentRow(a, s.confirming == a.id, vm::confirm) }
                }
                if (data.past.isNotEmpty()) {
                    item { SectionTitle("Historial") }
                    items(data.past.take(15), key = { "p${it.id}" }) { a -> AppointmentRow(a, false, null) }
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
private fun NextCard(a: AppointmentDto, patient: PatientDto, confirming: Boolean, onConfirm: (Long) -> Unit, platform: PlatformActions) {
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
        Text("${a.dentist.fullName} · ${a.dentist.specialty}", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        val (bg, fg) = Fmt.statusColors(a.status)
        Chip(Fmt.statusLabel[a.status] ?: a.status, bg, fg)
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
                    details = "Con ${a.dentist.fullName}. Tel. ${patient.clinic.phone}",
                )
            }
            WhiteButton("💬 Escribir", Modifier.weight(1f)) {
                val msg = "Hola, soy ${patient.fullName}. Consulto por mi cita del ${Fmt.dayLabel(a.startAt).lowercase()} a las ${Fmt.hour(a.startAt)}."
                platform.openUrl(whatsapp(patient.clinic.phone, msg))
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
        Text("Escríbenos o llámanos y te reservamos un horario.", color = Brand.Tinta2)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("💬 WhatsApp", { platform.openUrl(whatsapp(patient.clinic.phone, "Hola, soy ${patient.fullName} y quiero reservar una cita.")) }, Modifier.weight(1f))
            SecondaryButton("📞 Llamar", { platform.dial(patient.clinic.phone) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppointmentRow(a: AppointmentDto, confirming: Boolean, onConfirm: ((Long) -> Unit)?) {
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
                    Box(Modifier.size(8.dp).background(Fmt.color(a.dentist.color), RoundedCornerShape(50)))
                    Spacer(Modifier.width(6.dp))
                    Text("${Fmt.hour(a.startAt)} · ${a.dentist.fullName}", color = Brand.Tinta2, fontSize = 13.sp)
                }
            }
            val (bg, fg) = Fmt.statusColors(a.status)
            Chip(Fmt.statusLabel[a.status] ?: a.status, bg, fg)
        }
        if (onConfirm != null && a.canConfirm) {
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Confirmar asistencia", { onConfirm(a.id) }, loading = confirming)
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
