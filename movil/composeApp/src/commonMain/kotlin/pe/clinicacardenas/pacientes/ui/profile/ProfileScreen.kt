package pe.clinicacardenas.pacientes.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import pe.clinicacardenas.pacientes.ui.components.Banner
import pe.clinicacardenas.pacientes.ui.components.Fmt
import pe.clinicacardenas.pacientes.ui.components.Logo
import pe.clinicacardenas.pacientes.ui.components.Panel
import pe.clinicacardenas.pacientes.ui.components.PrimaryButton
import pe.clinicacardenas.pacientes.ui.components.SecondaryButton
import pe.clinicacardenas.pacientes.ui.components.SectionTitle
import pe.clinicacardenas.pacientes.ui.home.whatsapp
import pe.clinicacardenas.pacientes.ui.theme.Brand

@Composable
fun ProfileScreen(vm: ProfileViewModel = koinViewModel()) {
    val p by vm.patient.collectAsState()
    val s by vm.state.collectAsState()
    val patient = p ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Mi perfil", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta)
        Panel {
            Text(patient.fullName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta)
            Text("${if (patient.docType == "PAS") "Pasaporte" else patient.docType} ${patient.docNumber}", color = Brand.Tinta2)
            patient.phone?.let { Text("Celular: $it", color = Brand.Tinta2) }
            patient.email?.let { Text("Correo: $it", color = Brand.Tinta2) }
            Text("¿Algún dato cambió? Avísanos en recepción para actualizarlo.", color = Brand.Gris, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }

        SectionTitle("¿Por dónde te avisamos?")
        Text("Te enviamos un recordatorio 24 horas antes de cada cita y te avisamos si hay cambios.", color = Brand.Tinta2, fontSize = 14.sp)
        Panel(padding = PaddingValues(6.dp)) {
            listOf("WHATSAPP", "EMAIL", "AMBOS").forEach { c ->
                ChannelOption(
                    label = Fmt.channelLabel[c] ?: c,
                    selected = patient.notifyChannel == c,
                    saving = s.saving == c,
                    onClick = { vm.setChannel(c) },
                )
            }
        }
        s.message?.let { Banner(it, Brand.CelesteSuave, Brand.AzulOscuro) }

        SectionTitle("Clínica")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(patient.clinic.name, fontWeight = FontWeight.Bold, color = Brand.Tinta)
                    Text(patient.clinic.address, color = Brand.Tinta2, fontSize = 13.sp)
                    Text("Tel. ${patient.clinic.phone}", color = Brand.Tinta2, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("📞 Llamar", { vm.platform.dial(patient.clinic.phone) }, Modifier.weight(1f))
                SecondaryButton("💬 WhatsApp", { vm.platform.openUrl(whatsapp(patient.clinic.phone, "Hola, soy ${patient.fullName}.")) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                "📍 Cómo llegar",
                { vm.platform.openUrl("https://www.google.com/maps/search/?api=1&query=" + patient.clinic.address.replace(" ", "+")) },
                Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(4.dp))
        PrimaryButton("Cerrar sesión", vm::logout, loading = s.loggingOut, color = Brand.Rojo)
        Text(
            "App ${vm.platform.name} · v1.0.0 · Servidor ${vm.serverUrl}",
            color = Brand.Gris,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun ChannelOption(label: String, selected: Boolean, saving: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Brand.CelesteSuave else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(if (selected) Brand.Azul else Brand.Linea, RoundedCornerShape(50))
                .padding(5.dp)
                .background(if (selected) Color.White else Brand.Linea, RoundedCornerShape(50)),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = Brand.Tinta)
        if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Brand.Celeste)
    }
}
