package pe.clinicacardenas.pacientes.ui.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.remote.CashTodayDto
import pe.clinicacardenas.pacientes.data.remote.DashboardDto
import pe.clinicacardenas.pacientes.data.remote.StaffUserDto
import pe.clinicacardenas.pacientes.data.repository.SessionRepository
import pe.clinicacardenas.pacientes.data.repository.StaffRepository
import pe.clinicacardenas.pacientes.data.repository.StaffSession
import pe.clinicacardenas.pacientes.platform.PlatformActions
import pe.clinicacardenas.pacientes.ui.components.Banner
import pe.clinicacardenas.pacientes.ui.components.ErrorState
import pe.clinicacardenas.pacientes.ui.components.Fmt
import pe.clinicacardenas.pacientes.ui.components.Loading
import pe.clinicacardenas.pacientes.ui.components.Panel
import pe.clinicacardenas.pacientes.ui.components.PrimaryButton
import pe.clinicacardenas.pacientes.ui.components.SectionTitle
import pe.clinicacardenas.pacientes.ui.components.Stat
import pe.clinicacardenas.pacientes.ui.theme.Brand

// ---------------------------------------------------------------- caja

data class CashState(val loading: Boolean = true, val data: CashTodayDto? = null, val error: String? = null, val picking: Boolean = false)

/** Caja del día: ingresos por método y movimientos. Los cobros se registran desde la ficha del paciente. */
class CashViewModel(private val repo: StaffRepository) : ViewModel() {
    private val _state = MutableStateFlow(CashState())
    val state: StateFlow<CashState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(data = repo.guarded { cashToday() }, loading = false, error = null) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message, loading = false) }
            }
        }
    }

    fun picking(on: Boolean) = _state.update { it.copy(picking = on) }
}

@Composable
fun CashScreen(user: StaffUserDto, open: (StaffNav) -> Unit, vm: CashViewModel = koinViewModel(), search: PatientSearchViewModel = koinViewModel(key = "caja-buscar")) {
    val s by vm.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Caja del día", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::load) { Text("Actualizar") }
            }
        }
        item {
            if (!s.picking) PrimaryButton("＋ Registrar cobro", { vm.picking(true) }, color = Brand.Naranja)
            else Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("¿A quién se cobra?", fontWeight = FontWeight.Bold, color = Brand.Tinta, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.picking(false) }) { Text("Cancelar") }
                }
                PatientSearch({ p -> vm.picking(false); open(StaffNav.Patient(p.id, "cuenta")) }, search)
            }
        }
        val d = s.data
        when {
            s.loading -> item { Loading() }
            d == null -> item { ErrorState(s.error ?: "No se pudo cargar la caja.", vm::load) }
            else -> {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat(Fmt.money(d.income), "ingresos", Modifier.weight(1f), Brand.Verde)
                        Stat(Fmt.money(d.outgo), "egresos", Modifier.weight(1f), Brand.Rojo)
                    }
                }
                item { Stat(Fmt.money(d.net), "neto en caja", Modifier.fillMaxWidth()) }
                if (d.byMethod.isNotEmpty()) {
                    item { SectionTitle("Por método") }
                    item {
                        Panel {
                            d.byMethod.forEach { m ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(m.label.ifBlank { m.method }, color = Brand.Tinta2, modifier = Modifier.weight(1f))
                                    Text(Fmt.money(m.income - m.outgo), fontWeight = FontWeight.SemiBold, color = Brand.Tinta)
                                }
                            }
                        }
                    }
                }
                item { SectionTitle("Movimientos de hoy") }
                if (d.detail.isEmpty()) item { Text("Aún no hay movimientos hoy.", color = Brand.Tinta2) }
                items(d.detail, key = { "${it.type}${it.id}" }) { m ->
                    Panel(padding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.party ?: m.concept, fontWeight = FontWeight.SemiBold, color = Brand.Tinta)
                                Text("${Fmt.hour(m.date)} · ${m.method.lowercase().replaceFirstChar { it.uppercase() }} · ${m.concept}", color = Brand.Gris, fontSize = 12.sp)
                            }
                            val income = m.type == "INGRESO"
                            Text((if (income) "+ " else "- ") + Fmt.money(m.amount), fontWeight = FontWeight.Bold, color = if (income) Brand.Verde else Brand.Rojo)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- resumen (dirección)

data class DashState(val loading: Boolean = true, val data: DashboardDto? = null, val error: String? = null)

class DashboardViewModel(private val repo: StaffRepository) : ViewModel() {
    private val _state = MutableStateFlow(DashState())
    val state: StateFlow<DashState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(data = repo.guarded { dashboard() }, loading = false, error = null) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message, loading = false) }
            }
        }
    }
}

