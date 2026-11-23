package pe.clinicacardenas.pacientes

import pe.clinicacardenas.pacientes.data.remote.ApiException
import pe.clinicacardenas.pacientes.data.remote.toDomain
import pe.clinicacardenas.pacientes.domain.error.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Traducción de los errores del API a la jerarquía sellada del dominio. */
class ErrorMapperTest {

    @Test
    fun sinConexionEsReintentable() {
        val e = ApiException("Sin red", code = "SIN_CONEXION").toDomain()
        assertIs<DomainError.SinConexion>(e)
        assertTrue(e.retryable)
    }

    @Test
    fun validacionConservaElDetallePorCampo() {
        val e = ApiException("Revisa los campos.", code = "VALIDACION", fields = mapOf("startAt" to "Elige un horario futuro."), status = 400).toDomain()
        assertIs<DomainError.Validacion>(e)
        assertEquals("Elige un horario futuro.", e.fields["startAt"])
        assertFalse(e.retryable)
    }

    @Test
    fun estadosHttpSeTraducenAlTipoCorrecto() {
        assertIs<DomainError.SesionVencida>(ApiException("x", status = 401).toDomain())
        assertIs<DomainError.SinPermiso>(ApiException("x", status = 403).toDomain())
        assertIs<DomainError.NoEncontrado>(ApiException("x", status = 404).toDomain())
        val conflicto = ApiException("Ese horario ya no está libre.", code = "HORARIO_OCUPADO", status = 409).toDomain()
        assertIs<DomainError.Conflicto>(conflicto)
        assertEquals("HORARIO_OCUPADO", conflicto.code)
        val servidor = ApiException("x", status = 503).toDomain()
        assertIs<DomainError.Servidor>(servidor)
        assertTrue(servidor.retryable)
    }
}
