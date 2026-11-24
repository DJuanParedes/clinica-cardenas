package pe.clinicacardenas.pacientes.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pe.clinicacardenas.pacientes.data.local.AppointmentLocalRepositoryImpl
import pe.clinicacardenas.pacientes.data.platform.ReminderSchedulerImpl
import pe.clinicacardenas.pacientes.data.remote.AppointmentRemoteRepositoryImpl
import pe.clinicacardenas.pacientes.data.remote.encode
import pe.clinicacardenas.pacientes.domain.repository.AppointmentLocalRepository
import pe.clinicacardenas.pacientes.domain.repository.AppointmentRemoteRepository
import pe.clinicacardenas.pacientes.domain.repository.Clock
import pe.clinicacardenas.pacientes.domain.repository.KeyGenerator
import pe.clinicacardenas.pacientes.domain.repository.ReminderScheduler
import pe.clinicacardenas.pacientes.domain.usecase.ConfirmAppointmentUseCase
import pe.clinicacardenas.pacientes.domain.usecase.ObserveAppointmentsUseCase
import pe.clinicacardenas.pacientes.domain.usecase.ObserveSyncStatusUseCase
import pe.clinicacardenas.pacientes.domain.usecase.RequestAppointmentUseCase
import pe.clinicacardenas.pacientes.domain.usecase.ResolveFailedOperationUseCase
import pe.clinicacardenas.pacientes.domain.usecase.ScheduleRemindersUseCase
import pe.clinicacardenas.pacientes.domain.usecase.SyncAppointmentsUseCase
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
/** Reloj y claves reales (en las pruebas se reemplazan por versiones controladas). */
object SystemClock : Clock {
    override fun now(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}

object RandomKeys : KeyGenerator {
    override fun newKey(): String = buildString {
        repeat(32) { append("0123456789abcdef"[kotlin.random.Random.nextInt(16)]) }
    }
}

val appModule = module {
    single { LocalStore(get()) }
    // Offline-first: dominio (casos de uso) ← datos (SQLDelight + REST). Las interfaces viven en el dominio.
    single { AppointmentLocalRepositoryImpl(get()) }
    single<AppointmentLocalRepository> { get<AppointmentLocalRepositoryImpl>() }
    single<AppointmentRemoteRepository> { AppointmentRemoteRepositoryImpl(get()) }
    single<ReminderScheduler> { ReminderSchedulerImpl(get()) }
    single<Clock> { SystemClock }
    single<KeyGenerator> { RandomKeys }
    single { SyncAppointmentsUseCase(get(), get(), get()) }
    factory { ObserveAppointmentsUseCase(get()) }
    factory { ObserveSyncStatusUseCase(get(), get()) }
    factory { ConfirmAppointmentUseCase(get(), get(), get(), get()) }
    factory { RequestAppointmentUseCase(get(), get(), get(), get()) { it.encode() } }
    factory { ResolveFailedOperationUseCase(get(), get()) }
    factory { ScheduleRemindersUseCase(get()) }
    single {
        val local = get<AppointmentLocalRepositoryImpl>()
        SessionRepository(get(), get(), get()) { local.resetLastSync() }
    }
    single { createHttpClient(get()) }
    single {
        val session = get<SessionRepository>()
        ApiClient(get(), server = { session.serverUrl }, token = { session.token })
    }
    single { ClinicRepository(get(), get(), get(), get()) }
    // App del personal
    single { StaffSession(get(), get()) }
    single {
        val session = get<SessionRepository>()
        val staff = get<StaffSession>()
        StaffApi(get(), server = { session.serverUrl }, token = { staff.token })
    }
    single { StaffRepository(get(), get()) }
    viewModel { LoginViewModel(get(), get(), get()) }
    viewModel { BookingViewModel(get(), get()) }
    viewModel { NoticesViewModel(get()) }
    viewModel { DocumentsViewModel(get(), get()) }
    viewModel { pe.clinicacardenas.pacientes.ui.history.HistoryViewModel(get()) }
    viewModel { pe.clinicacardenas.pacientes.ui.promos.PromosViewModel(get()) }
    viewModel { AgendaViewModel(get(), get()) }
    viewModel { PatientSearchViewModel(get()) }
    viewModel { PatientRecordViewModel(get(), get(), get()) }
    viewModel { NewAppointmentViewModel(get(), get()) }
    viewModel { CashViewModel(get()) }
    viewModel { DashboardViewModel(get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { AccountViewModel(get()) }
    viewModel { ProfileViewModel(get(), get(), get()) }
}

private var started = false

fun initKoin(platformModule: Module) {
    if (started) return
    started = true
    startKoin { modules(platformModule, appModule) }
}
