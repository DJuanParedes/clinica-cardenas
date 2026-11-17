package pe.clinicacardenas.pacientes.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Muestra un módulo del sistema web dentro de la app, con la sesión del personal ya iniciada.
 *
 * @param url dirección completa del módulo (servidor fijo + ruta).
 * @param token token JWT del personal (el mismo que usa la web).
 * @param userJson usuario de la sesión en JSON (la web lo guarda junto al token).
 * @param onExit se llama cuando el usuario pulsa "atrás" y ya no hay páginas anteriores.
 */
@Composable
expect fun ClinicWebView(url: String, token: String, userJson: String, onExit: () -> Unit, modifier: Modifier)
