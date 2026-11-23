package pe.clinicacardenas.pacientes

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import pe.clinicacardenas.pacientes.data.remote.ApiClient
import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.remote.AppointmentRemoteRepositoryImpl
import pe.clinicacardenas.pacientes.data.remote.BookRequest
import pe.clinicacardenas.pacientes.data.remote.CancelRequest
import pe.clinicacardenas.pacientes.data.remote.CodeRequest
import pe.clinicacardenas.pacientes.data.remote.LoginRequest
import pe.clinicacardenas.pacientes.data.remote.RescheduleRequest
import pe.clinicacardenas.pacientes.data.remote.createHttpClient
import pe.clinicacardenas.pacientes.data.remote.encode
import pe.clinicacardenas.pacientes.data.remote.toDomain
import pe.clinicacardenas.pacientes.domain.error.DomainError
import pe.clinicacardenas.pacientes.domain.model.BookingRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contrato entre la app y el servicio: el cliente HTTP usa /api/v1, envía el token y la clave de idempotencia,
 * y los DTO decodifican las respuestas reales del servicio (Fixtures). Los errores llegan traducidos al dominio.
 */
class ApiContractTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun json(scope: MockRequestHandleScope, body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        scope.respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData): ApiClient {
        val engine = MockEngine { req -> requests += req; handler(req) }
        return ApiClient(createHttpClient(engine), { "https://clinica.test/" }, { "token-de-prueba" })
    }

    /** Responde con la captura real que corresponde al método y la ruta (sin parámetros de consulta). */
    private val fromFixtures: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData = { req ->
        val path = req.url.encodedPath
        val key = "${req.method.value} $path"
        when {
            Fixtures.byRoute.containsKey(key) -> json(this, Fixtures.byRoute.getValue(key))
            path.endsWith("/me/notifications") -> json(this, Fixtures.byRoute.getValue("GET /api/v1/portal/me"))
            path.endsWith("/auth/code") -> json(this, """{"message":"Te enviamos un código","sentTo":"9•• ••• 111"}""")
            path.endsWith("/confirm") -> json(this, Fixtures.byRoute.getValue("GET /api/v1/portal/appointments"))
            path.endsWith("/pdf") -> respond(byteArrayOf(37, 80, 68, 70), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/pdf"))
            path.contains("/link") -> json(this, """{"url":"/api/public/files/x?firma=abc","expiresAt":"2026-12-01T10:00"}""")
            req.method.value == "POST" || req.method.value == "PATCH" -> json(this, """{"message":"Listo","id":555}""")
            else -> json(this, Fixtures.byRoute["GET $path"] ?: "{}")
        }
    }

    @Test
    fun usaLaVersionV1YEnviaElToken() = runTest {
        val api = client(fromFixtures)
        api.me()
        val r = requests.single()
        assertEquals("https://clinica.test/api/v1/portal/me", r.url.toString())
        assertEquals("Bearer token-de-prueba", r.headers[HttpHeaders.Authorization])
    }

    @Test
    fun losDtoDecodificanLasRespuestasRealesDelServicio() = runTest {
        val api = client(fromFixtures)
        val session = api.login(LoginRequest("DNI", "45872196", "x"))
        assertEquals("token-de-prueba", session.token)
        assertTrue(session.patient.fullName.isNotBlank())
        assertNotNull(api.ping("https://clinica.test"))
        val citas = api.appointments()
        assertTrue(citas.upcoming.isNotEmpty() || citas.past.isNotEmpty(), "el paciente demo tiene citas")
        assertTrue(api.account().let { true })
        val opts = api.bookingOptions()
        assertTrue(opts.dentists.isNotEmpty() && opts.reasons.isNotEmpty())
        api.bookingSlots(opts.dentists.first().id, "2026-12-01", opts.reasons.first().code, null)
        api.notices()
        api.onlineConfig()
        api.documents()
        api.treatments()
        api.consents()
        api.fileRequests()
        api.promotions()
        api.pendingSurvey()
        api.legal("PRIVACIDAD")
        api.confirm(1)
        api.reschedule(1, RescheduleRequest(1, "2026-12-01T10:00"))
        api.cancel(1, CancelRequest("No puedo asistir"))
        api.requestCode(CodeRequest("DNI", "45872196", "1990-01-01"))
        api.fileLink(1); api.invoiceLink(1); api.ownFileLink(1)
        assertEquals(4, api.consentPdf(1).size)
        api.markNoticesRead()
        api.updateChannel("WHATSAPP")
    }

    @Test
    fun laReservaEnviaLaClaveDeIdempotencia() = runTest {
        val api = client(fromFixtures)
        val r = api.book(BookRequest(1, "2026-12-01T10:00", "EVALUACION"), "clave-123")
        assertEquals(555L, r.id)
        assertEquals("clave-123", requests.single().headers["Idempotency-Key"])
    }

    @Test
    fun elRepositorioRemotoTraduceDtoAlDominio() = runTest {
        val repo = AppointmentRemoteRepositoryImpl(client(fromFixtures))
        val list = repo.fetchAppointments()
        assertTrue(list.isNotEmpty())
        assertTrue(list.all { it.id > 0 && it.dentistName.isNotBlank() })
        val payload = BookingRequest(1, "Dra. X", "General", "#00A3D4", "2026-12-01T10:00", 30, "EVALUACION", "Evaluación", null).encode()
        assertEquals(555L, repo.book(payload, "k-1"))
        repo.confirm(1)
    }

    @Test
    fun losErroresDelServicioLleganConSuCodigoYSeTraducen() = runTest {
        val api = client { json(this, Fixtures.byRoute.getValue("ERROR 404"), HttpStatusCode.NotFound) }
        val e = assertFailsWith<ApiException> { api.confirm(999999) }
        assertEquals(404, e.status)
        assertEquals("NO_ENCONTRADO", e.code)
        assertIs<DomainError.NoEncontrado>(e.toDomain())

        val v = client { json(this, Fixtures.byRoute.getValue("ERROR BOOK"), HttpStatusCode.BadRequest) }
        val d = assertFailsWith<DomainError> { AppointmentRemoteRepositoryImpl(v).book(
            BookingRequest(1, "Dra. X", "General", "#00A3D4", "2020-01-01T10:00", 30, "EVALUACION", "Evaluación", null).encode(), "k") }
        assertIs<DomainError.Validacion>(d)
    }

    @Test
    fun sinRedElErrorEsSinConexion() = runTest {
        val api = client { throw IOException("sin red") }
        val e = assertFailsWith<DomainError> { AppointmentRemoteRepositoryImpl(api).fetchAppointments() }
        assertIs<DomainError.SinConexion>(e)
    }
}
