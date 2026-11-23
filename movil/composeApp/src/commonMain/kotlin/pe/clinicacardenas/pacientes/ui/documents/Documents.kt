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

data class DocumentsState(val loading: Boolean = true, val data: DocumentsDto? = null, val error: String? = null, val opening: Boolean = false,
                          val requests: List<pe.clinicacardenas.pacientes.data.remote.FileRequestDto> = emptyList(),
                          val consents: pe.clinicacardenas.pacientes.data.remote.ConsentsDto? = null,
                          val uploading: Long? = null, val message: String? = null)

/** Boletas y facturas SUNAT, recetas, consentimientos y radiografías que la clínica compartió. */
class DocumentsViewModel(private val repo: ClinicRepository, private val platform: PlatformActions) : ViewModel() {
    private val _state = MutableStateFlow(DocumentsState())
    val state: StateFlow<DocumentsState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(data = repo.documents(), loading = false, error = null) }
                runCatching { repo.fileRequests() }.onSuccess { r -> _state.update { it.copy(requests = r) } }
                runCatching { repo.consents() }.onSuccess { c -> _state.update { it.copy(consents = c) } }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    /** Sube (foto o PDF) el archivo que la clínica pidió: placa, radiografía, informe… */
    fun upload(requestId: Long, source: pe.clinicacardenas.pacientes.platform.PickSource) {
        platform.pickFile(source) { f ->
            if (f == null) return@pickFile
            _state.update { it.copy(uploading = requestId, error = null, message = null) }
            viewModelScope.launch {
                try {
                    val list = repo.uploadRequest(requestId, f.name, f.mimeType, f.bytes)
                    _state.update { it.copy(requests = list, message = "Archivo enviado a la clínica. ¡Gracias!") }
                } catch (e: ApiException) {
                    _state.update { it.copy(error = e.message) }
                } finally {
                    _state.update { it.copy(uploading = null) }
                }
            }
        }
    }

    fun openOwn(fileId: Long) {
        _state.update { it.copy(opening = true, error = null) }
        viewModelScope.launch {
            try { platform.openUrl(repo.ownFileUrl(fileId)) } catch (e: ApiException) { _state.update { it.copy(error = e.message) } }
            finally { _state.update { it.copy(opening = false) } }
        }
    }

    fun consentPdf(id: Long, code: String, version: String) {
        _state.update { it.copy(opening = true, error = null) }
        viewModelScope.launch {
            try { platform.openFile("consentimiento-${code.lowercase()}-v$version.pdf", "application/pdf", repo.consentPdf(id)) }
            catch (e: ApiException) { _state.update { it.copy(error = e.message) } }
            finally { _state.update { it.copy(opening = false) } }
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
                s.message?.let { m -> item { Banner(m, Brand.VerdeSuave, Brand.Verde) } }
                item { SectionTitle("Archivos que te pide la clínica") }
                if (s.requests.isEmpty()) item { Text("La clínica no te ha pedido archivos.", color = Brand.Tinta2) }
                items(s.requests, key = { "r${it.id}" }) { r ->
                    Panel(padding = PaddingValues(14.dp)) {
                        Text(r.title, fontWeight = FontWeight.SemiBold, color = Brand.Tinta)
                        val extra = listOfNotNull(r.planTitle?.let { "Para: $it" }, r.description, r.dueDate?.let { "Antes del ${Fmt.shortDate(it)}" }).joinToString(" · ")
                        if (extra.isNotBlank()) Text(extra, color = Brand.Gris, fontSize = 13.sp)
                        if (r.status == "PENDIENTE") {
                            if (s.uploading == r.id) Text("Subiendo…", color = Brand.Azul)
                            else Row {
                                TextButton(onClick = { vm.upload(r.id, pe.clinicacardenas.pacientes.platform.PickSource.CAMARA) }) { Text("📷 Tomar foto") }
                                TextButton(onClick = { vm.upload(r.id, pe.clinicacardenas.pacientes.platform.PickSource.GALERIA) }) { Text("🖼️ Galería") }
                                TextButton(onClick = { vm.upload(r.id, pe.clinicacardenas.pacientes.platform.PickSource.DOCUMENTO) }) { Text("📄 PDF") }
                            }
                        } else Row(verticalAlignment = Alignment.CenterVertically) {
                            Chip("Recibido", Brand.VerdeSuave, Brand.Verde)
                            r.fileId?.let { id -> TextButton(onClick = { vm.openOwn(id) }) { Text("Ver") } }
                        }
                    }
                }
                item { SectionTitle("Consentimientos firmados") }
                val cs = s.consents
                if (cs == null || cs.records.isEmpty()) item { Text("Sin registros.", color = Brand.Tinta2) }
                else items(cs.records, key = { "c${it.id}" }) { c ->
                    Panel(padding = PaddingValues(14.dp)) {
                        Text(c.title ?: c.code, fontWeight = FontWeight.SemiBold, color = Brand.Tinta)
                        Text("v${c.version} · ${c.acceptedAt?.let { Fmt.shortDate(it) } ?: ""} · ${c.acceptedBy ?: ""}", color = Brand.Gris, fontSize = 13.sp)
                        TextButton(onClick = { vm.consentPdf(c.id, c.code, c.version) }) { Text("📄 Ver constancia") }
                    }
                }
                cs?.signedFileId?.let { id -> item { TextButton(onClick = { vm.openOwn(id) }) { Text("✍️ Ver mi copia firmada") } } }
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
