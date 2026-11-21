package pe.clinicacardenas.pacientes.ui.staff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import pe.clinicacardenas.pacientes.data.remote.EvolutionRequest
import pe.clinicacardenas.pacientes.data.remote.HistoryViewDto
import pe.clinicacardenas.pacientes.data.remote.InvoiceCreateRequest
import pe.clinicacardenas.pacientes.data.remote.InvoiceLine
import pe.clinicacardenas.pacientes.data.remote.PatientDetailDto
import pe.clinicacardenas.pacientes.data.remote.PaymentRequest
import pe.clinicacardenas.pacientes.data.remote.PlanViewDto
import pe.clinicacardenas.pacientes.data.remote.SAccountDto
import pe.clinicacardenas.pacientes.data.remote.SFileDto
import pe.clinicacardenas.pacientes.data.repository.StaffRepository
import pe.clinicacardenas.pacientes.data.repository.StaffSession
import pe.clinicacardenas.pacientes.platform.PickSource
import pe.clinicacardenas.pacientes.platform.PickedFile
import pe.clinicacardenas.pacientes.platform.PlatformActions
import pe.clinicacardenas.pacientes.ui.components.BackHeader
import pe.clinicacardenas.pacientes.ui.components.Banner
import pe.clinicacardenas.pacientes.ui.components.Chip
import pe.clinicacardenas.pacientes.ui.components.Field
import pe.clinicacardenas.pacientes.ui.components.Fmt
import pe.clinicacardenas.pacientes.ui.components.Loading
import pe.clinicacardenas.pacientes.ui.components.Panel
import pe.clinicacardenas.pacientes.ui.components.Pill
import pe.clinicacardenas.pacientes.ui.components.PrimaryButton
import pe.clinicacardenas.pacientes.ui.components.SecondaryButton
import pe.clinicacardenas.pacientes.ui.components.SectionTitle
import pe.clinicacardenas.pacientes.ui.theme.Brand

data class RecordState(
    val loading: Boolean = true,
    val detail: PatientDetailDto? = null,
    val history: HistoryViewDto? = null,
    val plans: List<PlanViewDto> = emptyList(),
    val files: List<SFileDto> = emptyList(),
    val account: SAccountDto? = null,
    val error: String? = null,
    val message: String? = null,
    val busy: Boolean = false,
    val pendingFile: PickedFile? = null,
    val lastPayment: Double? = null,
    val invoiceDone: String? = null,
)

