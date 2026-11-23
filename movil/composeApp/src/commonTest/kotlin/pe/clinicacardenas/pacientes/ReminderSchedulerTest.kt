package pe.clinicacardenas.pacientes

import pe.clinicacardenas.pacientes.data.platform.ReminderSchedulerImpl
import pe.clinicacardenas.pacientes.domain.repository.PermissionStatus
import pe.clinicacardenas.pacientes.domain.usecase.ScheduleRemindersUseCase
import pe.clinicacardenas.pacientes.platform.PickSource
import pe.clinicacardenas.pacientes.platform.PickedFile
import pe.clinicacardenas.pacientes.platform.PlatformActions
import pe.clinicacardenas.pacientes.platform.Reminder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Acciones nativas simuladas: registran lo que la app pidió al sistema. */
class FakePlatform(var permission: String = "CONCEDIDO") : PlatformActions {
    val scheduled = mutableListOf<Reminder>()
    var settingsOpened = false
    override val name = "Prueba"
    override val defaultServerUrl = "https://clinica.test"
    override fun openUrl(url: String) {}
    override fun dial(phone: String) {}
    override fun addToCalendar(title: String, startEpochMillis: Long, endEpochMillis: Long, location: String, details: String) {}
    override fun scheduleReminders(reminders: List<Reminder>): Int { scheduled.clear(); scheduled += reminders; return reminders.size }
    override fun notify(id: Int, title: String, body: String) {}
    override fun pickFile(source: PickSource, onResult: (PickedFile?) -> Unit) = onResult(null)
    override fun openFile(name: String, mimeType: String, bytes: ByteArray) {}
    override val canUseBiometrics = false
    override fun authenticate(reason: String, onResult: (Boolean) -> Unit) = onResult(false)
    override fun notificationPermission() = permission
    override fun requestNotificationPermission(onResult: (String) -> Unit) { permission = "CONCEDIDO"; onResult(permission) }
    override fun openAppSettings() { settingsOpened = true }
}

/** Capacidad nativa de recordatorios detrás de la interfaz del dominio, con el permiso en sus tres estados. */
class ReminderSchedulerTest {

    @Test
    fun programaDosAvisosPorCitaFuturaYOmiteLasProvisionales() {
        val platform = FakePlatform()
        val useCase = ScheduleRemindersUseCase(ReminderSchedulerImpl(platform))
        val n = useCase(listOf(appointment(1, "2099-01-10T10:00"), appointment(-5, "2099-01-11T10:00"), appointment(2, "2099-01-12T10:00", status = "CANCELADA")), "Clínica")
        assertEquals(2, n)
        assertTrue(platform.scheduled.all { it.title.contains("10:00") })
    }

    @Test
    fun conElPermisoDenegadoNoSeProgramaNada() {
        val platform = FakePlatform(permission = "DENEGADO_PARA_SIEMPRE")
        val scheduler = ReminderSchedulerImpl(platform)
        assertEquals(PermissionStatus.DENEGADO_PARA_SIEMPRE, scheduler.permission())
        assertEquals(0, ScheduleRemindersUseCase(scheduler)(listOf(appointment(1, "2099-01-10T10:00")), "Clínica"))
        scheduler.openSettings()
        assertTrue(platform.settingsOpened)
    }

    @Test
    fun pedirElPermisoActualizaElEstado() {
        val platform = FakePlatform(permission = "DENEGADO")
        val scheduler = ReminderSchedulerImpl(platform)
        var result: PermissionStatus? = null
        scheduler.requestPermission { result = it }
        assertEquals(PermissionStatus.CONCEDIDO, result)
    }
}
