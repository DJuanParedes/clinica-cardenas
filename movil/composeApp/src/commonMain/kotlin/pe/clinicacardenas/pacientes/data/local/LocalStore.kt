package pe.clinicacardenas.pacientes.data.local

import app.cash.sqldelight.db.SqlDriver
import pe.clinicacardenas.pacientes.db.ClinicaDb

/** Fábrica del driver SQLite de cada plataforma (Android / iOS). */
fun interface DriverFactory {
    fun create(): SqlDriver
}

/**
 * Base local multiplataforma con SQLDelight: ajustes de la app, citas y cola de operaciones pendientes.
 * El token de sesión ya no se guarda aquí sino en el almacén seguro de cada plataforma (TokenStorage).
 */
class LocalStore(factory: DriverFactory) {
    val db = ClinicaDb(factory.create())
    val q = db.clinicaQueries

    fun get(key: String): String? = q.obtenerAjuste(key).executeAsOneOrNull()

    fun put(key: String, value: String?) {
        if (value == null) q.borrarAjuste(key) else q.guardarAjuste(key, value)
    }

    /** Al cerrar sesión se borran las citas y la cola: no deben quedar datos del paciente en el teléfono. */
    fun clearPatientData() {
        db.transaction {
            q.borrarCitas()
            q.borrarOperaciones()
        }
    }
}
