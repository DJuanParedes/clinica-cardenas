package pe.clinicacardenas.pacientes.ui.promos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import pe.clinicacardenas.pacientes.data.remote.PromoDto
import pe.clinicacardenas.pacientes.data.repository.ClinicRepository
import pe.clinicacardenas.pacientes.ui.booking.BookingScreen
import pe.clinicacardenas.pacientes.ui.booking.BookingTarget
import pe.clinicacardenas.pacientes.ui.components.ErrorState
import pe.clinicacardenas.pacientes.ui.components.Loading
import pe.clinicacardenas.pacientes.ui.components.PrimaryButton
import pe.clinicacardenas.pacientes.ui.theme.Brand

data class PromosState(val loading: Boolean = true, val items: List<PromoDto> = emptyList(), val error: String? = null)

/** Módulo Promociones: campañas vigentes de la clínica con precio antes/ahora, vigencia y condiciones (Ley 29571). */
class PromosViewModel(private val repo: ClinicRepository) : ViewModel() {
    private val _state = MutableStateFlow(PromosState())
    val state: StateFlow<PromosState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.items.isEmpty(), error = null) }
        viewModelScope.launch {
            try {
                _state.update { it.copy(items = repo.promotions(), loading = false) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }
}

@Composable
fun PromosScreen(vm: PromosViewModel = koinViewModel()) {
    val s by vm.state.collectAsState()
    var booking by remember { mutableStateOf(false) }
    if (booking) {
        BookingScreen(BookingTarget()) { booking = false }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Promociones", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta)
                    Text("Campañas vigentes de la clínica", color = Brand.Tinta2, fontSize = 13.sp)
                }
                TextButton(onClick = vm::load) { Text("Actualizar") }
            }
        }
        when {
            s.loading -> item { Loading() }
            s.error != null && s.items.isEmpty() -> item { ErrorState(s.error!!, vm::load) }
            s.items.isEmpty() -> item {
                Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏷️", fontSize = 36.sp)
                    Text("Por ahora no hay promociones vigentes.", color = Brand.Tinta2, modifier = Modifier.padding(top = 8.dp))
                    Text("Te avisaremos cuando haya una nueva.", color = Brand.Gris, fontSize = 13.sp)
                }
            }
            else -> {
                items(s.items, key = { it.id }) { p -> PromoCard(p) { booking = true } }
                item {
                    Text("Precios en soles, incluyen IGV. Válidos hasta la fecha indicada o hasta agotar cupos, según las condiciones de cada campaña.",
                        color = Brand.Gris, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun PromoCard(p: PromoDto, onBook: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, fontWeight = FontWeight.Bold, color = Brand.Tinta, fontSize = 16.sp, modifier = Modifier.weight(1f))
            badge(p)?.let {
                Text(it, color = Color(0xFF7A4600), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.background(Brand.NaranjaSuave, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        p.procedure?.let { Text(it, color = Brand.Tinta2, fontSize = 14.sp) }
        if (p.finalPrice != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("S/ ${fmt(p.finalPrice)}", color = Brand.Azul, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                if (p.listPrice != null && p.listPrice > p.finalPrice) {
                    Text("S/ ${fmt(p.listPrice)}", color = Brand.Gris, fontSize = 14.sp, textDecoration = TextDecoration.LineThrough, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
        p.validTo?.takeIf { it.length >= 10 }?.let { Text("Válida hasta el ${it.substring(8, 10)}/${it.substring(5, 7)}/${it.substring(0, 4)}", color = Brand.Tinta2, fontSize = 13.sp) }
        p.conditions?.takeIf { it.isNotBlank() }?.let { Text("Condiciones: $it", color = Brand.Gris, fontSize = 12.sp) }
        PrimaryButton("Reservar cita", onBook, color = Brand.Naranja)
    }
}

private fun badge(p: PromoDto): String? = when {
    p.discountType == "PORCENTAJE" && p.value != null -> "-${fmt(p.value)} %"
    p.listPrice != null && p.finalPrice != null && p.listPrice > p.finalPrice -> "Ahorra S/ ${fmt(p.listPrice - p.finalPrice)}"
    else -> null
}

private fun fmt(v: Double): String {
    val c = kotlin.math.round(v * 100).toLong()
    val e = c / 100
    val d = c % 100
    return if (d == 0L) e.toString() else "$e." + d.toString().padStart(2, '0')
}
