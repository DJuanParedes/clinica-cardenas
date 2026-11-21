package pe.clinicacardenas.pacientes

import android.app.Application
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.dsl.module
import pe.clinicacardenas.pacientes.data.local.AndroidDriverFactory
import pe.clinicacardenas.pacientes.data.local.DriverFactory
import pe.clinicacardenas.pacientes.di.initKoin
import pe.clinicacardenas.pacientes.platform.AndroidPlatformActions
import pe.clinicacardenas.pacientes.platform.PlatformActions

class ClinicaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(
            module {
                single<DriverFactory> { AndroidDriverFactory(this@ClinicaApp) }
                single<PlatformActions> { AndroidPlatformActions(this@ClinicaApp) }
                single { OkHttp.create() }
            },
        )
    }
}
