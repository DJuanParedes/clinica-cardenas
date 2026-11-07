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
import pe.clinicacardenas.pacientes.data.remote.StaffApi
import pe.clinicacardenas.pacientes.data.repository.StaffRepository
import pe.clinicacardenas.pacientes.data.repository.StaffSession
import pe.clinicacardenas.pacientes.ui.account.AccountViewModel
import pe.clinicacardenas.pacientes.ui.booking.BookingViewModel
import pe.clinicacardenas.pacientes.ui.documents.DocumentsViewModel
import pe.clinicacardenas.pacientes.ui.notices.NoticesViewModel
import pe.clinicacardenas.pacientes.ui.staff.AgendaViewModel
import pe.clinicacardenas.pacientes.ui.staff.CashViewModel
import pe.clinicacardenas.pacientes.ui.staff.DashboardViewModel
import pe.clinicacardenas.pacientes.ui.staff.NewAppointmentViewModel
import pe.clinicacardenas.pacientes.ui.staff.PatientRecordViewModel
import pe.clinicacardenas.pacientes.ui.staff.PatientSearchViewModel
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
    // App del personal
    single { StaffSession(get()) }
    single {
        val session = get<SessionRepository>()
        val staff = get<StaffSession>()
        StaffApi(get(), server = { session.serverUrl }, token = { staff.token })
    }
    single { StaffRepository(get(), get()) }
    viewModel { LoginViewModel(get(), get(), get()) }
    viewModel { BookingViewModel(get()) }
    viewModel { NoticesViewModel(get()) }
    viewModel { DocumentsViewModel(get(), get()) }
    viewModel { AgendaViewModel(get(), get()) }
    viewModel { PatientSearchViewModel(get()) }
    viewModel { PatientRecordViewModel(get(), get(), get()) }
    viewModel { NewAppointmentViewModel(get(), get()) }
    viewModel { CashViewModel(get()) }
    viewModel { DashboardViewModel(get()) }
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
