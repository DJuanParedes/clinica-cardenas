package pe.clinicacardenas.pacientes.ui.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.remote.NewAppointmentRequest
import pe.clinicacardenas.pacientes.data.remote.SDentistDto
import pe.clinicacardenas.pacientes.data.remote.SPatientDto
import pe.clinicacardenas.pacientes.data.repository.StaffRepository
import pe.clinicacardenas.pacientes.data.repository.StaffSession
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

data class NewApptState(
    val patient: SPatientDto? = null,
    val dentists: List<SDentistDto> = emptyList(),
    val dentistId: Long? = null,
    val duration: Int = 30,
    val days: List<LocalDate> = emptyList(),
    val date: LocalDate = today(),
    val slots: List<String> = emptyList(),
    val works: Boolean = true,
    val loadingSlots: Boolean = false,
    val slot: String? = null,
    val reason: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val done: String? = null,
)

/** Recepción: programa una cita desde el teléfono con los horarios libres reales (HU-07). */
class NewAppointmentViewModel(private val repo: StaffRepository, session: StaffSession) : ViewModel() {
    private val _state = MutableStateFlow(NewApptState(days = (0 until 21).map { today().plus(DatePeriod(days = it)) }.filter { it.dayOfWeek.ordinal != 6 }))
    val state: StateFlow<NewApptState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        val mine = session.user.value?.dentistId
        viewModelScope.launch {
            runCatching { repo.guarded { dentists() } }.onSuccess { list ->
                _state.update { it.copy(dentists = list, dentistId = mine ?: list.singleOrNull()?.id) }
                loadSlots()
            }
        }
    }

    fun patient(p: SPatientDto?) = _state.update { it.copy(patient = p) }
    fun dentist(id: Long) { _state.update { it.copy(dentistId = id, slot = null) }; loadSlots() }
    fun duration(d: Int) { _state.update { it.copy(duration = d, slot = null) }; loadSlots() }
    fun date(d: LocalDate) { _state.update { it.copy(date = d, slot = null) }; loadSlots() }
    fun slot(s: String) = _state.update { it.copy(slot = s) }
    fun reason(r: String) = _state.update { it.copy(reason = r.take(200)) }

    private fun loadSlots() {
        val s = _state.value
        val dentist = s.dentistId ?: return
        job?.cancel()
        _state.update { it.copy(loadingSlots = true, slots = emptyList()) }
        job = viewModelScope.launch {
            try {
                val r = repo.guarded { slots(dentist, s.date.toString(), s.duration) }
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
                _state.update { it.copy(slots = r.slots.filter { x -> x.available && x.start > now }.map { x -> x.start }, works = r.works, loadingSlots = false) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message, loadingSlots = false) }
            }
        }
    }

    fun save() {
        val s = _state.value
        val p = s.patient ?: return _state.update { it.copy(error = "Elige el paciente.") }
        val slot = s.slot ?: return _state.update { it.copy(error = "Elige el horario.") }
        if (s.reason.isBlank()) return _state.update { it.copy(error = "Indica el motivo.") }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val a = repo.guarded { createAppointment(NewAppointmentRequest(p.id, s.dentistId!!, slot, s.duration, s.reason.trim())) }
                _state.update { it.copy(saving = false, done = "Cita programada: ${a.patient.fullName}, ${Fmt.dayLabel(a.startAt).lowercase()} ${Fmt.hour(a.startAt)}.") }
            } catch (e: ApiException) {
                _state.update { it.copy(saving = false, error = e.fields.values.firstOrNull() ?: e.message) }
            }
        }
    }
}

private val diasCortos = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewAppointmentScreen(onClose: () -> Unit, vm: NewAppointmentViewModel = koinViewModel(key = "nueva-cita"), search: PatientSearchViewModel = koinViewModel(key = "cita-buscar")) {
    val s by vm.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BackHeader("Nueva cita", onClose) }
        val done = s.done
        if (done != null) {
            item { Banner(done, Brand.VerdeSuave, Brand.Verde) }
            item { PrimaryButton("Volver a la agenda", onClose) }
            return@LazyColumn
        }
        item { SectionTitle("Paciente") }
        item {
            val p = s.patient
            if (p != null) {
                Panel {
                    Text(p.fullName, fontWeight = FontWeight.SemiBold, color = Brand.Tinta)
                    Text("${p.docType} ${p.docNumber}", color = Brand.Gris, fontSize = 13.sp)
                    TextButton(onClick = { vm.patient(null) }) { Text("Cambiar") }
                }
            } else {
                PatientSearch(vm::patient, search)
            }
        }
        item { SectionTitle("Odontólogo y duración") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.dentists.forEach { d -> Pill(d.fullName, s.dentistId == d.id, { vm.dentist(d.id) }) }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 45, 60, 90).forEach { m -> Pill("$m min", s.duration == m, { vm.duration(m) }) }
            }
        }
        item { SectionTitle("Día y hora") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.days) { d -> Pill("${diasCortos[d.dayOfWeek.ordinal]} ${d.dayOfMonth}", s.date == d, { vm.date(d) }) }
            }
        }
        item {
            when {
                s.dentistId == null -> Text("Elige el odontólogo.", color = Brand.Gris)
                s.loadingSlots -> Loading()
                !s.works -> Text("No atiende ese día.", color = Brand.Tinta2)
                s.slots.isEmpty() -> Text("Sin horarios libres ese día.", color = Brand.Tinta2)
                else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.slots.forEach { t -> Pill(Fmt.hour(t), s.slot == t, { vm.slot(t) }) }
                }
            }
        }
        item { Field(s.reason, vm::reason, "Motivo", placeholder = "Evaluación, limpieza, control…") }
        s.error?.let { e -> item { Banner(e, Brand.RojoSuave, Brand.Rojo) } }
        item { PrimaryButton("Programar cita", vm::save, loading = s.saving, color = Brand.Naranja) }
    }
}
