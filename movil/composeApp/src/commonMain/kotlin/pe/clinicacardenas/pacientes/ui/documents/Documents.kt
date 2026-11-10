package pe.clinicacardenas.pacientes.ui.documents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import pe.clinicacardenas.pacientes.data.remote.DocumentsDto
import pe.clinicacardenas.pacientes.data.repository.ClinicRepository
import pe.clinicacardenas.pacientes.platform.PlatformActions
import pe.clinicacardenas.pacientes.ui.components.Banner
import pe.clinicacardenas.pacientes.ui.components.Chip
import pe.clinicacardenas.pacientes.ui.components.ErrorState
import pe.clinicacardenas.pacientes.ui.components.Fmt
import pe.clinicacardenas.pacientes.ui.components.Loading
import pe.clinicacardenas.pacientes.ui.components.Panel
import pe.clinicacardenas.pacientes.ui.components.SectionTitle
import pe.clinicacardenas.pacientes.ui.theme.Brand

data class DocumentsState(val loading: Boolean = true, val data: DocumentsDto? = null, val error: String? = null, val opening: Boolean = false)

/** Boletas y facturas SUNAT, recetas, consentimientos y radiografías que la clínica compartió. */
class DocumentsViewModel(private val repo: ClinicRepository, private val platform: PlatformActions) : ViewModel() {
    private val _state = MutableStateFlow(DocumentsState())
    val state: StateFlow<DocumentsState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(data = repo.documents(), loading = false, error = null) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    /** Pide un enlace temporal (10 min) y lo abre en el teléfono. */
    fun open(invoiceId: Long? = null, fileId: Long? = null) {
        _state.update { it.copy(opening = true, error = null) }
        viewModelScope.launch {
            try {
                val url = if (invoiceId != null) repo.invoiceUrl(invoiceId) else repo.fileUrl(fileId!!)
                platform.openUrl(url)
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(opening = false) }
            }
        }
    }
}

private val kindLabel = mapOf(
    "FOTO" to "Foto", "RADIOGRAFIA" to "Radiografía", "DOCUMENTO" to "Documento",
    "CONSENTIMIENTO" to "Consentimiento", "RECETA" to "Receta", "PRESUPUESTO" to "Presupuesto",
)

@Composable
fun DocumentsScreen(vm: DocumentsViewModel = koinViewModel()) {
    val s by vm.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mis documentos", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::load) { Text("Actualizar") }
            }
        }
        if (s.opening) item { Banner("Abriendo documento…", Brand.CelesteSuave, Brand.AzulOscuro) }
        val data = s.data
        when {
            s.loading -> item { Loading() }
            data == null -> item { ErrorState(s.error ?: "No se pudieron cargar tus documentos.", vm::load) }
            else -> {
                s.error?.let { e -> item { Banner(e, Brand.RojoSuave, Brand.Rojo) } }
                item { SectionTitle("Recetas, consentimientos e imágenes") }
                if (data.files.isEmpty()) item { Text("Tu odontólogo aún no compartió documentos contigo.", color = Brand.Tinta2) }
                items(data.files, key = { "f${it.id}" }) { f ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.open(fileId = f.id) }) {
                        Panel(padding = PaddingValues(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (f.contentType == "application/pdf") "📄" else "🖼️", fontSize = 24.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(f.title, fontWeight = FontWeight.SemiBold, color = Brand.Tinta)
                                    Text("${kindLabel[f.kind] ?: f.kind} · ${Fmt.shortDate(f.date)}", color = Brand.Gris, fontSize = 13.sp)
                                }
                                Text("Abrir ›", color = Brand.Azul, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                item { SectionTitle("Comprobantes electrónicos (SUNAT)") }
                if (data.invoices.isEmpty()) item { Text("Aún no tienes comprobantes.", color = Brand.Tinta2) }
                items(data.invoices, key = { "i${it.id}" }) { i ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.open(invoiceId = i.id) }) {
                        Panel(padding = PaddingValues(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${i.type} ${i.number}", fontWeight = FontWeight.SemiBold, color = Brand.Tinta)
                                    Text("${Fmt.shortDate(i.date + "T00:00:00")} · ${Fmt.money(i.total)}", color = Brand.Gris, fontSize = 13.sp)
                                }
                                val ok = i.status == "ACEPTADO"
                                Chip(if (ok) "Aceptado SUNAT" else i.status.lowercase().replaceFirstChar { it.uppercase() }, if (ok) Brand.VerdeSuave else Brand.NaranjaSuave, if (ok) Brand.Verde else Brand.AzulOscuro)
                            }
                        }
                    }
                }
            }
        }
    }
}
