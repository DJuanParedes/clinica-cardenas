package pe.clinicacardenas.pacientes.ui.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import pe.clinicacardenas.pacientes.ui.components.BackHeader
import pe.clinicacardenas.pacientes.ui.components.Banner
import pe.clinicacardenas.pacientes.ui.components.Field
import pe.clinicacardenas.pacientes.ui.components.Fmt
import pe.clinicacardenas.pacientes.ui.components.Loading
import pe.clinicacardenas.pacientes.ui.components.Panel
import pe.clinicacardenas.pacientes.ui.components.Pill
import pe.clinicacardenas.pacientes.ui.components.PrimaryButton
import pe.clinicacardenas.pacientes.ui.components.SectionTitle
import pe.clinicacardenas.pacientes.ui.theme.Brand

private val diasCortos = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookingScreen(target: BookingTarget, onClose: (changed: Boolean) -> Unit) {
    val vm: BookingViewModel = koinViewModel(key = "reserva-${target.rescheduleId ?: "nueva"}")
    LaunchedEffect(target) { vm.start(target) }
    val s by vm.state.collectAsState()
    val reschedule = target.rescheduleId != null

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            BackHeader(
                if (reschedule) "Cambiar horario" else "Reservar cita",
                onBack = { onClose(s.done != null) },
                subtitle = if (reschedule) target.reason else "Elige el motivo, el odontólogo y el horario",
            )
        }
        val done = s.done
        if (done != null) {
            item {
                Panel {
                    Text("✅", fontSize = 40.sp)
                    Text(done, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta)
                    s.slot?.let { Text("${Fmt.dayLabel(it)} · ${Fmt.hour(it)}", color = Brand.Tinta2) }
                    Text("Te enviaremos un recordatorio antes de la cita.", color = Brand.Gris, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Ver mis citas", { onClose(true) })
                }
            }
            return@LazyColumn
        }
        if (s.loading) {
            item { Loading() }
            return@LazyColumn
        }
        val o = s.options ?: run {
            item { Banner(s.error ?: "No se pudieron cargar los horarios.", Brand.RojoSuave, Brand.Rojo) }
            return@LazyColumn
        }
        if (!reschedule) {
            item { SectionTitle("¿Qué necesitas?") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    o.reasons.forEach { r -> Pill("${r.label} · ${r.durationMin} min", s.reasonCode == r.code, { vm.reason(r.code) }) }
                }
            }
        }
        item { SectionTitle("Odontólogo") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                o.dentists.forEach { d -> Pill(d.fullName, s.dentistId == d.id, { vm.dentist(d.id) }) }
            }
        }
        if (s.reasonCode == null || s.dentistId == null) {
            item { Text("Elige el motivo y el odontólogo para ver los horarios libres.", color = Brand.Gris, fontSize = 14.sp) }
            return@LazyColumn
        }
        item { SectionTitle("Día") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.days) { d ->
                    Pill("${diasCortos[d.dayOfWeek.ordinal]} ${d.dayOfMonth}", s.date == d, { vm.date(d) })
                }
            }
        }
        item {
            Row { SectionTitle("Horarios libres") }
        }
        item {
            when {
                s.loadingSlots -> Loading()
                !s.works -> Text("El odontólogo no atiende ese día. Prueba con otra fecha.", color = Brand.Tinta2)
                s.slots.isEmpty() -> Text("No quedan horarios libres ese día. Prueba con otra fecha u odontólogo.", color = Brand.Tinta2)
                else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.slots.forEach { t -> Pill(Fmt.hour(t), s.slot == t, { vm.slot(t) }) }
                }
            }
        }
        if (!reschedule) {
            item { Field(s.notes, vm::notes, "Comentario para la clínica (opcional)", placeholder = "Ej. me duele una muela del lado derecho") }
        }
        s.error?.let { e -> item { Banner(e, Brand.RojoSuave, Brand.Rojo) } }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                val label = s.slot?.let { (if (reschedule) "Cambiar a " else "Reservar ") + Fmt.dayLabel(it).lowercase() + " " + Fmt.hour(it) }
                    ?: "Elige un horario"
                PrimaryButton(label, vm::submit, loading = s.saving, enabled = s.slot != null, color = Brand.Naranja)
                Text(
                    "Puedes cambiar o cancelar tu cita desde la app hasta 12 horas antes.",
                    color = Brand.Gris,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
