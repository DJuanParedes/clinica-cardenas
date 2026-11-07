package pe.clinicacardenas.pacientes.ui.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import pe.clinicacardenas.pacientes.data.remote.StaffUserDto
import pe.clinicacardenas.pacientes.data.repository.StaffRepository
import pe.clinicacardenas.pacientes.data.repository.StaffSession
import pe.clinicacardenas.pacientes.platform.AppLifecycle
import pe.clinicacardenas.pacientes.platform.PlatformActions
import pe.clinicacardenas.pacientes.ui.components.Logo
import pe.clinicacardenas.pacientes.ui.components.PrimaryButton
import pe.clinicacardenas.pacientes.ui.theme.Brand

/** Tiempo sin tocar la pantalla tras el cual se cierra la sesión del personal (datos de salud). */
private const val INACTIVITY_MS = 15 * 60_000L

/** Si la app estuvo en segundo plano más de esto, se pide la huella al volver. */
private const val LOCK_AFTER_MS = 30_000L

private enum class STab(val label: String, val icon: String, val perms: List<String>) {
    RESUMEN("Resumen", "📊", listOf("REPORTES", "CAJA_REPORTE")),
    AGENDA("Agenda", "🗓️", listOf("AGENDA_VER")),
    PACIENTES("Pacientes", "🦷", listOf("PACIENTES_VER")),
    CAJA("Caja", "💵", listOf("CAJA_COBRAR")),
    CUENTA("Cuenta", "👤", emptyList()),
}

/** Navegación interna de la app del personal. */
sealed interface StaffNav {
    data class Patient(val id: Long, val section: String = "resumen") : StaffNav
    data object NewAppointment : StaffNav
}

@Composable
fun StaffApp(user: StaffUserDto) {
    val session = koinInject<StaffSession>()
    val repo = koinInject<StaffRepository>()
    val platform = koinInject<PlatformActions>()

    val tabs = STab.entries.filter { t -> t.perms.isEmpty() || t.perms.any { it in user.permissions } }
    val start = when {
        user.role == "DIRECTOR" -> STab.RESUMEN
        user.role == "CAJA" -> STab.CAJA
        STab.AGENDA in tabs -> STab.AGENDA
        else -> tabs.first()
    }
    var tab by rememberSaveable { mutableStateOf(start) }
    var nav by remember { mutableStateOf<StaffNav?>(null) }
    var locked by remember { mutableStateOf(session.biometric && platform.canUseBiometrics) }

    // Permisos al día y bloqueo al volver del segundo plano.
    LaunchedEffect(Unit) { repo.refreshUser() }
    val foreground by AppLifecycle.foreground.collectAsState()
    LaunchedEffect(foreground) {
        if (!foreground) return@LaunchedEffect
        val since = AppLifecycle.consumeBackground()
        if (since == 0L) return@LaunchedEffect
        val away = AppLifecycle.now() - since
        if (away > INACTIVITY_MS) repo.logout()
        else if (away > LOCK_AFTER_MS && session.biometric && platform.canUseBiometrics) locked = true
    }
    // Cierre de sesión por inactividad.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            if (AppLifecycle.foreground.value && AppLifecycle.now() - AppLifecycle.lastTouch > INACTIVITY_MS) repo.logout()
        }
    }

    if (locked) {
        LockScreen(user, onUnlock = { platform.authenticate("Desbloquea la app de la clínica") { ok -> if (ok) locked = false } }, onLogout = repo::logout)
        return
    }

    val open: (StaffNav) -> Unit = { nav = it }
    Scaffold(
        containerColor = Brand.Fondo,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t && nav == null,
                        onClick = { tab = t; nav = null },
                        icon = { Text(t.icon, fontSize = 20.sp) },
                        label = { Text(t.label, fontSize = 11.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = Brand.Azul,
                            indicatorColor = Brand.CelesteSuave,
                            unselectedTextColor = Brand.Gris,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val n = nav) {
                is StaffNav.Patient -> PatientRecordScreen(n.id, n.section, onBack = { nav = null })
                StaffNav.NewAppointment -> NewAppointmentScreen(onClose = { nav = null })
                null -> when (tab) {
                    STab.RESUMEN -> DashboardScreen(user, open)
                    STab.AGENDA -> AgendaScreen(user, open)
                    STab.PACIENTES -> PatientsScreen(open)
                    STab.CAJA -> CashScreen(user, open)
                    STab.CUENTA -> StaffAccountScreen(user)
                }
            }
        }
    }
}

@Composable
private fun LockScreen(user: StaffUserDto, onUnlock: () -> Unit, onLogout: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Brand.CelesteSuave, Brand.Fondo))).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.widthIn(max = 420.dp).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Logo(76.dp)
            Text("App bloqueada", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta)
            Text("${user.fullName} · ${user.roleLabel}", color = Brand.Tinta2, textAlign = TextAlign.Center)
            Text("Usa tu huella, rostro o el PIN del teléfono para continuar.", color = Brand.Gris, textAlign = TextAlign.Center, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            PrimaryButton("🔓 Desbloquear", onUnlock)
            TextButton(onClick = onLogout) { Text("Cerrar sesión", color = Brand.Rojo) }
        }
    }
}
