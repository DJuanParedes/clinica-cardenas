package pe.clinicacardenas.pacientes

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pe.clinicacardenas.pacientes.data.local.AppointmentLocalRepositoryImpl
import pe.clinicacardenas.pacientes.data.local.DriverFactory
import pe.clinicacardenas.pacientes.data.local.LocalStore
import pe.clinicacardenas.pacientes.data.remote.ApiJson
import pe.clinicacardenas.pacientes.data.remote.SessionDto
import pe.clinicacardenas.pacientes.data.repository.SessionRepository
import pe.clinicacardenas.pacientes.data.repository.StaffSession
import pe.clinicacardenas.pacientes.db.ClinicaDb
import pe.clinicacardenas.pacientes.domain.model.OperationState
import pe.clinicacardenas.pacientes.domain.model.OperationType
import pe.clinicacardenas.pacientes.domain.repository.TokenStorage
import pe.clinicacardenas.pacientes.domain.usecase.SyncAppointmentsUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Base SQLite real (en memoria) con el mismo esquema SQLDelight que usa la app. */
object MemoryDriver : DriverFactory {
    override fun create(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ClinicaDb.Schema.create(it) }
}

class MemoryTokens : TokenStorage {
    val values = mutableMapOf<String, String>()
    override fun read(key: String) = values[key]
    override fun write(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
}

/** Capa de datos local: fuente única de verdad, cola de pendientes y sesión. */
class LocalDataTest {
    private val store = LocalStore(MemoryDriver)
    private val local = AppointmentLocalRepositoryImpl(store)

    @Test
    fun reemplazarDesdeElServicioNoPisaLoPendiente() = runTest {
        local.replaceSynced(listOf(appointment(1), appointment(2)))
        local.markConfirmedPending(2)
        local.insertProvisional(appointment(-10, "2026-12-05T09:00", status = "POR_ENVIAR"))
        local.replaceSynced(listOf(appointment(1), appointment(2, status = "PROGRAMADA"), appointment(3)))
        val list = local.observeAppointments().first()
        assertEquals(listOf(-10L, 1L, 2L, 3L).sorted(), list.map { it.id }.sorted())
        val two = list.first { it.id == 2L }
        assertEquals("CONFIRMADA", two.status, "la confirmación pendiente no se pierde al bajar")
        assertTrue(two.pending)
        assertFalse(two.canConfirm)
    }

    @Test
    fun colaDePendientesConIdProvisionalYReintentos() = runTest {
        local.insertProvisional(appointment(-20, "2026-12-06T10:00", status = "POR_ENVIAR"))
        val opId = local.enqueue(OperationType.RESERVAR, -20, "{}", "clave-1", now = 100)
        val ready = local.readyOperations(now = 100)
        assertEquals(opId, ready.single().id)
        assertEquals("clave-1", ready.single().key)

        local.scheduleRetry(ready.single(), attempts = 1, nextAttemptAt = 500, error = "La clínica no respondió")
        assertTrue(local.readyOperations(now = 200).isEmpty(), "espera antes de reintentar")
        val again = local.readyOperations(now = 500).single()
        assertEquals(1, again.attempts)

        local.completeOperation(again, serverId = 777)
        val list = local.observeAppointments().first()
        assertEquals(777L, list.single().id)
        assertFalse(list.single().pending)
        assertTrue(local.observeOperations().first().isEmpty())
    }

    @Test
    fun operacionFallidaQuedaVisibleYSePuedeReintentarODescartar() = runTest {
        local.replaceSynced(listOf(appointment(5)))
        local.markConfirmedPending(5)
        val id = local.enqueue(OperationType.CONFIRMAR, 5, "", "c-1", now = 0)
        local.failOperation(local.readyOperations(0).single(), attempts = 5, error = "Rechazada")
        val ops = local.observeOperations().first()
        assertEquals(OperationState.FALLIDA, ops.single().state)
        assertEquals("Rechazada", local.observeAppointments().first().single().syncError)

        local.reactivate(id)
        assertEquals(OperationState.PENDIENTE, local.observeOperations().first().single().state)
        assertNull(local.observeAppointments().first().single().syncError)

        local.discard(id)
        assertTrue(local.observeOperations().first().isEmpty())
        assertFalse(local.observeAppointments().first().single().pending)
    }

    @Test
    fun descartarUnaReservaProvisionalBorraSuCita() = runTest {
        local.insertProvisional(appointment(-30, status = "POR_ENVIAR"))
        val id = local.enqueue(OperationType.RESERVAR, -30, "{}", "r-1", now = 0)
        local.discard(id)
        assertTrue(local.observeAppointments().first().isEmpty())
    }

    @Test
    fun sincronizacionCompletaSobreLaBaseReal() = runTest {
        val remote = FakeRemote().apply { server += appointment(9) }
        val clock = FakeClock()
        val sync = SyncAppointmentsUseCase(local, remote, clock)
        local.insertProvisional(appointment(-40, "2026-12-07T08:00", status = "POR_ENVIAR"))
        local.enqueue(OperationType.RESERVAR, -40, "2026-12-07T08:00", "k-9", now = clock.time)
        val r = sync()
        assertEquals(1, r.sent)
        assertTrue(r.pulled)
        assertEquals(setOf(9L, 100L), local.observeAppointments().first().map { it.id }.toSet())
        assertEquals(clock.time, local.observeLastSync().first())
    }

    @Test
    fun laSesionUsaElAlmacenSeguroYCerrarSesionBorraLosDatos() = runTest {
        // una versión anterior dejó el token en SQLite: se migra al almacén seguro
        store.put("token", "viejo")
        val tokens = MemoryTokens()
        val session = SessionRepository(store, FakePlatform(), tokens) { local.resetLastSync() }
        assertEquals("viejo", session.token)
        assertNull(store.get("token"), "el token ya no queda en la base")

        val dto = ApiJson.decodeFromString(SessionDto.serializer(), Fixtures.byRoute.getValue("POST /api/v1/portal/auth/login"))
        session.start(dto)
        assertEquals("token-de-prueba", tokens.values["token"])
        local.replaceSynced(listOf(appointment(1)))
        local.enqueue(OperationType.CONFIRMAR, 1, "", "x", 0)
        local.setLastSync(123)

        session.clear()
        assertNull(session.token)
        assertTrue(local.observeAppointments().first().isEmpty())
        assertTrue(local.observeOperations().first().isEmpty())
        assertNull(local.observeLastSync().first())
    }

    @Test
    fun laSesionDelPersonalTambienVaAlAlmacenSeguro() {
        store.put("personal.token", "t-personal")
        val tokens = MemoryTokens()
        val staff = StaffSession(store, tokens)
        assertEquals("t-personal", staff.token)
        assertNull(store.get("personal.token"))
        staff.clear()
        assertNull(staff.token)
    }
}