/** Ficha del paciente en el teléfono: historia, plan y evolución, archivos (cámara) y cuenta. */
class PatientRecordViewModel(
    private val repo: StaffRepository,
    private val session: StaffSession,
    private val platform: PlatformActions,
) : ViewModel() {
    private val _state = MutableStateFlow(RecordState())
    val state: StateFlow<RecordState> = _state.asStateFlow()
    private var id = 0L

    fun can(vararg p: String) = session.can(*p)

    fun start(patientId: Long) {
        if (id == patientId) return
        id = patientId
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.detail == null, error = null) }
        viewModelScope.launch {
            try {
                val detail = repo.guarded { patient(id) }
                _state.update { it.copy(detail = detail, loading = false) }
                if (can("HISTORIA_CLINICA")) runCatching { repo.guarded { history(id) } }.onSuccess { h -> _state.update { it.copy(history = h) } }
                if (can("PLAN_TRATAMIENTO", "HISTORIA_CLINICA")) runCatching { repo.guarded { plans(id) } }.onSuccess { p -> _state.update { it.copy(plans = p) } }
                runCatching { repo.guarded { files(id) } }.onSuccess { f -> _state.update { it.copy(files = f) } }
                if (can("CAJA_COBRAR")) runCatching { repo.guarded { account(id) } }.onSuccess { a -> _state.update { it.copy(account = a) } }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message, loading = false) }
            }
        }
    }

    private fun act(success: String, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(message = success) }
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.fields.values.firstOrNull() ?: e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun addEvolution(planId: Long, itemId: Long?, notes: String, done: Boolean) = act("Sesión registrada en la historia clínica.") {
        val updated = repo.guarded { addEvolution(planId, EvolutionRequest(itemId, today().toString(), notes.trim(), done && itemId != null)) }
        _state.update { s -> s.copy(plans = s.plans.map { if (it.plan.id == planId) updated else it }) }
    }

    fun pick(source: PickSource) {
        platform.pickFile(source) { f ->
            if (f == null) return@pickFile
            if (f.bytes.size > 10 * 1024 * 1024) _state.update { it.copy(message = "El archivo supera 10 MB.") }
            else _state.update { it.copy(pendingFile = f) }
        }
    }

    fun discardFile() = _state.update { it.copy(pendingFile = null) }

    fun upload(kind: String, title: String, note: String, share: Boolean) {
        val f = _state.value.pendingFile ?: return
        act("Archivo guardado en la ficha.") {
            val saved = repo.guarded { upload(id, f.name, f.mimeType, f.bytes, kind, title.ifBlank { f.name.substringBeforeLast('.') }, note, share) }
            _state.update { s -> s.copy(files = listOf(saved) + s.files, pendingFile = null) }
        }
    }

    fun openFile(f: SFileDto) = act("Abriendo ${f.title}…") {
        val bytes = repo.guarded { fileContent(f.id) }
        val ext = when (f.contentType) { "application/pdf" -> ".pdf"; "image/png" -> ".png"; else -> ".jpg" }
        platform.openFile(f.title + ext, f.contentType, bytes)
    }

    fun pay(amount: Double, method: String, reference: String, itemId: Long?) = act("Cobro registrado.") {
        repo.guarded { pay(PaymentRequest(id, itemId, amount, method, reference.ifBlank { null }, "Registrado desde la app")) }
        _state.update { it.copy(lastPayment = amount, invoiceDone = null) }
        _state.update { it.copy(account = repo.guarded { account(id) }) }
    }

    /** Boleta electrónica por el último cobro (se envía a SUNAT). */
    fun invoice() {
        val p = _state.value.detail?.patient ?: return
        val amount = _state.value.lastPayment ?: return
        val docType = when (p.docType) { "DNI" -> "1"; "CE" -> "4"; "PAS" -> "7"; else -> "0" }
        act("Boleta emitida.") {
            val r = repo.guarded {
                createInvoice(
                    InvoiceCreateRequest("03", id, docType, p.docNumber, p.fullName, null, listOf(InvoiceLine("Servicio odontológico", 1.0, amount))),
                )
            }
            _state.update { it.copy(invoiceDone = r.invoice.fullNumber + (r.statusMessage?.let { m -> " · $m" } ?: ""), lastPayment = null) }
        }
    }
}

private val kinds = listOf("FOTO" to "Foto", "RADIOGRAFIA" to "Radiografía", "DOCUMENTO" to "Documento")
private val payMethods = listOf("EFECTIVO" to "Efectivo", "YAPE" to "Yape", "PLIN" to "Plin", "TARJETA" to "Tarjeta", "TRANSFERENCIA" to "Transferencia")
private val itemLabel = mapOf("PENDIENTE" to "Pendiente", "EN_PROCESO" to "En proceso", "REALIZADO" to "Realizado")

