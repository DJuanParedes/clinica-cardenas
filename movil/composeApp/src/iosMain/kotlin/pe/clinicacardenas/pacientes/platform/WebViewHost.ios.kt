package pe.clinicacardenas.pacientes.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import pe.clinicacardenas.pacientes.ui.components.PrimaryButton
import pe.clinicacardenas.pacientes.ui.theme.Brand

/** En iOS el módulo se abre en el navegador (Safari), con el mismo usuario de la web. */
@Composable
actual fun ClinicWebView(url: String, token: String, userJson: String, onExit: () -> Unit, modifier: Modifier) {
    val platform = koinInject<PlatformActions>()
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Este módulo se abre en el navegador. Ingresa con tu usuario del personal.", color = Brand.Tinta2)
        PrimaryButton("Abrir en el navegador", { platform.openUrl(url) })
    }
}
