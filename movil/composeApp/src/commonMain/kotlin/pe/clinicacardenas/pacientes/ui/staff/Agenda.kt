package pe.clinicacardenas.pacientes.ui.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.koin.compose.viewmodel.koinViewModel
import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.remote.SAppointmentDto
import pe.clinicacardenas.pacientes.data.remote.StaffUserDto
import pe.clinicacardenas.pacientes.data.repository.StaffRepository
import pe.clinicacardenas.pacientes.data.repository.StaffSession
import pe.clinicacardenas.pacientes.ui.components.Banner
import pe.clinicacardenas.pacientes.ui.components.Chip
import pe.clinicacardenas.pacientes.ui.components.ErrorState
import pe.clinicacardenas.pacientes.ui.components.Fmt
import pe.clinicacardenas.pacientes.ui.components.Loading
import pe.clinicacardenas.pacientes.ui.components.Panel
import pe.clinicacardenas.pacientes.ui.components.Pill
import pe.clinicacardenas.pacientes.ui.components.PrimaryButton
import pe.clinicacardenas.pacientes.ui.components.SecondaryButton
import pe.clinicacardenas.pacientes.ui.theme.Brand

fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

data class AgendaState(
    val date: LocalDate = today(),
    val onlyMine: Boolean = true,
    val loading: Boolean = true,
    val items: List<SAppointmentDto> = emptyList(),
    val error: String? = null,
    val busyId: Long? = null,
    val message: String? = null,
)

/** Agenda del día: el odontólogo ve la suya; recepción marca llegadas y estados. */
class AgendaViewModel(private val repo: StaffRepository, private val session: StaffSession) : ViewModel() {
    private val _state = MutableStateFlow(AgendaState())
    val state: StateFlow<AgendaState> = _state.asStateFlow()

    private val myDentist get() = session.user.value?.dentistId

    init { load() }

    fun shift(days: Int) {
        _state.update { it.copy(date = if (days >= 0) it.date.plus(DatePeriod(days = days)) else it.date.minus(DatePeriod(days = -days))) }
        load()
    }

    fun goToday() { _state.update { it.copy(date = today()) }; load() }

    fun toggleMine() { _state.update { it.copy(onlyMine = !it.onlyMine) }; load() }

    fun load() {
        val s = _state.value
        _state.update { it.copy(loading = it.items.isEmpty(), error = null) }
        viewModelScope.launch {
            try {
                val dentist = if (s.onlyMine) myDentist else null
                val day = repo.guarded { day(s.date.toString(), dentist) }
                _state.update { it.copy(items = day.appointments.sortedBy { a -> a.startAt }, loading = false) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message, loading = false) }
            }
        }
    }

    fun move(a: SAppointmentDto, status: String) {
        _state.update { it.copy(busyId = a.id, message = null) }
        viewModelScope.launch {
            try {
                repo.guarded { setStatus(a.id, status) }
                _state.update { it.copy(message = "${a.patient.fullName}: ${Fmt.statusLabel[status] ?: status}.") }
                load()
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.message) }
            } finally {
                _state.update { it.copy(busyId = null) }
            }
        }
    }
}

@Composable
fun AgendaScreen(user: StaffUserDto, open: (StaffNav) -> Unit, vm: AgendaViewModel = koinViewModel()) {
    val s by vm.state.collectAsState()
    val canMove = "CITAS_ESTADO" in user.permissions
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Agenda", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta)
                    Text(Fmt.longDate(s.date).replaceFirstChar { it.uppercase() }, color = Brand.Tinta2, fontSize = 14.sp)
                }
                TextButton(onClick = { vm.shift(-1) }) { Text("‹", fontSize = 22.sp) }
                TextButton(onClick = vm::goToday) { Text("Hoy") }
                TextButton(onClick = { vm.shift(1) }) { Text("›", fontSize = 22.sp) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (user.dentistId != null) {
                    Pill("Solo mis citas", s.onlyMine, vm::toggleMine)
                    Pill("Toda la clínica", !s.onlyMine, vm::toggleMine)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = vm::load) { Text("Actualizar") }
            }
        }
        if ("CITAS_GESTIONAR" in user.permissions) {
            item { PrimaryButton("＋ Nueva cita", { open(StaffNav.NewAppointment) }, color = Brand.Naranja) }
        }
        s.message?.let { m -> item { Banner(m, Brand.CelesteSuave, Brand.AzulOscuro) } }
        when {
            s.loading -> item { Loading() }
            s.error != null && s.items.isEmpty() -> item { ErrorState(s.error!!, vm::load) }
            s.items.isEmpty() -> item { Panel { Text("No hay citas este día.", color = Brand.Tinta2) } }
            else -> {
                val active = s.items.filter { it.status !in listOf("CANCELADA", "NO_ASISTIO") }
                item {
                    Text(
                        "${active.size} citas · ${active.count { it.status == "ATENDIDA" }} atendidas · ${active.count { it.status == "EN_ESPERA" }} en sala",
                        color = Brand.Gris,
                        fontSize = 13.sp,
                    )
                }
                items(s.items, key = { it.id }) { a -> AppointmentCard(a, canMove, s.busyId == a.id, vm::move) { open(StaffNav.Patient(a.patient.id)) } }
            }
        }
    }
}

@Composable
private fun AppointmentCard(a: SAppointmentDto, canMove: Boolean, busy: Boolean, onMove: (SAppointmentDto, String) -> Unit, onOpen: () -> Unit) {
    val closed = a.status in listOf("CANCELADA", "NO_ASISTIO")
    Panel(padding = PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.Top) {
            Column(Modifier.width(62.dp)) {
                Text(Fmt.hour(a.startAt).substringBefore(" "), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (closed) Brand.Gris else Brand.Tinta)
                Text(Fmt.hour(a.startAt).substringAfter(" "), fontSize = 11.sp, color = Brand.Gris)
                Text("${a.durationMin} min", fontSize = 11.sp, color = Brand.Gris)
            }
            Column(Modifier.weight(1f)) {
                Text(a.patient.fullName, fontWeight = FontWeight.SemiBold, color = if (closed) Brand.Gris else Brand.Tinta)
                Text(listOfNotNull(a.reason, a.patient.age?.let { "$it años" }).joinToString(" · "), color = Brand.Tinta2, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Box(Modifier.size(8.dp).background(Fmt.color(a.dentist.color), RoundedCornerShape(50)))
                    Spacer(Modifier.width(6.dp))
                    Text(a.dentist.fullName, color = Brand.Gris, fontSize = 12.sp)
                }
                if (a.rescheduleRequestedAt != null) Text("Pidió reprogramar por WhatsApp", color = Brand.Rojo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            val (bg, fg) = Fmt.statusColors(a.status)
            Chip(Fmt.statusLabel[a.status] ?: a.status, bg, fg)
        }
        if (canMove && !closed && a.status != "ATENDIDA") {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (a.status) {
                    "PROGRAMADA", "CONFIRMADA" -> {
                        PrimaryButton(if (busy) "…" else "Llegó", { onMove(a, "EN_ESPERA") }, Modifier.weight(1f), loading = busy)
                        SecondaryButton("No asistió", { onMove(a, "NO_ASISTIO") }, Modifier.weight(1f), enabled = !busy)
                    }
                    "EN_ESPERA" -> PrimaryButton("Pasar a atención", { onMove(a, "EN_ATENCION") }, loading = busy)
                    "EN_ATENCION" -> PrimaryButton("Terminar atención", { onMove(a, "ATENDIDA") }, loading = busy, color = Brand.Verde)
                }
            }
        }
    }
}