@Composable
fun PatientRecordScreen(patientId: Long, initialSection: String, onBack: () -> Unit, vm: PatientRecordViewModel = koinViewModel(key = "ficha-$patientId")) {
    LaunchedEffect(patientId) { vm.start(patientId) }
    val s by vm.state.collectAsState()
    val sections = buildList {
        add("resumen" to "Resumen")
        if (vm.can("PLAN_TRATAMIENTO", "HISTORIA_CLINICA")) add("plan" to "Plan y evolución")
        add("archivos" to "Archivos")
        if (vm.can("CAJA_COBRAR")) add("cuenta" to "Cuenta")
    }
    var section by remember { mutableStateOf(sections.firstOrNull { it.first == initialSection }?.first ?: "resumen") }
    val p = s.detail?.patient

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            BackHeader(
                p?.fullName ?: "Ficha del paciente",
                onBack,
                subtitle = p?.let { listOfNotNull("${it.docType} ${it.docNumber}", it.age?.let { a -> "$a años" }, it.phone).joinToString(" · ") },
            )
        }
        if (s.loading) {
            item { Loading() }
            return@LazyColumn
        }
        if (p == null) {
            item { Banner(s.error ?: "No se pudo abrir la ficha.", Brand.RojoSuave, Brand.Rojo) }
            return@LazyColumn
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sections) { (k, l) -> Pill(l, section == k, { section = k }) }
            }
        }
        s.message?.let { m -> item { Banner(m, Brand.CelesteSuave, Brand.AzulOscuro) } }
        when (section) {
            "plan" -> planSection(s, vm)
            "archivos" -> filesSection(s, vm)
            "cuenta" -> accountSection(s, vm)
            else -> summarySection(s)
        }
    }
}

