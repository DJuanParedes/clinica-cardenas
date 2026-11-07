package pe.clinicacardenas.pacientes.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import pe.clinicacardenas.pacientes.ui.components.Banner
import pe.clinicacardenas.pacientes.ui.components.Field
import pe.clinicacardenas.pacientes.ui.components.Logo
import pe.clinicacardenas.pacientes.ui.components.Panel
import pe.clinicacardenas.pacientes.ui.components.PrimaryButton
import pe.clinicacardenas.pacientes.ui.theme.Brand

@Composable
fun LoginScreen(vm: LoginViewModel = koinViewModel()) {
    val s by vm.state.collectAsState()
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Brand.CelesteSuave, Brand.Fondo)))
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Logo(84.dp)
            Spacer(Modifier.height(12.dp))
            Text("Clínica Dental Cárdenas", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta)
            Text("Tus citas, recordatorios y pagos en un solo lugar", color = Brand.Tinta2, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))

            Tabs(s.mode, vm::mode)
            Spacer(Modifier.height(12.dp))

            Panel(padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
                when (s.mode) {
                    LoginMode.INGRESAR -> LoginForm(s, vm)
                    LoginMode.ACTIVAR -> ActivateForm(s, vm)
                    LoginMode.SERVIDOR -> ServerForm(s, vm)
                }
            }

            Spacer(Modifier.height(10.dp))
            if (s.mode != LoginMode.SERVIDOR) {
                TextButton(onClick = { vm.mode(LoginMode.SERVIDOR) }) {
                    Text("Servidor: ${s.server}", color = Brand.Gris, fontSize = 12.sp)
                }
            }
            Text(
                "RUC 10102984841 · Lima, Perú",
                color = Brand.Gris,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun Tabs(mode: LoginMode, onSelect: (LoginMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(LoginMode.INGRESAR to "Ingresar", LoginMode.ACTIVAR to "Primera vez", LoginMode.SERVIDOR to "Servidor").forEach { (m, label) ->
            val on = m == mode
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) Brand.Azul else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelect(m) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) Color.White else Brand.Tinta2, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun DocFields(s: LoginState, vm: LoginViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("DNI", "CE", "PAS").forEach { t ->
            val on = s.docType == t
            Box(
                Modifier
                    .background(if (on) Brand.CelesteSuave else Brand.Fondo, RoundedCornerShape(50))
                    .clickable { vm.edit { copy(docType = t) } }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(if (t == "PAS") "Pasaporte" else t, color = if (on) Brand.AzulOscuro else Brand.Gris, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Field(
        value = s.docNumber,
        onChange = { v -> vm.edit { copy(docNumber = v.filter { it.isLetterOrDigit() }.take(12)) } },
        label = "Número de documento",
        keyboard = if (s.docType == "DNI") KeyboardType.Number else KeyboardType.Text,
        error = s.fieldErrors["docNumber"],
    )
}

@Composable
private fun LoginForm(s: LoginState, vm: LoginViewModel) {
    Text("Ingresa a tu cuenta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    DocFields(s, vm)
    Field(s.password, { vm.edit { copy(password = it) } }, "Contraseña", password = true, error = s.fieldErrors["password"])
    s.error?.let { Banner(it, Brand.RojoSuave, Brand.Rojo, Modifier.padding(vertical = 8.dp)) }
    Spacer(Modifier.height(8.dp))
    PrimaryButton("Ingresar", vm::login, loading = s.loading)
    Spacer(Modifier.height(10.dp))
    Text(
        "¿Aún no tienes contraseña? Toca “Primera vez” y te enviamos un código.",
        color = Brand.Gris,
        fontSize = 13.sp,
    )
}

@Composable
private fun ActivateForm(s: LoginState, vm: LoginViewModel) {
    Text("Activa tu cuenta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("Usa el documento y la fecha de nacimiento que registraste en la clínica.", color = Brand.Tinta2, fontSize = 14.sp)
    Spacer(Modifier.height(12.dp))
    DocFields(s, vm)
    if (!s.codeSent) {
        Field(
            value = s.birthDate,
            onChange = { v -> vm.edit { copy(birthDate = formatBirth(v)) } },
            label = "Fecha de nacimiento",
            placeholder = "dd/mm/aaaa",
            keyboard = KeyboardType.Number,
            error = s.fieldErrors["birthDate"],
        )
        s.error?.let { Banner(it, Brand.RojoSuave, Brand.Rojo, Modifier.padding(vertical = 8.dp)) }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Enviarme el código", vm::requestCode, loading = s.loading)
    } else {
        s.codeInfo?.let { Banner(it, Brand.VerdeSuave, Brand.Verde, Modifier.padding(bottom = 8.dp)) }
        Field(
            value = s.code,
            onChange = { v -> vm.edit { copy(code = v.filter(Char::isDigit).take(6)) } },
            label = "Código de 6 dígitos",
            keyboard = KeyboardType.Number,
            error = s.fieldErrors["code"],
        )
        Field(
            value = s.newPassword,
            onChange = { vm.edit { copy(newPassword = it) } },
            label = "Crea tu contraseña",
            password = true,
            error = s.fieldErrors["newPassword"] ?: s.fieldErrors["password"],
        )
        s.error?.let { Banner(it, Brand.RojoSuave, Brand.Rojo, Modifier.padding(vertical = 8.dp)) }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Activar y entrar", vm::activate, loading = s.loading)
        TextButton(onClick = { vm.edit { copy(codeSent = false, code = "") } }) { Text("Pedir otro código") }
    }
}

@Composable
private fun ServerForm(s: LoginState, vm: LoginViewModel) {
    Text("Dirección del servidor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        "En el emulador de Android usa 10.0.2.2:9081. En un teléfono real, la IP de la computadora donde corre el sistema (por ejemplo 192.168.1.20:9081), conectada al mismo Wi-Fi.",
        color = Brand.Tinta2,
        fontSize = 14.sp,
    )
    Spacer(Modifier.height(12.dp))
    Field(
        value = s.server,
        onChange = { vm.edit { copy(server = it.trim(), serverOk = null, serverInfo = null) } },
        label = "Servidor",
        keyboard = KeyboardType.Uri,
        error = s.fieldErrors["server"],
    )
    s.serverInfo?.let { Banner(it, Brand.VerdeSuave, Brand.Verde, Modifier.padding(vertical = 8.dp)) }
    s.error?.let { Banner(it, Brand.RojoSuave, Brand.Rojo, Modifier.padding(vertical = 8.dp)) }
    Spacer(Modifier.height(8.dp))
    PrimaryButton("Probar y guardar", vm::testServer, loading = s.loading)
    if (s.serverOk == true) {
        TextButton(onClick = { vm.mode(LoginMode.INGRESAR) }) { Text("Ir a ingresar") }
    }
}

/** Inserta las barras mientras se escribe: 15031990 → 15/03/1990. */
private fun formatBirth(raw: String): String {
    val d = raw.filter(Char::isDigit).take(8)
    return buildString {
        d.forEachIndexed { i, c ->
            if (i == 2 || i == 4) append('/')
            append(c)
        }
    }
}

