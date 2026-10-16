package com.isra16.movienight.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.painter.Painter
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
}

/**
 * Barra que junta los campos de las pantallas de entrada: un solo contenedor redondeado de cristal, con un tinte casi
 * transparente (`GlassColors.barTint`) y un borde de un pelo que va de más brillante arriba (`barBorderTop`) a más tenue
 * abajo (`barBorder`), y los campos adentro sin contorno propio, separados con [GlassFieldDivider].
 * Va dentro de un [GlassPanel], que ya pone el texto oscuro y sin sombra del modo claro. Con el foco en cualquiera de los
 * campos de adentro, el borde se engrosa (2 dp) y se pone blanco (antes tomaba [GlassColors.accent], un verde casi negro).
 */
@Composable
fun GlassFieldGroup(content: @Composable ColumnScope.() -> Unit) {
    val glass = LocalGlassColors.current
    val shape = RoundedCornerShape(16.dp)
    var focused by remember { mutableStateOf(false) }
    val borderBrush = if (focused) SolidColor(Color.White) else Brush.verticalGradient(listOf(glass.barBorderTop, glass.barBorder))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(glass.barTint)
            .border(if (focused) 2.dp else 1.dp, borderBrush, shape)
            .onFocusChanged { focused = it.hasFocus },
        content = content,
    )
}

/** Línea fina entre dos campos de un [GlassFieldGroup] (`GlassColors.barDivider`: blanca en el modo claro, el borde al 50 % en el oscuro). */
@Composable
fun GlassFieldDivider() {
    HorizontalDivider(color = LocalGlassColors.current.barDivider)
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

/**
 * Campo de texto suelto para dentro de un [GlassFieldGroup] (Home: "Código o link de la sala"). Sin mayúsculas
 * automáticas; [imeAction] y [onAction] sirven para "Ir" o "Listo" en el teclado.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onAction: () -> Unit = {},
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else {
            null
        },
        colors = glassFieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onAction() }, onGo = { onAction() }, onNext = { onAction() }, onSend = { onAction() }),
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
                    fontSize = 13.sp,
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
 * Enlace de texto de las pantallas de entrada ("¿Olvidaste tu contraseña?", "Registrate", etc.): 14 sp, peso normal
 * (diseño "cristal"; antes negrita de 16 sp). Si se pasa [emphasis] y es parte de [text], esa parte va en peso Medium
 * ("¿No tenés cuenta? **Registrate**"). Color y sin sombra los pone el [GlassPanel].
 */
@Composable
fun GlassLinkButton(text: String, enabled: Boolean, onClick: () -> Unit, emphasis: String? = null, icon: Painter? = null) {
    val label = remember(text, emphasis) {
        buildAnnotatedString {
            append(text)
            val start = if (emphasis.isNullOrEmpty()) -1 else text.indexOf(emphasis)
            if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Medium), start, start + emphasis!!.length)
        }
    }
    // Sin TextButton: este impone 48 dp de alto mínimo y separaba demasiado los dos enlaces del login. Aquí el alto es
    // 32 dp (línea de 20 dp + 6 dp arriba y abajo). Mismos colores que TextButton: acento si está activo, 38 % si no.
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val labelStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
        if (icon == null) {
            Text(label, color = color, style = labelStyle)
        } else {
            // Icono decorativo delante del texto (Home, "Solo subir"): el texto ya dice qué hace, el icono no se lee aparte.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Text(label, color = color, style = labelStyle)
            }
        }
    }
}

/**
 * Botón de las pantallas de entrada (Fase 8, paso 3, diseño "cristal"): otra pieza de vidrio, no un bloque sólido.
 * Relleno translúcido `GlassColors.buttonFill`, borde de un pelo `buttonBorder` y texto `buttonContent` en 15 sp Medium
 * con la letra un poco abierta; esquinas de 16 dp como la barra, 48 dp de alto y 4 dp más de aire arriba. Mientras trabaja
 * se ve el indicador de progreso y el borde más tenue. `PrimaryButton` sigue igual porque lo usa también la sala (contraseña de la sala).
 */
@Composable
fun GlassButton(text: String, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val glass = LocalGlassColors.current
    // Con [enabled] en false (por ejemplo "Unirme" sin código) el borde y el texto se ven a la mitad; antes no había forma de atenuarlo.
    val dimmed = busy || !enabled
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.fillMaxWidth().padding(top = 4.dp).height(48.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (dimmed) glass.buttonBorder.copy(alpha = glass.buttonBorder.alpha * 0.5f) else glass.buttonBorder),
        colors = ButtonDefaults.buttonColors(
            containerColor = glass.buttonFill,
            contentColor = glass.buttonContent,
            disabledContainerColor = glass.buttonFill,
            disabledContentColor = glass.buttonContent.copy(alpha = 0.5f),
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = glass.buttonContent)
        } else {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
            )
        }
    }
}
