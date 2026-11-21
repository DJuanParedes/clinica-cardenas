package pe.clinicacardenas.pacientes.data.local

import app.cash.sqldelight.db.SqlDriver
import pe.clinicacardenas.pacientes.db.Cita
import pe.clinicacardenas.pacientes.db.ClinicaDb

/** Fábrica del driver SQLite de cada plataforma (Android / iOS). */
interface DriverFactory {
    fun create(): SqlDriver
}

/**
 * Persistencia multiplataforma con SQLDelight (U2): ajustes de la app, sesión y la última copia de las citas,
 * para que el paciente las vea aunque no tenga conexión.
 */
class LocalStore(factory: DriverFactory) {
    private val db = ClinicaDb(factory.create())
    private val q = db.clinicaQueries

    fun get(key: String): String? = q.obtenerAjuste(key).executeAsOneOrNull()

    fun put(key: String, value: String?) {
        if (value == null) q.borrarAjuste(key) else q.guardarAjuste(key, value)
    }

    fun cachedAppointments(): List<Cita> = q.citas().executeAsList()

    fun replaceAppointments(items: List<Cita>) {
        db.transaction {
            q.borrarCitas()
            items.forEach { q.insertarCita(it) }
        }
    }

    fun clearAppointments() = q.borrarCitas()
}
