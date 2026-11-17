package pe.clinicacardenas.pacientes.ui.staff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import pe.clinicacardenas.pacientes.data.remote.ApiJson
import pe.clinicacardenas.pacientes.data.remote.StaffUserDto
import pe.clinicacardenas.pacientes.data.repository.SessionRepository
import pe.clinicacardenas.pacientes.data.repository.StaffSession
import pe.clinicacardenas.pacientes.platform.ClinicWebView
import pe.clinicacardenas.pacientes.ui.components.BackHeader
import pe.clinicacardenas.pacientes.ui.components.Panel
import pe.clinicacardenas.pacientes.ui.theme.Brand

/** Módulo del sistema web (mismas rutas y permisos que el menú de la web). */
data class WebModule(val group: String, val icon: String, val label: String, val detail: String, val path: String, val perms: List<String>)

/** Todo lo que el personal puede hacer en la web, según los permisos de su rol. */
val WEB_MODULES = listOf(
    WebModule("General", "🏠", "Inicio", "Resumen del día y alertas", "/inicio", emptyList()),
    WebModule("General", "🗓️", "Agenda completa", "Todas las vistas y acciones de la agenda", "/agenda", listOf("AGENDA_VER")),
    WebModule("General", "➕", "Nueva cita", "Agendar una cita", "/citas/nueva", listOf("CITAS_GESTIONAR")),
    WebModule("General", "💬", "Seguimiento", "Confirmaciones, inasistencias y lista de espera", "/seguimiento", listOf("AGENDA_VER")),
    WebModule("Pacientes", "🦷", "Pacientes", "Buscar y abrir fichas completas", "/pacientes", listOf("PACIENTES_VER")),
    WebModule("Pacientes", "📝", "Registrar paciente", "Alta de un paciente nuevo", "/pacientes/nuevo", listOf("PACIENTES_EDITAR")),
    WebModule("Pacientes", "📦", "Inventario", "Materiales, stock y movimientos", "/inventario", listOf("INVENTARIO")),
    WebModule("Caja", "💵", "Caja del día", "Cobros, egresos y cierre", "/caja", listOf("CAJA_COBRAR", "CAJA_EGRESOS", "CAJA_REPORTE")),
    WebModule("Caja", "🧾", "Registrar cobro", "Cobrar un tratamiento o cuota", "/caja/cobro", listOf("CAJA_COBRAR")),
    WebModule("Caja", "📄", "Comprobantes", "Boletas y facturas electrónicas", "/comprobantes", listOf("COMPROBANTES")),
    WebModule("Caja", "🆕", "Nuevo comprobante", "Emitir boleta o factura", "/comprobantes/nuevo", listOf("COMPROBANTES")),
    WebModule("Caja", "💰", "Flujo de caja", "Ingresos y egresos por período", "/caja/reporte", listOf("CAJA_REPORTE")),
    WebModule("Gestión", "📊", "Indicadores", "Reportes de la clínica", "/reportes", listOf("REPORTES")),
    WebModule("Gestión", "％", "Comisiones", "Liquidación de odontólogos", "/comisiones", listOf("COMISIONES")),
    WebModule("Gestión", "🔔", "Notificaciones", "WhatsApp, correo y recordatorios", "/notificaciones", listOf("NOTIFICACIONES")),
    WebModule("Seguridad", "🛡️", "Usuarios y accesos", "Crear y administrar cuentas", "/usuarios", listOf("USUARIOS")),
    WebModule("Seguridad", "🔑", "Roles y permisos", "Qué puede hacer cada rol", "/roles", listOf("ROLES")),
    WebModule("Seguridad", "🔐", "Mi seguridad (2 pasos)", "Activar la verificación en 2 pasos", "/seguridad", emptyList()),
)

fun StaffUserDto.webModules(): List<WebModule> =
    WEB_MODULES.filter { m -> m.perms.isEmpty() || m.perms.any { it in permissions } }

/** Pestaña "Más": todas las funciones de la web disponibles para el rol. */
@Composable
fun MoreScreen(user: StaffUserDto, open: (StaffNav) -> Unit) {
    val modules = user.webModules()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Todas las funciones", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta)
            Text("Lo mismo que haces en la web, con tu sesión ya iniciada.", color = Brand.Tinta2, fontSize = 14.sp)
        }
        modules.groupBy { it.group }.forEach { (group, mods) ->
            item { Text(group.uppercase(), color = Brand.Gris, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
            items(mods, key = { it.path }) { m ->
                Panel(Modifier.clickable { open(StaffNav.Web(m.label, m.path)) }, padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.icon, fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.label, fontWeight = FontWeight.SemiBold, color = Brand.Tinta)
                            Text(m.detail, color = Brand.Gris, fontSize = 13.sp)
                        }
                        Text("›", fontSize = 22.sp, color = Brand.Gris)
                    }
                }
            }
        }
    }
}

/** Abre un módulo de la web dentro de la app, en el servidor fijo de la clínica. */
@Composable
fun WebModuleScreen(title: String, path: String, onBack: () -> Unit) {
    val staff = koinInject<StaffSession>()
    val server = koinInject<SessionRepository>().serverUrl.trimEnd('/')
    val user = staff.user.value
    val token = staff.token
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)) { BackHeader(title, onBack) }
        if (user == null || token == null) {
            Text("Tu sesión terminó. Vuelve a ingresar.", color = Brand.Rojo, modifier = Modifier.padding(16.dp))
        } else {
            ClinicWebView(
                url = server + path,
                token = token,
                userJson = ApiJson.encodeToString(StaffUserDto.serializer(), user),
                onExit = onBack,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
