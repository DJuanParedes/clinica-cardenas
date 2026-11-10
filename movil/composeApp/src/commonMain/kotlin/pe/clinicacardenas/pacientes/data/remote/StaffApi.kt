package pe.clinicacardenas.pacientes.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Cliente REST de la app del personal (odontólogo, recepción, caja y dirección).
 * Usa el mismo servidor que la app del paciente y su propio token JWT.
 */
class StaffApi(
    private val http: HttpClient,
    private val server: () -> String,
    private val token: () -> String?,
) {
    private fun url(path: String) = server().trimEnd('/') + path

    private fun HttpRequestBuilder.auth() {
        token()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    private suspend inline fun <reified T> get(path: String, crossinline params: HttpRequestBuilder.() -> Unit = {}): T =
        apiCall { http.get(url(path)) { auth(); params() } }

    private suspend inline fun <reified T, reified B : Any> post(path: String, body: B): T =
        apiCall { http.post(url(path)) { auth(); contentType(ContentType.Application.Json); setBody(body) } }

    suspend fun login(body: StaffLoginRequest): StaffSessionDto =
        apiCall { http.post(url("/api/auth/login")) { contentType(ContentType.Application.Json); setBody(body) } }

    suspend fun me(): StaffUserDto = get("/api/auth/me")

    suspend fun dashboard(): DashboardDto = get("/api/dashboard")

    // ---------------------------------------------------------------- agenda
    suspend fun day(date: String, dentistId: Long?): DayDto = get("/api/agenda/day") {
        parameter("date", date)
        if (dentistId != null) parameter("dentistId", dentistId)
    }

    suspend fun setStatus(id: Long, status: String, reason: String? = null): SAppointmentDto =
        apiCall { http.patch(url("/api/appointments/$id/status")) { auth(); contentType(ContentType.Application.Json); setBody(StatusRequest(status, reason)) } }

    suspend fun dentists(): List<SDentistDto> = get("/api/dentists")

    suspend fun slots(dentistId: Long, date: String, duration: Int): StaffSlotsDto = get("/api/agenda/slots") {
        parameter("dentistId", dentistId)
        parameter("date", date)
        parameter("duration", duration)
    }

    suspend fun createAppointment(body: NewAppointmentRequest): SAppointmentDto = post("/api/appointments", body)

    // ---------------------------------------------------------------- pacientes e historia clínica
    suspend fun searchPatients(q: String): PatientPageDto = get("/api/patients") {
        parameter("q", q)
        parameter("size", 30)
    }

    suspend fun patient(id: Long): PatientDetailDto = get("/api/patients/$id")

    suspend fun history(id: Long): HistoryViewDto = get("/api/patients/$id/history")

    suspend fun plans(id: Long): List<PlanViewDto> = get("/api/patients/$id/plans")

    suspend fun addEvolution(planId: Long, body: EvolutionRequest): PlanViewDto = post("/api/plans/$planId/evolutions", body)

    suspend fun files(patientId: Long): List<SFileDto> = get("/api/patients/$patientId/files")

    suspend fun upload(patientId: Long, name: String, mime: String, bytes: ByteArray, kind: String, title: String, note: String?, share: Boolean): SFileDto =
        apiCall {
            http.submitFormWithBinaryData(
                url = url("/api/patients/$patientId/files"),
                formData = formData {
                    append(
                        "file",
                        bytes,
                        Headers.build {
                            append(HttpHeaders.ContentType, mime)
                            append(HttpHeaders.ContentDisposition, "filename=\"${name.replace("\"", "")}\"")
                        },
                    )
                    append("kind", kind)
                    append("title", title)
                    if (!note.isNullOrBlank()) append("note", note)
                    append("share", share.toString())
                },
            ) { auth() }
        }

    /** Descarga el contenido de un archivo de la ficha (con el token del personal). */
    suspend fun fileContent(id: Long): ByteArray {
        val res = try {
            http.get(url("/api/files/$id/content")) { auth() }
        } catch (e: Exception) {
            throw ApiException("No se pudo descargar el archivo.", code = "SIN_CONEXION")
        }
        if (!res.status.isSuccess()) throw ApiException("No se pudo descargar el archivo (${res.status.value}).", status = res.status.value)
        return res.readBytes()
    }

    // ---------------------------------------------------------------- caja y comprobantes
    suspend fun account(patientId: Long): SAccountDto = get("/api/cash/patients/$patientId/account")

    suspend fun pay(body: PaymentRequest): kotlinx.serialization.json.JsonObject = post("/api/cash/payments", body)

    suspend fun cashToday(): CashTodayDto = get("/api/cash/today")

    suspend fun createInvoice(body: InvoiceCreateRequest): InvoiceCreatedDto = post("/api/billing/invoices", body)
}
