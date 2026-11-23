package pe.clinicacardenas.pacientes

import kotlinx.coroutines.test.runTest
import pe.clinicacardenas.pacientes.domain.error.DomainError
import pe.clinicacardenas.pacientes.domain.model.BookingRequest
import pe.clinicacardenas.pacientes.domain.model.OperationState
import pe.clinicacardenas.pacientes.domain.usecase.ConfirmAppointmentUseCase
import pe.clinicacardenas.pacientes.domain.usecase.RequestAppointmentUseCase
import pe.clinicacardenas.pacientes.domain.usecase.SyncAppointmentsUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Registro sin conexión, orden de la sincronización y reintento acotado. */
class OfflineSyncTest {
    private val local = FakeLocal()
    private val remote = FakeRemote()
    private val clock = FakeClock()
    private val keys = SeqKeys()
    private val sync = SyncAppointmentsUseCase(local, remote, clock)
    private val request = RequestAppointmentUseCase(local, sync, keys, clock) { it.startAt }
    private val confirm = ConfirmAppointmentUseCase(local, sync, keys, clock)

    private fun booking(at: String = "2026-12-02T09:00") =
        BookingRequest(1, "Dra. Prueba", "General", "#00A3D4", at, 30, "EVALUACION", "Evaluación", null)

    @Test
    fun reservaSinConexionQuedaPendienteConIdProvisional() = runTest {
        remote.offline = true
        val (id, result) = request(booking())
        assertTrue(id < 0, "el id provisional debe ser negativo")
        assertTrue(result.offline)
        val a = local.appointments.value.single()
        assertTrue(a.pending && a.isProvisional)
        assertEquals(OperationState.PENDIENTE, local.operations.value.single().state)
        assertTrue(remote.log.isEmpty(), "sin red no se llama al servicio")
    }

    @Test
    fun alVolverLaRedSeEnviaSinDuplicarYTomaElIdDefinitivo() = runTest {
        remote.offline = true
        request(booking())
        remote.offline = false
        val r = sync()
        assertEquals(1, r.sent)
        val a = local.appointments.value.single()
        assertEquals(100L, a.id)
        assertFalse(a.pending)
        assertTrue(local.operations.value.isEmpty())
        // un segundo envío con la misma clave no crea otra cita
        remote.book("2026-12-02T09:00", "k1")
        assertEquals(1, remote.server.size)
    }

    @Test
    fun laSincronizacionSubeAntesDeBajar() = runTest {
        remote.server += appointment(5)
        local.replaceSynced(listOf(appointment(5)))
        remote.offline = true
        confirm(5)
        request(booking())
        remote.offline = false
        remote.log.clear()
        sync()
        assertEquals(listOf("confirm:5", "book:k2", "fetch"), remote.log)
        assertEquals("CONFIRMADA", local.appointments.value.first { it.id == 5L }.status)
        assertEquals(2, local.appointments.value.size)
    }

    @Test
    fun bajarNoBorraLoCreadoSinConexion() = runTest {
        remote.offline = true
        request(booking())
        local.replaceSynced(listOf(appointment(7)))
        assertEquals(2, local.appointments.value.size)
        assertTrue(local.appointments.value.any { it.isProvisional && it.pending })
    }

    @Test
    fun reintentoAcotadoConEsperaCreciente() = runTest {
        remote.offline = true
        request(booking())
        remote.offline = false
        repeat(SyncAppointmentsUseCase.MAX_ATTEMPTS) { n ->
            remote.failNextWith = DomainError.Servidor(503)
            sync()
            val op = local.operations.value.single()
            if (n < SyncAppointmentsUseCase.MAX_ATTEMPTS - 1) {
                assertEquals(OperationState.PENDIENTE, op.state)
                assertEquals(clock.time + SyncAppointmentsUseCase.backoff(n + 1), op.nextAttemptAt)
                clock.time = op.nextAttemptAt
            } else {
                assertEquals(OperationState.FALLIDA, op.state)
            }
        }
        assertTrue(local.appointments.value.single().syncError != null, "la falla queda visible en la cita")
    }

    @Test
    fun unRechazoDeNegocioNoSeReintenta() = runTest {
        remote.offline = true
        request(booking())
        remote.offline = false
        remote.failNextWith = DomainError.Conflicto("Ese horario ya no está libre.", "HORARIO_OCUPADO")
        val r = sync()
        assertEquals(1, r.failed)
        assertEquals(OperationState.FALLIDA, local.operations.value.single().state)
        assertEquals(1, local.operations.value.single().attempts)
    }

    @Test
    fun sinRedNoSeBajaNada() = runTest {
        remote.offline = true
        val r = sync()
        assertTrue(r.offline)
        assertFalse(r.pulled)
        assertTrue(remote.log.isEmpty())
    }

    @Test
    fun laFaltaDeRedNoGastaIntentos() = runTest {
        remote.offline = true
        request(booking())
        sync()
        sync()
        val op = local.operations.value.single()
        assertEquals(0, op.attempts)
        assertEquals(0L, op.nextAttemptAt)
    }
}
