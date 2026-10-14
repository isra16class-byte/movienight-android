package com.isra16.movienight.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isra16.movienight.ui.theme.AuthTitleStyle
import com.isra16.movienight.ui.theme.LocalGlassColors

/**
 * Estructura común de las pantallas de auth: centrada, con scroll y respetando teclado y barras. No hay panel: el
 * título, el subtítulo y el formulario van directo sobre la hoja de vidrio de pantalla completa que pone
 * [AuthBackdrop] por encima del NavHost, fuera de este padding (Fase 8, paso 3). Los campos van juntos en una barra
 * ([GlassFieldGroup]).
 */
@Composable
fun AuthScreenLayout(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassContent {
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = AuthTitleStyle.copy(shadow = GlassTextShadow), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                content()
            }
        }
    }
}

/**
 * Barra que junta los campos de las pantallas de entrada: un solo contenedor redondeado con tinte y borde fino
 * (`GlassColors.barTint` y `barBorder`), y los campos adentro sin contorno propio, separados con [GlassFieldDivider].
 */
@Composable
fun GlassFieldGroup(content: @Composable ColumnScope.() -> Unit) {
    val glass = LocalGlassColors.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(glass.barTint)
            .border(1.dp, glass.barBorder, shape),
        content = content,
    )
}

/** Línea fina entre dos campos de un [GlassFieldGroup]. */
@Composable
fun GlassFieldDivider() {
    HorizontalDivider(color = LocalGlassColors.current.barBorder.copy(alpha = 0.5f))
}

/** Campo sin fondo ni línea inferior: lo dibuja la barra que lo contiene. */
@Composable
private fun glassFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
)

/** Campo de email para dentro de un [GlassFieldGroup]. */
@Composable
fun GlassEmailField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Email") },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else {
            null
        },
        colors = glassFieldColors(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Email,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

/** Campo de contraseña para dentro de un [GlassFieldGroup]. [PasswordField] sigue igual: lo usan también Home y la sala. */
@Composable
fun GlassPasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
    showPassword: Boolean,
    onToggleShow: () -> Unit,
    hint: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    val supporting = error ?: hint
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = if (supporting != null) {
            { Text(supporting) }
        } else {
            null
        },
        colors = glassFieldColors(),
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = onToggleShow, enabled = enabled) {
                Text(if (showPassword) "Ocultar" else "Mostrar", fontWeight = FontWeight.SemiBold)
            }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

@Composable
fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
    showPassword: Boolean,
    onToggleShow: () -> Unit,
    hint: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    val supporting = error ?: hint
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = if (supporting != null) {
            { Text(supporting) }
        } else {
            null
        },
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = onToggleShow, enabled = enabled) {
                Text(if (showPassword) "Ocultar" else "Mostrar")
            }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

@Composable
fun ErrorText(message: String?) {
    if (message != null) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun InfoText(message: String?) {
    if (message != null) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun PrimaryButton(text: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(text)
        }
    }
}

/**
 * Enlace de texto de las pantallas de entrada ("¿Olvidaste tu contraseña?", "Registrate", etc.): negrita de 16 sp
 * (antes el tamaño y peso de un `TextButton` normal, 14 sp), que sobre la foto se perdía. Color y sombra los pone
 * [GlassContent].
 */
@Composable
fun GlassLinkButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        )
    }
}

/**
 * Botón de las pantallas de entrada (Fase 8, paso 3): contorno fino sobre el vidrio, esquinas de 16 dp como la barra de campos y, en el modo claro, un relleno crema translúcido (`GlassColors.buttonTint`); texto en negrita de 18 sp. El color es `primary`
 * del panel (el mismo con el que se midió el contraste del texto verde: >= 4,5:1). `PrimaryButton` sigue igual porque
 * lo usa también la sala (contraseña de la sala).
 */
@Composable
fun GlassButton(text: String, busy: Boolean, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    val tint = LocalGlassColors.current.buttonTint
    OutlinedButton(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (busy) color.copy(alpha = 0.38f) else color),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = tint, contentColor = color, disabledContainerColor = tint, disabledContentColor = color,
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = color)
        } else {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
            )
        }
    }
}
