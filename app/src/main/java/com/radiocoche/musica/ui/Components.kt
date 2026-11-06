package com.radiocoche.musica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Estilo de un botón: fondo, borde y color del contenido. */
data class BtnStyle(val bg: Color, val border: Color?, val fg: Color, val dashed: Boolean = false)

fun panelStyle() = BtnStyle(Tema.p.panel, Tema.p.border, Tema.p.text)
fun accentStyle() = BtnStyle(Tema.p.accent, null, Tema.p.onAccent)

/**
 * Botón genérico del coche. Con [onHold] se repite mientras se mantiene pulsado
 * (avance y retroceso rápido); si se suelta antes, cuenta como pulsación normal.
 */
@Composable
fun CarButton(
    modifier: Modifier,
    style: BtnStyle,
    radius: Dp = 16.dp,
    enabled: Boolean = true,
    onHold: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val scope = rememberCoroutineScope()
    val click by rememberUpdatedState(onClick)
    val hold by rememberUpdatedState(onHold)
    var m = modifier.clip(shape).background(style.bg, shape)
    if (style.border != null) m = m.border(1.dp, style.border, shape)
    if (enabled) {
        m = m.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    var held = false
                    val job = hold?.let { h ->
                        scope.launch {
                            delay(600)
                            held = true
                            while (true) {
                                h()
                                delay(250)
                            }
                        }
                    }
                    val released = tryAwaitRelease()
                    job?.cancel()
                    if (released && !held) click()
                },
            )
        }
    }
    Box(m, contentAlignment = Alignment.Center, content = content)
}

@Composable
fun CarIcon(icon: ImageVector, size: Dp, tint: Color, description: String? = null) {
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size))
}

/** Aviso breve abajo en el centro, como en la Radio. */
@Composable
fun BoxScope.Toast(msg: String) {
    if (msg.isEmpty()) return
    Text(
        msg,
        color = Tema.p.bg,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 104.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Tema.p.text)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}
