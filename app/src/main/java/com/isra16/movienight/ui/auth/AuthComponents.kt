package com.isra16.movienight.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
            ) {
                GlassPanel {
                    Text(title, style = AuthTitleStyle, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyLarge,
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
}

/**
 * Barra que junta los campos de las pantallas de entrada: un solo contenedor redondeado con tinte y borde fino
 * (`GlassColors.barTint` y `barBorder`), y los campos adentro sin contorno propio, separados con [GlassFieldDivider].
 * Va dentro de un [GlassPanel], que ya pone el texto oscuro y sin sombra del modo claro. Con el foco en cualquiera de los
 * campos de adentro, el borde se engrosa (2 dp) y toma el color de acento ([GlassColors.accent]).
 */
@Composable
fun GlassFieldGroup(content: @Composable ColumnScope.() -> Unit) {
    val glass = LocalGlassColors.current
    val shape = RoundedCornerShape(16.dp)
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(glass.barTint)
            .border(if (focused) 2.dp else 1.dp, if (focused) glass.accent else glass.barBorder, shape)
            .onFocusChanged { focused = it.hasFocus },
        content = content,
    )
}

/**
 * Línea fina entre dos campos de un [GlassFieldGroup]. En el modo claro el borde de la barra es blanco y no se vería sobre
 * ella: la línea es del color oscuro del texto (`barContent.outline`) al 18 %. En el oscuro sigue siendo el borde al 50 %.
 */
@Composable
fun GlassFieldDivider() {
    val glass = LocalGlassColors.current
    HorizontalDivider(color = glass.barContent?.outline?.copy(alpha = 0.18f) ?: glass.barBorder.copy(alpha = 0.5f))
}

/** Campo sin fondo ni línea inferior: lo dibuja la barra que lo contiene. Etiqueta, etiqueta con foco y cursor: ver `GlassColors`. */
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
    focusedLabelColor = LocalGlassColors.current.accent,
    unfocusedLabelColor = LocalGlassColors.current.fieldLabel,
    cursorColor = LocalGlassColors.current.accent,
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
                Text(
                    if (showPassword) "Ocultar" else "Mostrar",
                    fontWeight = FontWeight.Normal,
                    color = LocalGlassColors.current.accent.copy(alpha = if (enabled) 1f else 0.38f),
                )
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
 * (antes el tamaño y peso de un `TextButton` normal, 14 sp). Color y sin sombra los pone el [GlassPanel].
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
 * Botón de las pantallas de entrada (Fase 8, paso 3): SÓLIDO, para que se lea como la acción principal (antes era de
 * contorno, igual que la barra de campos). Relleno `GlassColors.buttonFill` y texto `buttonContent` (modo claro: verde
 * casi negro con texto crema, 17,5:1; modo oscuro: verde claro con texto verde oscuro, 7,7:1), esquinas de 16 dp como
 * la barra, 52 dp de alto y 4 dp más de aire arriba; texto en negrita de 18 sp. Mientras trabaja se ve el relleno un
 * poco más claro y el indicador de progreso. `PrimaryButton` sigue igual porque lo usa también la sala (contraseña de la sala).
 */
@Composable
fun GlassButton(text: String, busy: Boolean, onClick: () -> Unit) {
    val glass = LocalGlassColors.current
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = glass.buttonFill,
            contentColor = glass.buttonContent,
            disabledContainerColor = glass.buttonFill.copy(alpha = 0.75f),
            disabledContentColor = glass.buttonContent,
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = glass.buttonContent)
        } else {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
            )
        }
    }
}
