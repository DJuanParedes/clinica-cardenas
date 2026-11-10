package pe.clinicacardenas.pacientes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.clinicacardenas.pacientes.ui.theme.Brand

@Composable
fun Panel(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, Brand.Linea, RoundedCornerShape(18.dp))
            .padding(padding),
        content = content,
    )
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Brand.Gris) } },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false, enabled: Boolean = true, color: Color = Brand.Azul) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(46.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) { Text(text, fontWeight = FontWeight.Medium, color = Brand.AzulOscuro) }
}

@Composable
fun Chip(text: String, bg: Color, fg: Color) {
    Box(Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Banner(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().background(bg, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(text, color = fg, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Brand.Tinta,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Brand.Celeste)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Panel {
        Text(message, color = Brand.Tinta2)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Reintentar", onRetry)
        }
    }
}

/** Encabezado de pantalla con botón para volver. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        androidx.compose.material3.TextButton(onClick = onBack) { Text("‹ Volver", color = Brand.Azul, fontWeight = FontWeight.SemiBold) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Brand.Tinta)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = Brand.Tinta2)
        }
    }
}

/** Opción seleccionable en forma de píldora (motivos, métodos de pago, fechas). */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .background(if (selected) Brand.Azul else Color.White, RoundedCornerShape(50))
            .border(1.dp, if (selected) Brand.Azul else Brand.Linea, RoundedCornerShape(50))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = when {
                !enabled -> Brand.Gris.copy(alpha = 0.5f)
                selected -> Color.White
                else -> Brand.Tinta2
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}


/** Cifra grande con su etiqueta (resúmenes de caja y dirección). */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, color: Color = Brand.Tinta) {
    Column(modifier.background(Color.White, RoundedCornerShape(16.dp)).border(1.dp, Brand.Linea, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 13.sp, color = Brand.Tinta2)
    }
}