@Composable
fun DashboardScreen(user: StaffUserDto, open: (StaffNav) -> Unit, vm: DashboardViewModel = koinViewModel()) {
    val s by vm.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hola, ${user.fullName.substringBefore(' ')}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta)
                    Text(Fmt.longDate(today()).replaceFirstChar { it.uppercase() }, color = Brand.Tinta2, fontSize = 14.sp)
                }
                TextButton(onClick = vm::load) { Text("Actualizar") }
            }
        }
        val d = s.data
        when {
            s.loading -> item { Loading() }
            d == null -> item { ErrorState(s.error ?: "No se pudo cargar el resumen.", vm::load) }
            else -> {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat(d.appointmentsToday.toString(), "citas hoy", Modifier.weight(1f))
                        Stat(d.statusCounts.ATENDIDA.toString(), "atendidas", Modifier.weight(1f), Brand.Verde)
                        Stat((d.statusCounts.CANCELADA + d.statusCounts.NO_ASISTIO).toString(), "canceladas", Modifier.weight(1f), Brand.Rojo)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat(Fmt.money(d.collectedToday), "cobrado hoy", Modifier.weight(1f), Brand.Verde)
                        Stat(Fmt.money(d.collectedMonth), "en el mes", Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat(Fmt.money(d.receivable), "por cobrar", Modifier.weight(1f), Brand.Rojo)
                        Stat(d.patientsTotal.toString(), "pacientes", Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat(if (d.surveysCount > 0) "${(d.satisfaction * 10).toLong() / 10.0} ★" else "—", "satisfacción (${d.surveysCount})", Modifier.weight(1f), Brand.Naranja)
                        Stat(d.pendingSunat.toString(), "sin enviar a SUNAT", Modifier.weight(1f), if (d.pendingSunat > 0) Brand.Rojo else Brand.Tinta)
                    }
                }
                val alerts = buildList {
                    if (d.rescheduleRequests > 0) add("${d.rescheduleRequests} pacientes piden reprogramar")
                    if (d.waitlist > 0) add("${d.waitlist} en lista de espera")
                    if (d.lowStock > 0) add("${d.lowStock} materiales por agotarse")
                }
                if (alerts.isNotEmpty()) item { Banner(alerts.joinToString("\n"), Brand.NaranjaSuave, Brand.AzulOscuro) }
            }
        }
    }
}

// ---------------------------------------------------------------- cuenta del personal

@Composable
fun StaffAccountScreen(user: StaffUserDto) {
    val session = koinInject<StaffSession>()
    val repo = koinInject<StaffRepository>()
    val platform = koinInject<PlatformActions>()
    val patientSession = koinInject<SessionRepository>()
    var bio by remember { mutableStateOf(session.biometric) }
    var info by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Mi cuenta", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta) }
        item {
            Panel {
                Text(user.fullName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Brand.Tinta)
                Text("${user.roleLabel} · usuario ${user.username}", color = Brand.Tinta2)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (user.twoFactor) "🔐 Verificación en 2 pasos activada" else "Verificación en 2 pasos desactivada: actívala en la web, en Mi seguridad.",
                    color = if (user.twoFactor) Brand.Verde else Brand.Gris,
                    fontSize = 13.sp,
                )
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Desbloqueo con huella", fontWeight = FontWeight.SemiBold, color = Brand.Tinta)
                        Text(
                            if (platform.canUseBiometrics) "Al volver a la app se pide tu huella, rostro o PIN del teléfono."
                            else "Configura una huella o bloqueo de pantalla en el teléfono para usarlo.",
                            color = Brand.Tinta2,
                            fontSize = 13.sp,
                        )
                    }
                    Switch(
                        checked = bio,
                        enabled = platform.canUseBiometrics,
                        colors = SwitchDefaults.colors(checkedTrackColor = Brand.Azul),
                        onCheckedChange = { on ->
                            if (!on) {
                                session.biometric = false
                                bio = false
                            } else {
                                platform.authenticate("Confirma para activar el desbloqueo con huella") { ok ->
                                    session.biometric = ok
                                    bio = ok
                                    info = if (ok) "Desbloqueo con huella activado." else "No se pudo verificar."
                                }
                            }
                        },
                    )
                }
                info?.let { Text(it, color = Brand.Gris, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
        item {
            Text(
                "Por seguridad la sesión se cierra sola tras 15 minutos sin uso. Servidor: ${patientSession.serverUrl}",
                color = Brand.Gris,
                fontSize = 12.sp,
            )
        }
        item { PrimaryButton("Cerrar sesión", repo::logout, color = Brand.Rojo) }
    }
}
