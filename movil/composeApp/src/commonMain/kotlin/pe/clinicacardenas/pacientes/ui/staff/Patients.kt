package pe.clinicacardenas.pacientes.ui.staff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.remote.SPatientDto
import pe.clinicacardenas.pacientes.data.repository.StaffRepository
import pe.clinicacardenas.pacientes.ui.components.Banner
import pe.clinicacardenas.pacientes.ui.components.Field
import pe.clinicacardenas.pacientes.ui.components.Loading
import pe.clinicacardenas.pacientes.ui.components.Panel
import pe.clinicacardenas.pacientes.ui.theme.Brand

data class SearchState(val q: String = "", val loading: Boolean = false, val items: List<SPatientDto> = emptyList(), val error: String? = null, val searched: Boolean = false)

/** Búsqueda de pacientes por DNI, nombre o apellido (con espera de 350 ms mientras se escribe). */
class PatientSearchViewModel(private val repo: StaffRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var job: Job? = null

    fun query(q: String) {
        _state.update { it.copy(q = q) }
        job?.cancel()
        if (q.trim().length < 2) return _state.update { it.copy(items = emptyList(), searched = false, loading = false) }
        job = viewModelScope.launch {
            delay(350)
            _state.update { it.copy(loading = true, error = null) }
            try {
                val r = repo.guarded { searchPatients(q.trim()) }
                _state.update { it.copy(items = r.content, loading = false, searched = true) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message, loading = false) }
            }
        }
    }
}

@Composable
fun PatientSearch(onPick: (SPatientDto) -> Unit, vm: PatientSearchViewModel, hint: String = "DNI, nombre o apellido") {
    val s by vm.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(s.q, vm::query, "Buscar paciente", placeholder = hint)
        s.error?.let { Banner(it, Brand.RojoSuave, Brand.Rojo) }
        if (s.loading) Loading()
        if (s.searched && s.items.isEmpty() && !s.loading) Text("No se encontraron pacientes.", color = Brand.Tinta2)
        s.items.take(20).forEach { p -> PatientRow(p) { onPick(p) } }
    }
}

@Composable
fun PatientRow(p: SPatientDto, onClick: () -> Unit) {
    Panel(Modifier.clickable(onClick = onClick), padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.fullName, fontWeight = FontWeight.SemiBold, color = Brand.Tinta)
                Text(listOfNotNull("${p.docType} ${p.docNumber}", p.age?.let { "$it años" }, p.phone).joinToString(" · "), color = Brand.Gris, fontSize = 13.sp)
            }
            Text("›", fontSize = 22.sp, color = Brand.Gris)
        }
    }
}

@Composable
fun PatientsScreen(open: (StaffNav) -> Unit, vm: PatientSearchViewModel = koinViewModel(key = "pacientes")) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Pacientes", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta) }
        item { Column(Modifier.fillMaxWidth()) { PatientSearch({ open(StaffNav.Patient(it.id)) }, vm) } }
    }
}
