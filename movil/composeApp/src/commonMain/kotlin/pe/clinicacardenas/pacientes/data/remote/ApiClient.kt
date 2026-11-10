package pe.clinicacardenas.pacientes.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** Error del API con el mensaje del servidor (en español) y, si aplica, los campos a corregir. */
class ApiException(
    override val message: String,
    val code: String? = null,
    val fields: Map<String, String> = emptyMap(),
    val status: Int = 0,
) : Exception(message)

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    // Si el servidor manda null en un campo con valor por defecto, se usa el valor por defecto en vez de fallar.
    coerceInputValues = true
}

fun createHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) { json(ApiJson) }
    install(HttpTimeout) {
        connectTimeoutMillis = 8_000
        requestTimeoutMillis = 15_000
    }
}

/** Ejecuta la llamada y convierte la respuesta: JSON si fue exitosa, ApiException con el mensaje del servidor si no. */
suspend inline fun <reified T> apiCall(block: () -> HttpResponse): T {
    val res = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ApiException(
            "No se pudo conectar con la clínica. Revisa tu internet o la dirección del servidor.",
            code = "SIN_CONEXION",
        )
    }
    if (res.status.isSuccess()) return res.body()
    val err = runCatching { res.body<ApiErrorDto>() }.getOrNull()
    throw ApiException(
        err?.message ?: "La clínica respondió con un error (${res.status.value}).",
        code = err?.code,
        fields = err?.fields ?: emptyMap(),
        status = res.status.value,
    )
}

/**
 * Cliente REST de la app del paciente (U2: conectividad y CRUD REST con Ktor).
 * El servidor y el token se leen en cada llamada, así un cambio de servidor o de sesión aplica al instante.
 */
class ApiClient(
    private val http: HttpClient,
    private val server: () -> String,
    private val token: () -> String?,
) {
    private fun url(path: String) = server().trimEnd('/') + path

    private fun HttpRequestBuilder.auth() {
        token()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    private suspend inline fun <reified T> call(block: () -> HttpResponse): T = apiCall(block)

    suspend fun ping(serverUrl: String): PingDto = call {
        http.get(serverUrl.trimEnd('/') + "/api/portal/auth/ping")
    }

    suspend fun login(body: LoginRequest): SessionDto = call {
        http.post(url("/api/portal/auth/login")) { contentType(ContentType.Application.Json); setBody(body) }
    }

    suspend fun requestCode(body: CodeRequest): CodeResponse = call {
        http.post(url("/api/portal/auth/code")) { contentType(ContentType.Application.Json); setBody(body) }
    }

    suspend fun activate(body: ActivateRequest): SessionDto = call {
        http.post(url("/api/portal/auth/activate")) { contentType(ContentType.Application.Json); setBody(body) }
    }

    suspend fun me(): PatientDto = call { http.get(url("/api/portal/me")) { auth() } }

    suspend fun appointments(): AppointmentsDto = call { http.get(url("/api/portal/appointments")) { auth() } }

    suspend fun confirm(id: Long): AppointmentsDto = call {
        http.post(url("/api/portal/appointments/$id/confirm")) { auth() }
    }

    suspend fun account(): AccountDto = call { http.get(url("/api/portal/account")) { auth() } }

    suspend fun updateChannel(channel: String): PatientDto = call {
        http.patch(url("/api/portal/me/notifications")) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(ChannelRequest(channel))
        }
    }

    // ------------------------------------------------------------ reservas, avisos, pagos, documentos y encuesta

    private suspend inline fun <reified T, reified B : Any> postJson(path: String, body: B): T = call {
        http.post(url(path)) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    suspend fun bookingOptions(): BookingOptionsDto = call { http.get(url("/api/portal/booking/options")) { auth() } }

    suspend fun bookingSlots(dentistId: Long, date: String, reasonCode: String, excludeId: Long?): PortalSlotsDto = call {
        http.get(url("/api/portal/booking/slots")) {
            auth()
            parameter("dentistId", dentistId)
            parameter("date", date)
            parameter("reasonCode", reasonCode)
            if (excludeId != null) parameter("excludeId", excludeId)
        }
    }

    suspend fun book(body: BookRequest): MessageDto = postJson("/api/portal/appointments", body)

    suspend fun reschedule(id: Long, body: RescheduleRequest): MessageDto = postJson("/api/portal/appointments/$id/reschedule", body)

    suspend fun cancel(id: Long, body: CancelRequest): MessageDto = postJson("/api/portal/appointments/$id/cancel", body)

    suspend fun notices(): NoticesDto = call { http.get(url("/api/portal/notices")) { auth() } }

    suspend fun markNoticesRead() {
        runCatching { http.post(url("/api/portal/notices/read")) { auth() } }
    }

    suspend fun pay(body: PayRequest): PayResultDto = postJson("/api/portal/payments", body)

    suspend fun documents(): DocumentsDto = call { http.get(url("/api/portal/documents")) { auth() } }

    suspend fun fileLink(id: Long): LinkDto = call { http.get(url("/api/portal/documents/files/$id/link")) { auth() } }

    suspend fun invoiceLink(id: Long): LinkDto = call { http.get(url("/api/portal/documents/invoices/$id/link")) { auth() } }

    suspend fun pendingSurvey(): SurveyPendingDto = call { http.get(url("/api/portal/surveys/pending")) { auth() } }

    suspend fun sendSurvey(body: SurveyRequest): MessageDto = postJson("/api/portal/surveys", body)

    /** Los enlaces temporales vienen como ruta; el teléfono los abre con la dirección del servidor. */
    fun absolute(path: String): String = if (path.startsWith("http")) path else url(path)

    suspend fun logout() {
        runCatching { http.post(url("/api/portal/logout")) { auth() } }
    }
}