private fun LazyListScope.summarySection(s: RecordState) {
    val h = s.history
    if (h != null && h.alerts.isNotEmpty()) item {
        Banner("⚠️ " + h!!.alerts.joinToString(" · "), Brand.RojoSuave, Brand.Rojo)
    }
    item {
        Panel {
            val p = s.detail!!.patient
            Text("Datos", fontWeight = FontWeight.Bold, color = Brand.Tinta)
            Spacer(Modifier.height(6.dp))
            KV("Celular", p.phone ?: "—")
            KV("Correo", p.email ?: "—")
            KV("Citas registradas", s.detail!!.appointmentsCount.toString())
            if (!p.notes.isNullOrBlank()) KV("Notas", p.notes!!)
        }
    }
    if (h?.history != null) item {
        Panel {
            val x = h!!.history!!
            Text("Historia clínica", fontWeight = FontWeight.Bold, color = Brand.Tinta)
            Spacer(Modifier.height(6.dp))
            KV("Alergias", x.allergies?.ifBlank { null } ?: "Ninguna registrada")
            KV("Antecedentes", x.medicalBackground?.ifBlank { null } ?: "—")
            KV("Medicación actual", x.currentMedication?.ifBlank { null } ?: "—")
            KV("Grupo sanguíneo", x.bloodType?.ifBlank { null } ?: "—")
            if (!x.observations.isNullOrBlank()) KV("Observaciones", x.observations!!)
        }
    }
    if (h != null && h.diagnoses.isNotEmpty()) {
        item { SectionTitle("Diagnósticos") }
        items(h!!.diagnoses.take(10), key = { "d${it.id}" }) { d ->
            Panel(padding = PaddingValues(12.dp)) {
                Text(d.description, color = Brand.Tinta)
                Text(listOfNotNull(d.tooth?.let { "Pieza $it" }, d.createdAt?.let(Fmt::shortDate)).joinToString(" · "), color = Brand.Gris, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun KV(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, color = Brand.Gris, fontSize = 13.sp, modifier = Modifier.width(130.dp))
        Text(v, color = Brand.Tinta, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}

private fun LazyListScope.planSection(s: RecordState, vm: PatientRecordViewModel) {
    if (s.plans.isEmpty()) {
        item { Panel { Text("Este paciente no tiene plan de tratamiento. Créalo desde la web.", color = Brand.Tinta2) } }
        return
    }
    items(s.plans.filter { it.plan.status != "ANULADO" }, key = { "p${it.plan.id}" }) { v ->
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.plan.title, fontWeight = FontWeight.Bold, color = Brand.Tinta, modifier = Modifier.weight(1f))
                Text("${Fmt.money(v.doneAmount)} / ${Fmt.money(v.total)}", color = Brand.Gris, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            v.items.forEach { i ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(i.procedure.name + (i.tooth?.let { " · pieza $it" } ?: ""), color = Brand.Tinta, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    val done = i.status == "REALIZADO"
                    Chip(itemLabel[i.status] ?: i.status, if (done) Brand.VerdeSuave else Brand.Fondo, if (done) Brand.Verde else Brand.Tinta2)
                }
            }
            if (v.plan.status == "ACTIVO" && vm.can("PLAN_TRATAMIENTO")) {
                Spacer(Modifier.height(10.dp))
                EvolutionForm(v, s.busy) { item, notes, done -> vm.addEvolution(v.plan.id, item, notes, done) }
            }
            if (v.evolutions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Evolución", fontWeight = FontWeight.SemiBold, color = Brand.Tinta)
                v.evolutions.take(6).forEach { e ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text("${Fmt.shortDate(e.sessionDate + "T00:00:00")} · ${e.createdBy}", color = Brand.Gris, fontSize = 12.sp)
                        Text(e.notes, color = Brand.Tinta, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EvolutionForm(v: PlanViewDto, busy: Boolean, onSave: (Long?, String, Boolean) -> Unit) {
    var itemId by remember(v.plan.id) { mutableStateOf<Long?>(null) }
    var notes by remember(v.plan.id) { mutableStateOf("") }
    var done by remember(v.plan.id) { mutableStateOf(false) }
    Text("Registrar sesión de hoy", fontWeight = FontWeight.SemiBold, color = Brand.Tinta)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        Pill("General", itemId == null, { itemId = null; done = false })
        v.items.filter { it.status != "REALIZADO" }.forEach { i -> Pill(i.procedure.name + (i.tooth?.let { " $it" } ?: ""), itemId == i.id, { itemId = i.id }) }
    }
    OutlinedTextField(
        value = notes,
        onValueChange = { notes = it.take(1000) },
        label = { Text("Qué se realizó, materiales, indicaciones…") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    if (itemId != null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { done = !done }) {
            Checkbox(checked = done, onCheckedChange = { done = it })
            Text("Procedimiento terminado (descuenta materiales del inventario)", fontSize = 13.sp, color = Brand.Tinta2)
        }
    }
    Spacer(Modifier.height(6.dp))
    PrimaryButton("Guardar sesión", {
        onSave(itemId, notes, done)
        notes = ""
        done = false
    }, loading = busy, enabled = notes.isNotBlank())
}

private fun LazyListScope.filesSection(s: RecordState, vm: PatientRecordViewModel) {
    val canUpload = vm.can("HISTORIA_CLINICA", "PACIENTES_EDITAR")
    if (canUpload) item {
        val pending = s.pendingFile
        if (pending == null) {
            Panel {
                Text("Agregar a la ficha", fontWeight = FontWeight.Bold, color = Brand.Tinta)
                Text("Fotos intraorales, radiografías o documentos (PDF).", color = Brand.Tinta2, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("📷 Cámara", { vm.pick(PickSource.CAMARA) }, Modifier.weight(1f))
                    SecondaryButton("🖼️ Galería", { vm.pick(PickSource.GALERIA) }, Modifier.weight(1f))
                    SecondaryButton("📄 Archivo", { vm.pick(PickSource.DOCUMENTO) }, Modifier.weight(1f))
                }
            }
        } else {
            UploadForm(pending, s.busy, vm::upload, vm::discardFile)
        }
    }
    if (s.files.isEmpty()) item { Text("Aún no hay archivos en la ficha.", color = Brand.Tinta2) }
    items(s.files, key = { "f${it.id}" }) { f ->
        Panel(Modifier.clickable { vm.openFile(f) }, padding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (f.contentType == "application/pdf") "📄" else "🖼️", fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.title, fontWeight = FontWeight.SemiBold, color = Brand.Tinta)
                    Text(
                        listOfNotNull(f.kind.lowercase().replaceFirstChar { it.uppercase() }, f.tooth?.let { "pieza $it" }, Fmt.shortDate(f.createdAt)).joinToString(" · "),
                        color = Brand.Gris,
                        fontSize = 12.sp,
                    )
                }
                if (f.sharedWithPatient) Chip("En su app", Brand.CelesteSuave, Brand.AzulOscuro)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UploadForm(f: PickedFile, busy: Boolean, onUpload: (String, String, String, Boolean) -> Unit, onDiscard: () -> Unit) {
    var kind by remember(f) { mutableStateOf(if (f.mimeType == "application/pdf") "DOCUMENTO" else "FOTO") }
    var title by remember(f) { mutableStateOf(f.name.substringBeforeLast('.')) }
    var note by remember(f) { mutableStateOf("") }
    var share by remember(f) { mutableStateOf(false) }
    Panel {
        Text("Nuevo archivo · ${f.bytes.size / 1024} KB", fontWeight = FontWeight.Bold, color = Brand.Tinta)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            kinds.forEach { (k, l) -> Pill(l, kind == k, { kind = k }) }
        }
        Field(title, { title = it.take(150) }, "Título")
        Field(note, { note = it.take(500) }, "Nota (pieza, hallazgo…)")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { share = !share }) {
            Checkbox(checked = share, onCheckedChange = { share = it })
            Text("Compartir con el paciente en su app", fontSize = 14.sp, color = Brand.Tinta2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Descartar", onDiscard, Modifier.weight(1f))
            PrimaryButton("Subir", { onUpload(kind, title, note, share) }, Modifier.weight(1f), loading = busy)
        }
    }
}

private fun LazyListScope.accountSection(s: RecordState, vm: PatientRecordViewModel) {
    val a = s.account
    if (a == null) {
        item { Loading() }
        return
    }
    item {
        Panel {
            Text(if (a.balance > 0.005) "Saldo pendiente" else "Al día", color = Brand.Tinta2)
            Text(Fmt.money(a.balance.coerceAtLeast(0.0)), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = if (a.balance > 0.005) Brand.Rojo else Brand.Verde)
            if (a.inFavor > 0.005) Text("Saldo a favor: ${Fmt.money(a.inFavor)}", color = Brand.Verde)
        }
    }
    s.invoiceDone?.let { item { Banner("Boleta $it", Brand.VerdeSuave, Brand.Verde) } }
    if (s.lastPayment != null && vm.can("COMPROBANTES")) item {
        PrimaryButton("Emitir boleta por ${Fmt.money(s.lastPayment!!)}", vm::invoice, loading = s.busy, color = Brand.Verde)
    }
    item { PaymentForm(a, s.busy) { amount, method, ref, item -> vm.pay(amount, method, ref, item) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentForm(a: SAccountDto, busy: Boolean, onPay: (Double, String, String, Long?) -> Unit) {
    val pending = a.obligations.filter { it.pending > 0.005 }
    var itemId by remember { mutableStateOf<Long?>(null) }
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("EFECTIVO") }
    var reference by remember { mutableStateOf("") }
    Panel {
        Text("Registrar cobro", fontWeight = FontWeight.Bold, color = Brand.Tinta)
        if (pending.isNotEmpty()) {
            Text("Aplicar a", color = Brand.Gris, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                Pill("A cuenta", itemId == null, { itemId = null })
                pending.forEach { o ->
                    Pill("${o.procedure}${o.tooth?.let { " $it" } ?: ""} · ${Fmt.money(o.pending)}", itemId == o.treatmentItemId, {
                        itemId = o.treatmentItemId
                        amount = ((o.pending * 100).toLong() / 100.0).toString()
                    })
                }
            }
        }
        Field(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }.take(9) }, "Monto (S/)", keyboard = KeyboardType.Decimal)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            payMethods.forEach { (k, l) -> Pill(l, method == k, { method = k }) }
        }
        if (method != "EFECTIVO") Field(reference, { reference = it.take(60) }, "N.° de operación")
        Spacer(Modifier.height(6.dp))
        PrimaryButton("Cobrar", {
            onPay(amount.toDoubleOrNull() ?: 0.0, method, reference, itemId)
            reference = ""
        }, loading = busy, enabled = (amount.toDoubleOrNull() ?: 0.0) > 0)
    }
}
