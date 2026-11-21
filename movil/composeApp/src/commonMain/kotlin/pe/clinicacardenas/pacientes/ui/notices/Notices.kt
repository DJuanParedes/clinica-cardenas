package pe.clinicacardenas.pacientes.ui.notices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.remote.NoticeDto
import pe.clinicacardenas.pacientes.data.repository.ClinicRepository
import pe.clinicacardenas.pacientes.platform.AppLifecycle
import pe.clinicacardenas.pacientes.ui.components.ErrorState
import pe.clinicacardenas.pacientes.ui.components.Fmt
import pe.clinicacardenas.pacientes.ui.components.Loading
import pe.clinicacardenas.pacientes.ui.components.Panel
import pe.clinicacardenas.pacientes.ui.theme.Brand

data class NoticesState(val loading: Boolean = true, val items: List<NoticeDto> = emptyList(), val unread: Int = 0, val error: String? = null)

/**
 * Bandeja de avisos de la clínica (notificaciones "push" simuladas): la app consulta cada minuto mientras está
 * abierta y muestra en el teléfono los avisos nuevos (cita reservada, espacio liberado, control, cuota…).
 */
class NoticesViewModel(private val repo: ClinicRepository) : ViewModel() {
    private val _state = MutableStateFlow(NoticesState())
    val state: StateFlow<NoticesState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                if (AppLifecycle.foreground.value) runCatching { repo.checkNewNotices() }.onSuccess { n -> _state.update { it.copy(unread = n) } }
                delay(60_000)
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            try {
                val r = repo.notices()
                _state.update { it.copy(loading = false, items = r.items, unread = r.unread.toInt(), error = null) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun markRead() {
        if (_state.value.unread == 0) return
        viewModelScope.launch {
            repo.markNoticesRead()
            _state.update { it.copy(unread = 0) }
        }
    }
}

private val icons = mapOf(
    "CITA_RESERVADA" to "🗓️", "CITA_REPROGRAMADA" to "🔁", "CITA_CANCELADA" to "✕", "CITA_REPROGRAMAR" to "🔁",
    "LISTA_ESPERA" to "⏰", "CONTROL" to "🦷", "CUOTA" to "💳", "PAGO" to "✅", "DOCUMENTO" to "📄",
)

@Composable
fun NoticesScreen(vm: NoticesViewModel) {
    val s by vm.state.collectAsState()
    LaunchedEffect(Unit) {
        vm.load()
        delay(1500)
        vm.markRead()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Avisos", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::load) { Text("Actualizar") }
            }
        }
        when {
            s.loading -> item { Loading() }
            s.error != null && s.items.isEmpty() -> item { ErrorState(s.error!!, vm::load) }
            s.items.isEmpty() -> item {
                Panel {
                    Text("No tienes avisos", fontWeight = FontWeight.Bold, color = Brand.Tinta)
                    Text("Aquí verás las confirmaciones de tus reservas, espacios liberados y recordatorios de control.", color = Brand.Tinta2)
                }
            }
            else -> items(s.items, key = { it.id }) { n ->
                Panel(padding = PaddingValues(14.dp)) {
                    Row {
                        Box(Modifier.size(40.dp).background(Brand.CelesteSuave, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Text(icons[n.kind] ?: "🔔", fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(n.title, fontWeight = FontWeight.SemiBold, color = Brand.Tinta, modifier = Modifier.weight(1f))
                                if (n.readAt == null) Box(Modifier.size(9.dp).background(Brand.Naranja, RoundedCornerShape(50)))
                            }
                            Text(n.body, color = Brand.Tinta2, fontSize = 14.sp)
                            Text("${Fmt.shortDate(n.createdAt)} · ${Fmt.hour(n.createdAt)}", color = Brand.Gris, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
