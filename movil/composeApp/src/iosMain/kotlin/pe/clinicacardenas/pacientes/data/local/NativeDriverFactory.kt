package pe.clinicacardenas.pacientes.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import pe.clinicacardenas.pacientes.db.ClinicaDb

class NativeDriverFactory : DriverFactory {
    override fun create(): SqlDriver = NativeSqliteDriver(ClinicaDb.Schema, "clinica.db")
}
