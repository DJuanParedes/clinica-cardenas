package pe.clinicacardenas.pacientes.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pe.clinicacardenas.pacientes.ui.theme.Brand

/** Logo de la clínica dibujado como vector (el mismo de la web), compartido por Android e iOS. */
val ClinicLogo: ImageVector by lazy {
    fun nodes(d: String) = PathParser().parsePathString(d).toNodes()
    ImageVector.Builder(
        name = "LogoClinica",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 332f,
        viewportHeight = 332f,
    ).addGroup(translationX = -40f, translationY = -40f)
        .addPath(nodes("M334 102 A154 154 0 1 0 342 300 A149.5 149.5 0 1 1 334 102 Z"), fill = SolidColor(Brand.Naranja))
        .addPath(
            nodes("M121 196 C116 236 140 272 160 292 C168 301 180 306 186 296 C193 284 199 268 214 266 C229 266 236 284 243 296 C250 307 263 304 270 292 C292 258 312 222 312 172 C312 136 290 118 262 118 C238 118 222 132 210 154 C196 178 168 196 121 196 Z"),
            fill = SolidColor(Brand.Celeste),
        )
        .addPath(
            nodes("M116 168 C116 148 128 132 156 130 C182 128 206 128 224 128 C214 146 200 170 176 182 C156 192 134 194 124 190 C118 186 116 178 116 168 Z"),
            fill = SolidColor(Brand.Naranja),
        )
        .clearGroup()
        .build()
}

@Composable
fun Logo(size: Dp, modifier: Modifier = Modifier) {
    Image(imageVector = ClinicLogo, contentDescription = "Clínica Dental Cárdenas", modifier = modifier.size(size))
}
