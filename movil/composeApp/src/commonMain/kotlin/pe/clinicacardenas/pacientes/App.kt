package pe.clinicacardenas.pacientes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import org.koin.compose.koinInject
import pe.clinicacardenas.pacientes.data.remote.PatientDto
import pe.clinicacardenas.pacientes.data.repository.SessionRepository
import pe.clinicacardenas.pacientes.ui.account.AccountScreen
import pe.clinicacardenas.pacientes.ui.home.HomeScreen
import pe.clinicacardenas.pacientes.ui.login.LoginScreen
import pe.clinicacardenas.pacientes.ui.profile.ProfileScreen
import pe.clinicacardenas.pacientes.ui.theme.Brand
import pe.clinicacardenas.pacientes.ui.theme.ClinicaTheme

private enum class Tab(val label: String, val icon: String) {
    CITAS("Citas", "🗓️"),
    PAGOS("Pagos", "💳"),
    PERFIL("Perfil", "👤"),
}

/** Punto de entrada compartido por Android e iOS. Si no hay sesión muestra el ingreso; si hay, las pestañas. */
@Composable
fun App() {
    ClinicaTheme {
        val session = koinInject<SessionRepository>()
        val patient by session.patient.collectAsState()
        val current = patient
        // Cada sesión tiene sus propios ViewModels: al cerrar sesión se descartan y no queda información del paciente anterior.
        ScopedViewModels(key = current?.id ?: -1L) {
            if (current == null) LoginScreen() else MainTabs(current)
        }
    }
}

@Composable
private fun MainTabs(current: PatientDto) {
    var tab by rememberSaveable { mutableStateOf(Tab.CITAS) }
    Scaffold(
        containerColor = Brand.Fondo,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(t.icon, fontSize = 20.sp) },
                        label = { Text(t.label) },
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
                Tab.PAGOS -> AccountScreen()
                Tab.PERFIL -> ProfileScreen()
            }
        }
    }
}

@Composable
private fun ScopedViewModels(key: Long, content: @Composable () -> Unit) {
    val owner = remember(key) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

