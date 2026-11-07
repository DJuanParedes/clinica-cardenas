package pe.clinicacardenas.pacientes.ui.components

import androidx.compose.ui.graphics.Color
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import pe.clinicacardenas.pacientes.ui.theme.Brand
import kotlin.math.abs
import kotlin.math.roundToLong

/** Formatos en español peruano, sin depender de java.text (funciona igual en iOS). */
object Fmt {
    private val dias = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    private val meses = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
    private val mesesCortos = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    /** Acepta "2026-11-10", "2026-11-10T09:00" o con segundos; si no se entiende, devuelve una fecha neutra en vez de cerrar la app. */
    fun parse(iso: String): LocalDateTime {
        val t = iso.trim().take(19)
        val full = if (t.length == 10) t + "T00:00" else t
        return runCatching { LocalDateTime.parse(full) }.getOrElse { LocalDateTime(1970, 1, 1, 0, 0) }
    }

    fun hour(iso: String): String {
        val d = parse(iso)
        val h = d.hour % 12
        return "${if (h == 0) 12 else h}:${d.minute.toString().padStart(2, '0')} ${if (d.hour < 12) "a. m." else "p. m."}"
    }

    /** "Hoy", "Mañana" o "jueves 12 de noviembre". */
    fun dayLabel(iso: String): String {
        val date = parse(iso).date
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val diff = date.toEpochDays() - today.toEpochDays()
        return when (diff) {
            0 -> "Hoy"
            1 -> "Mañana"
            else -> longDate(date)
        }
    }

    fun longDate(date: LocalDate): String =
        "${dias[date.dayOfWeek.ordinal]} ${date.dayOfMonth} de ${meses[date.monthNumber - 1]}"

    fun shortDate(iso: String): String {
        val d = parse(iso).date
        return "${d.dayOfMonth} ${mesesCortos[d.monthNumber - 1]} ${d.year}"
    }

    fun dayNumber(iso: String) = parse(iso).dayOfMonth.toString()
    fun monthShort(iso: String) = mesesCortos[parse(iso).monthNumber - 1].uppercase()

    fun money(v: Double): String {
        val cents = (abs(v) * 100).roundToLong()
        val soles = (cents / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        return "${if (v < 0) "-" else ""}S/ $soles.${(cents % 100).toString().padStart(2, '0')}"
    }

    fun color(hex: String): Color = runCatching {
        Color(("FF" + hex.removePrefix("#")).toLong(16))
    }.getOrDefault(Brand.Celeste)

    val statusLabel = mapOf(
        "PROGRAMADA" to "Por confirmar",
        "CONFIRMADA" to "Confirmada",
        "ATENDIDA" to "Atendida",
        "CANCELADA" to "Cancelada",
        "NO_ASISTIO" to "No asistió",
        "REPROGRAMADA" to "Reprogramada",
        "EN_ESPERA" to "En sala",
        "EN_ATENCION" to "En atención",
    )

    fun statusColors(status: String): Pair<Color, Color> = when (status) {
        "CONFIRMADA", "ATENDIDA" -> Brand.VerdeSuave to Brand.Verde
        "PROGRAMADA" -> Brand.NaranjaSuave to Color(0xFF9A5B00)
        "CANCELADA", "NO_ASISTIO" -> Brand.RojoSuave to Brand.Rojo
        "EN_ESPERA" -> Color(0xFFEDE7FF) to Color(0xFF5B3CC4)
        else -> Brand.CelesteSuave to Brand.AzulOscuro
    }

    val channelLabel = mapOf("WHATSAPP" to "WhatsApp", "EMAIL" to "Correo electrónico", "AMBOS" to "WhatsApp y correo")
}
