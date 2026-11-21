package pe.clinicacardenas.pacientes

import androidx.compose.ui.window.ComposeUIViewController
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module
import pe.clinicacardenas.pacientes.data.local.DriverFactory
import pe.clinicacardenas.pacientes.data.local.NativeDriverFactory
import pe.clinicacardenas.pacientes.di.initKoin
import pe.clinicacardenas.pacientes.platform.IosPlatformActions
import pe.clinicacardenas.pacientes.platform.PlatformActions
import platform.UIKit.UIViewController

/** Lo llama ContentView.swift: monta la misma interfaz Compose dentro de la app de iOS. */
fun MainViewController(): UIViewController {
    initKoin(
        module {
            single<DriverFactory> { NativeDriverFactory() }
            single<PlatformActions> { IosPlatformActions() }
            single { Darwin.create() }
        },
    )
    return ComposeUIViewController { App() }
}
