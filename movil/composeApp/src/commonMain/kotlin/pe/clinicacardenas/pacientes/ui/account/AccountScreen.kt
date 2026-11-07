package pe.clinicacardenas.pacientes.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import pe.clinicacardenas.pacientes.data.remote.PayRequest
import pe.clinicacardenas.pacientes.platform.PlatformActions
import pe.clinicacardenas.pacientes.ui.components.Banner
import pe.clinicacardenas.pacientes.ui.components.Field
import pe.clinicacardenas.pacientes.ui.components.Pill
import pe.clinicacardenas.pacientes.ui.components.PrimaryButton
import kotlin.math.roundToLong
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
fun AccountScreen(vm: AccountViewModel = koinViewModel(), platform: PlatformActions = koinInject()) {
    val s by vm.state.collectAsState()
    var payOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (payOpen) {
        PayDialog(
            balance = s.data?.balance ?: 0.0,
            state = s,
            onPay = vm::pay,
            onOpenInvoice = { id -> scope.launch { vm.invoiceUrl(id)?.let(platform::openUrl) } },
            onClose = { payOpen = false; vm.clearPay() },
        )
    }
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
                if (data.balance > 0.005) item { PrimaryButton("Pagar en línea (Yape, Plin o tarjeta)", { payOpen = true }, color = Brand.Naranja) }
                item { Text("También puedes pagar en recepción. Cada pago emite su comprobante electrónico SUNAT, que verás en Documentos.", color = Brand.Gris, fontSize = 13.sp) }
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

private val methods = listOf("YAPE" to "Yape", "PLIN" to "Plin", "TARJETA" to "Tarjeta")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PayDialog(balance: Double, state: AccountState, onPay: (PayRequest) -> Unit, onOpenInvoice: (Long) -> Unit, onClose: () -> Unit) {
    var amount by remember { mutableStateOf(((balance * 100).roundToLong() / 100.0).toString()) }
    var method by remember { mutableStateOf("YAPE") }
    var code by remember { mutableStateOf("") }
    var card by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    val paid = state.paid
    AlertDialog(
        onDismissRequest = { if (!state.paying) onClose() },
        title = { Text(if (paid != null) "¡Pago aprobado!" else "Pagar en línea") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (paid != null) {
                    Text("${Fmt.money(paid.amount)} · operación ${paid.operationId}", color = Brand.Tinta)
                    paid.invoice?.let { Text("Boleta electrónica $it enviada a SUNAT.", color = Brand.Verde, modifier = Modifier.padding(top = 6.dp)) }
                    Text(paid.message, color = Brand.Tinta2, modifier = Modifier.padding(top = 6.dp))
                } else {
                Text("Saldo pendiente: ${Fmt.money(balance)}", color = Brand.Tinta2)
                Spacer(Modifier.height(8.dp))
                Field(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }.take(9) }, "Monto (S/)", keyboard = KeyboardType.Decimal)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    methods.forEach { (k, l) -> Pill(l, method == k, { method = k }) }
                }
                if (method == "TARJETA") {
                    Field(card, { card = it.filter(Char::isDigit).take(16) }, "Número de tarjeta", keyboard = KeyboardType.Number)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(expiry, { v -> expiry = v.filter(Char::isDigit).take(4).let { d -> if (d.length > 2) d.take(2) + "/" + d.drop(2) else d } }, "MM/AA", Modifier.weight(1f), keyboard = KeyboardType.Number)
                        Field(cvv, { cvv = it.filter(Char::isDigit).take(4) }, "CVV", Modifier.weight(1f), password = true, keyboard = KeyboardType.Number)
                    }
                } else {
                    Text(
                        "Abre ${if (method == "YAPE") "Yape" else "Plin"}, genera el código de aprobación de 6 dígitos y escríbelo aquí.",
                        color = Brand.Tinta2,
                        fontSize = 13.sp,
                    )
                    Field(code, { code = it.filter(Char::isDigit).take(6) }, "Código de aprobación", keyboard = KeyboardType.Number)
                }
                Text("Pago de demostración: no se cobra dinero real.", color = Brand.Gris, fontSize = 12.sp)
                state.payError?.let { Banner(it, Brand.RojoSuave, Brand.Rojo, Modifier.padding(top = 8.dp)) }
                }
            }
        },
        confirmButton = {
            if (paid != null) {
                if (paid.invoiceId != null) TextButton(onClick = { onOpenInvoice(paid.invoiceId) }) { Text("Ver boleta") }
                TextButton(onClick = onClose) { Text("Listo") }
            } else {
                TextButton(
                    enabled = !state.paying,
                    onClick = {
                        onPay(
                            PayRequest(
                                amount = amount.toDoubleOrNull() ?: 0.0,
                                method = method,
                                approvalCode = code.takeIf { method != "TARJETA" },
                                cardNumber = card.takeIf { method == "TARJETA" },
                                cardExpiry = expiry.takeIf { method == "TARJETA" },
                                cardCvv = cvv.takeIf { method == "TARJETA" },
                            ),
                        )
                    },
                ) { Text(if (state.paying) "Procesando…" else "Pagar") }
            }
        },
        dismissButton = { if (paid == null) TextButton(onClick = onClose, enabled = !state.paying) { Text("Cancelar") } },
    )
}
