package pe.clinicacardenas.pacientes.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import pe.clinicacardenas.pacientes.db.ClinicaDb

class AndroidDriverFactory(private val context: Context) : DriverFactory {
    override fun create(): SqlDriver = AndroidSqliteDriver(ClinicaDb.Schema, context, "clinica.db")
}
