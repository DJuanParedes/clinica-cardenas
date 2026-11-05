package pe.clinicacardenas.pacientes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colores de la marca Clínica Dental Cárdenas: celeste del diente y naranja de la corona. */
object Brand {
    val Celeste = Color(0xFF00B0E0)
    val Naranja = Color(0xFFF89818)
    val Azul = Color(0xFF0679AC)
    val AzulOscuro = Color(0xFF055F88)
    val Tinta = Color(0xFF0E2A3B)
    val Tinta2 = Color(0xFF35576A)
    val Gris = Color(0xFF688494)
    val Fondo = Color(0xFFF3F8FB)
    val Linea = Color(0xFFDBE7EE)
    val CelesteSuave = Color(0xFFE2F6FC)
    val NaranjaSuave = Color(0xFFFFF1DD)
    val Verde = Color(0xFF217A4A)
    val VerdeSuave = Color(0xFFE1F4E9)
    val Rojo = Color(0xFFC2410C)
    val RojoSuave = Color(0xFFFDECE4)
}

private val colors = lightColorScheme(
    primary = Brand.Azul,
    onPrimary = Color.White,
    primaryContainer = Brand.CelesteSuave,
    onPrimaryContainer = Brand.AzulOscuro,
    secondary = Brand.Naranja,
    onSecondary = Color.White,
    secondaryContainer = Brand.NaranjaSuave,
    background = Brand.Fondo,
    onBackground = Brand.Tinta,
    surface = Color.White,
    onSurface = Brand.Tinta,
    onSurfaceVariant = Brand.Tinta2,
    outline = Brand.Linea,
    error = Brand.Rojo,
)

@Composable
fun ClinicaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
