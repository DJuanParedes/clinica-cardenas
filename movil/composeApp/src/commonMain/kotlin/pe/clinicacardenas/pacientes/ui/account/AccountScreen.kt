package pe.clinicacardenas.pacientes.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.koin.compose.viewmodel.koinViewModel
import pe.clinicacardenas.pacientes.data.remote.MovementDto
import pe.clinicacardenas.pacientes.ui.components.ErrorState
import pe.clinicacardenas.pacientes.ui.components.Fmt
import pe.clinicacardenas.pacientes.ui.components.Loading
import pe.clinicacardenas.pacientes.ui.components.Panel
import pe.clinicacardenas.pacientes.ui.components.SectionTitle
import pe.clinicacardenas.pacientes.ui.theme.Brand

@Composable
fun AccountScreen(vm: AccountViewModel = koinViewModel()) {
    val s by vm.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mis pagos", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::load) { Text("Actualizar") }
            }
        }
        val data = s.data
        when {
            s.loading -> item { Loading() }
            data == null -> item { ErrorState(s.error ?: "No se pudo cargar tu estado de cuenta.", vm::load) }
            else -> {
                item {
                    Panel {
                        Text(if (data.balance > 0.005) "Saldo pendiente" else "Estás al día", color = Brand.Tinta2)
                        Text(
                            Fmt.money(data.balance.coerceAtLeast(0.0)),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (data.balance > 0.005) Brand.Rojo else Brand.Verde,
                        )
                        if (data.inFavor > 0.005) Text("Saldo a favor: ${Fmt.money(data.inFavor)}", color = Brand.Verde)
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("Tratamientos", color = Brand.Gris, fontSize = 13.sp)
                                Text(Fmt.money(data.charges), fontWeight = FontWeight.SemiBold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Pagado", color = Brand.Gris, fontSize = 13.sp)
                                Text(Fmt.money(data.credits), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                item { Text("Los pagos se realizan en recepción. Cada pago emite su comprobante electrónico SUNAT.", color = Brand.Gris, fontSize = 13.sp) }
                item { SectionTitle("Movimientos") }
                if (data.movements.isEmpty()) item { Text("Aún no tienes movimientos.", color = Brand.Tinta2) }
                items(data.movements.asReversed()) { m -> MovementRow(m) }
            }
        }
    }
}

@Composable
private fun MovementRow(m: MovementDto) {
    Panel(padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.concept, fontWeight = FontWeight.SemiBold, color = Brand.Tinta)
                val meta = listOfNotNull(m.date?.let(Fmt::shortDate), m.detail).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, color = Brand.Gris, fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            if (m.credit > 0) Text("- ${Fmt.money(m.credit)}", color = Brand.Verde, fontWeight = FontWeight.SemiBold)
            else Text(Fmt.money(m.charge), color = Brand.Tinta, fontWeight = FontWeight.SemiBold)
        }
    }
}
