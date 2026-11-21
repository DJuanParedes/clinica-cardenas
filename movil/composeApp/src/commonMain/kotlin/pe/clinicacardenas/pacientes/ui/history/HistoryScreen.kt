package pe.clinicacardenas.pacientes.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import org.koin.compose.viewmodel.koinViewModel
import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.remote.PortalPlanDto
import pe.clinicacardenas.pacientes.data.remote.TreatmentsDto
import pe.clinicacardenas.pacientes.data.repository.ClinicRepository
import pe.clinicacardenas.pacientes.ui.account.AccountScreen
import pe.clinicacardenas.pacientes.ui.components.Chip
import pe.clinicacardenas.pacientes.ui.components.ErrorState
import pe.clinicacardenas.pacientes.ui.components.Fmt
import pe.clinicacardenas.pacientes.ui.components.Loading
import pe.clinicacardenas.pacientes.ui.components.Panel
import pe.clinicacardenas.pacientes.ui.components.SectionTitle
import pe.clinicacardenas.pacientes.ui.theme.Brand

data class HistoryState(val loading: Boolean = true, val data: TreatmentsDto? = null, val error: String? = null)

/** Mi historia: planes de tratamiento, qué se me realizó y cuándo, cuánto pagué por cada cosa y mi ficha clínica. */
class HistoryViewModel(private val repo: ClinicRepository) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.data == null, error = null) }
        viewModelScope.launch {
            try {
                _state.update { it.copy(data = repo.treatments(), loading = false) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }
}

private enum class Part(val label: String) { TRATAMIENTOS("Tratamientos"), PAGOS("Pagos"), FICHA("Mi ficha") }

@Composable
fun HistoryScreen(vm: HistoryViewModel = koinViewModel()) {
    var part by rememberSaveable { mutableStateOf(Part.TRATAMIENTOS) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(14.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Part.entries.forEach { p ->
                Box(
                    Modifier.weight(1f).background(if (p == part) Brand.CelesteSuave else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { part = p }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(p.label, color = if (p == part) Brand.Azul else Brand.Tinta2, fontWeight = if (p == part) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp) }
            }
        }
        when (part) {
            Part.PAGOS -> AccountScreen()
            else -> Treatments(vm, part == Part.FICHA)
        }
    }
}

@Composable
private fun Treatments(vm: HistoryViewModel, ficha: Boolean) {
    val s by vm.state.collectAsState()
    val d = s.data
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (ficha) "Mi ficha clínica" else "Mis tratamientos", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::load) { Text("Actualizar") }
            }
        }
        when {
            s.loading -> item { Loading() }
            d == null -> item { ErrorState(s.error ?: "No se pudo cargar tu historia.", vm::load) }
            ficha -> {
                item {
                    Panel {
                        val c = d.clinical
                        if (c == null) Text("Tu ficha se completa en tu primera consulta.", color = Brand.Tinta2)
                        else {
                            Line("Alergias", c.allergies ?: "No refiere", highlight = !c.allergies.isNullOrBlank())
                            Line("Medicación actual", c.medication ?: "—")
                            Line("Antecedentes", c.background ?: "—")
                            Line("Grupo sanguíneo", c.bloodType ?: "—")
                            c.updatedAt?.let { Text("Actualizado el ${Fmt.shortDate(it)}. Si algo cambió, avísale a tu odontólogo.", color = Brand.Gris, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                        }
                    }
                }
                if (d.visits.isNotEmpty()) {
                    item { SectionTitle("Mis atenciones") }
                    items(d.visits) { v ->
                        Panel(padding = PaddingValues(12.dp)) {
                            Text("${v.date?.let { Fmt.shortDate(it) + " · " + Fmt.hour(it) } ?: ""}", fontWeight = FontWeight.SemiBold, color = Brand.Tinta)
                            Text("${v.reason ?: ""} · ${if (v.status == "ATENDIDA") v.dentist ?: "" else "No asistió"}", color = Brand.Tinta2, fontSize = 13.sp)
                        }
                    }
                }
                item { Text("Tienes derecho a acceder a tu historia clínica completa (Ley 29414): pide una copia en recepción.", color = Brand.Gris, fontSize = 12.sp) }
            }
            else -> {
                item {
                    Panel {
                        Row {
                            Column(Modifier.weight(1f)) { Text("Total", color = Brand.Gris, fontSize = 12.sp); Text(Fmt.money(d.total), fontWeight = FontWeight.Bold) }
                            Column(Modifier.weight(1f)) { Text("Pagado", color = Brand.Gris, fontSize = 12.sp); Text(Fmt.money(d.paid), fontWeight = FontWeight.Bold, color = Brand.Verde) }
                            Column(Modifier.weight(1f)) { Text("Saldo", color = Brand.Gris, fontSize = 12.sp); Text(Fmt.money(d.balance), fontWeight = FontWeight.Bold, color = if (d.balance > 0) Brand.Rojo else Brand.Tinta) }
                        }
                    }
                }
                if (d.plans.isEmpty()) item { Text("Aún no tienes planes de tratamiento.", color = Brand.Tinta2) }
                items(d.plans, key = { it.id }) { PlanCard(it) }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, color = Brand.Gris, fontSize = 13.sp, modifier = Modifier.weight(0.42f))
        Text(value, color = if (highlight) Brand.Rojo else Brand.Tinta, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp, modifier = Modifier.weight(0.58f))
    }
}

@Composable
private fun PlanCard(p: PortalPlanDto) {
    var open by remember { mutableStateOf(false) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.title, fontWeight = FontWeight.Bold, color = Brand.Tinta, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Chip("${p.doneCount}/${p.itemCount} hechos", Brand.CelesteSuave, Brand.AzulOscuro)
        }
        Text("${p.dentist ?: ""}${p.createdAt?.let { " · desde " + Fmt.shortDate(it) } ?: ""}", color = Brand.Gris, fontSize = 12.sp)
        val pct = if (p.itemCount == 0) 0f else p.doneCount.toFloat() / p.itemCount
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(8.dp).background(Brand.Fondo, RoundedCornerShape(6.dp))) {
            Box(Modifier.fillMaxWidth(pct).height(8.dp).background(Brand.Azul, RoundedCornerShape(6.dp)))
        }
        p.items.forEach { it ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Text(if (it.status == "REALIZADO") "✅" else "⏳", fontSize = 15.sp)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(it.procedure + (it.tooth?.let { t -> " · pieza $t" } ?: ""), color = Brand.Tinta, fontSize = 14.sp)
                    Text(
                        if (it.status == "REALIZADO") "Realizado el ${it.doneAt?.let { d -> Fmt.shortDate(d) } ?: ""}${it.doneBy?.let { b -> " · $b" } ?: ""}"
                        else if (it.status == "EN_PROCESO") "En proceso" else "Pendiente",
                        color = Brand.Gris, fontSize = 12.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.money(it.price), fontSize = 13.sp, color = Brand.Tinta)
                    Text(if (it.balance > 0) "debe ${Fmt.money(it.balance)}" else "pagado", fontSize = 12.sp, color = if (it.balance > 0) Brand.Rojo else Brand.Verde, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (p.sessions.isNotEmpty()) {
            TextButton(onClick = { open = !open }) { Text(if (open) "Ocultar sesiones" else "Ver sesiones de atención (${p.sessions.size})") }
            if (open) p.sessions.forEach { s ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text("${s.date?.let { Fmt.shortDate(it) } ?: ""} · ${s.dentist ?: ""}${s.procedure?.let { " · $it" } ?: ""}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Brand.Tinta)
                    s.notes?.let { Text(it, fontSize = 13.sp, color = Brand.Tinta2) }
                }
            }
        }
        Text("Total ${Fmt.money(p.total)} · pagado ${Fmt.money(p.paid)} · saldo ${Fmt.money(p.balance)}", fontSize = 13.sp, color = Brand.Tinta2, modifier = Modifier.padding(top = 6.dp))
    }
}
