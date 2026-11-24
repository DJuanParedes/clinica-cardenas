package pe.clinicacardenas.pacientes.data.remote

import kotlinx.coroutines.CancellationException
import pe.clinicacardenas.pacientes.domain.error.DomainError

/**
 * Traduce los errores del API (ApiException, con el formato único de error del servicio) a la jerarquía sellada del
 * dominio. Es la única frontera entre HTTP y las reglas de la app.
 */
fun ApiException.toDomain(): DomainError = when {
    code == "SIN_CONEXION" -> DomainError.SinConexion(message)
    status == 401 -> DomainError.SesionVencida()
    status == 403 -> DomainError.SinPermiso(message)
    status == 404 -> DomainError.NoEncontrado(message)
    status == 400 || status == 422 -> DomainError.Validacion(message, fields)
    status == 409 -> DomainError.Conflicto(message, code)
    status >= 500 -> DomainError.Servidor(status, message)
    else -> DomainError.Desconocido(message)
}

/** Ejecuta una llamada al API y entrega cualquier falla ya traducida al dominio. */
suspend fun <T> domainCall(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: DomainError) {
    throw e
} catch (e: ApiException) {
    throw e.toDomain()
} catch (e: Exception) {
    throw DomainError.Desconocido(e.message ?: "Ocurrió un error inesperado.")
}
