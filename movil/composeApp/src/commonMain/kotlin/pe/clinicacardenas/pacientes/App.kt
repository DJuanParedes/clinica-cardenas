package pe.clinicacardenas.pacientes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import pe.clinicacardenas.pacientes.data.remote.PatientDto
import pe.clinicacardenas.pacientes.data.repository.SessionRepository
import pe.clinicacardenas.pacientes.data.repository.StaffSession
import pe.clinicacardenas.pacientes.platform.AppLifecycle
import pe.clinicacardenas.pacientes.ui.account.AccountScreen
import pe.clinicacardenas.pacientes.ui.documents.DocumentsScreen
import pe.clinicacardenas.pacientes.ui.home.HomeScreen
import pe.clinicacardenas.pacientes.ui.login.LoginScreen
import pe.clinicacardenas.pacientes.ui.notices.NoticesScreen
import pe.clinicacardenas.pacientes.ui.notices.NoticesViewModel
import pe.clinicacardenas.pacientes.ui.profile.ProfileScreen
import pe.clinicacardenas.pacientes.ui.staff.StaffApp
import pe.clinicacardenas.pacientes.ui.theme.Brand
import pe.clinicacardenas.pacientes.ui.theme.ClinicaTheme

private enum class Tab(val label: String, val icon: String) {
    CITAS("Citas", "🗓️"),
    AVISOS("Avisos", "🔔"),
    PAGOS("Pagos", "💳"),
    DOCUMENTOS("Documentos", "📄"),
    PERFIL("Perfil", "👤"),
}

/**
 * Punto de entrada compartido por Android e iOS. Sin sesión muestra el ingreso (paciente o personal);
 * con sesión de paciente, sus pestañas; con sesión del personal, la app de la clínica según su rol.
 */
@Composable
fun App() {
    ClinicaTheme {
        val session = koinInject<SessionRepository>()
        val staff = koinInject<StaffSession>()
        val patient by session.patient.collectAsState()
        val user by staff.user.collectAsState()
        val p = patient
        val u = user
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                // Cualquier toque cuenta como actividad (cierre de sesión por inactividad del personal).
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        AppLifecycle.touched()
                    }
                }
            },
        ) {
            // Cada sesión tiene sus propios ViewModels: al cerrar sesión se descartan y no queda información anterior.
            when {
                u != null -> ScopedViewModels(key = "personal-${u.id}") { StaffApp(u) }
                p != null -> ScopedViewModels(key = "paciente-${p.id}") { MainTabs(p) }
                else -> ScopedViewModels(key = "ingreso") { LoginScreen() }
            }
        }
    }
}

@Composable
private fun MainTabs(current: PatientDto) {
    var tab by rememberSaveable { mutableStateOf(Tab.CITAS) }
    val notices: NoticesViewModel = koinViewModel()
    val ns by notices.state.collectAsState()
    Scaffold(
        containerColor = Brand.Fondo,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            if (t == Tab.AVISOS && ns.unread > 0) {
                                BadgedBox(badge = { Badge(containerColor = Brand.Naranja) { Text(ns.unread.toString()) } }) { Text(t.icon, fontSize = 20.sp) }
                            } else {
                                Text(t.icon, fontSize = 20.sp)
                            }
                        },
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
            when (tab) {
                Tab.CITAS -> HomeScreen(current)
                Tab.AVISOS -> NoticesScreen(notices)
                Tab.PAGOS -> AccountScreen()
                Tab.DOCUMENTOS -> DocumentsScreen()
                Tab.PERFIL -> ProfileScreen()
            }
        }
    }
}

@Composable
private fun ScopedViewModels(key: String, content: @Composable () -> Unit) {
    val owner = remember(key) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
