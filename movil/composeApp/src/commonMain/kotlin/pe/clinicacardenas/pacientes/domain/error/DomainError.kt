package pe.clinicacardenas.pacientes.domain.error

/**
 * Jerarquía sellada de errores del dominio. La capa de datos traduce aquí cualquier error del API o de la red,
 * de modo que los casos de uso y las pantallas deciden con un `when` exhaustivo, sin conocer HTTP ni Ktor.
 */
sealed class DomainError(override val message: String) : Exception(message) {
    /** No hay red o el servicio no respondió: se puede reintentar más tarde. */
    data class SinConexion(override val message: String = "Sin conexión con la clínica.") : DomainError(message)

    /** Token vencido o inválido (401): hay que volver a iniciar sesión. */
    data class SesionVencida(override val message: String = "Tu sesión venció. Vuelve a ingresar.") : DomainError(message)

    /** El usuario no tiene el privilegio necesario (403). */
    data class SinPermiso(override val message: String = "No tienes permiso para esta acción.") : DomainError(message)

    /** Datos rechazados por el servicio (400), con el detalle por campo. */
    data class Validacion(override val message: String, val fields: Map<String, String> = emptyMap()) : DomainError(message)

    /** El estado cambió en el servicio y la operación ya no aplica (409), p. ej. el horario se ocupó. */
    data class Conflicto(override val message: String, val code: String? = null) : DomainError(message)

    /** Recurso inexistente (404). */
    data class NoEncontrado(override val message: String = "No se encontró lo que buscabas.") : DomainError(message)

    /** Falla del servicio (5xx): se puede reintentar. */
    data class Servidor(val status: Int, override val message: String = "La clínica tuvo un problema. Inténtalo más tarde.") : DomainError(message)

    /** Cualquier otro caso no previsto. */
    data class Desconocido(override val message: String = "Ocurrió un error inesperado.") : DomainError(message)

    /** Solo se reintenta solo lo que pudo fallar por la red o por el servicio, nunca un rechazo de negocio. */
    val retryable: Boolean get() = this is SinConexion || this is Servidor
}
