package pe.clinicacardenas.pacientes.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pe.clinicacardenas.pacientes.data.local.LocalStore
import pe.clinicacardenas.pacientes.data.remote.ApiClient
import pe.clinicacardenas.pacientes.data.remote.createHttpClient
import pe.clinicacardenas.pacientes.data.repository.ClinicRepository
import pe.clinicacardenas.pacientes.data.repository.SessionRepository
import pe.clinicacardenas.pacientes.ui.account.AccountViewModel
import pe.clinicacardenas.pacientes.ui.home.HomeViewModel
import pe.clinicacardenas.pacientes.ui.login.LoginViewModel
import pe.clinicacardenas.pacientes.ui.profile.ProfileViewModel

/**
 * Inyección de dependencias con Koin. Cada plataforma aporta su módulo con el motor HTTP,
 * el driver de SQLite y sus acciones nativas (PlatformActions).
 */
val appModule = module {
    single { LocalStore(get()) }
    single { SessionRepository(get(), get()) }
    single { createHttpClient(get()) }
    single {
        val session = get<SessionRepository>()
        ApiClient(get(), server = { session.serverUrl }, token = { session.token })
    }
    single { ClinicRepository(get(), get(), get(), get()) }
    viewModel { LoginViewModel(get(), get()) }
    viewModel { HomeViewModel(get()) }
    viewModel { AccountViewModel(get()) }
    viewModel { ProfileViewModel(get(), get(), get()) }
}

private var started = false

fun initKoin(platformModule: Module) {
    if (started) return
    started = true
    startKoin { modules(platformModule, appModule) }
}
