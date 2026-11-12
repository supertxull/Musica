package com.radiocoche.musica.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.key
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.radiocoche.musica.MusicViewModel
import com.radiocoche.musica.data.Kind

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(vm: MusicViewModel) {
    val p = Tema.p
    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------- Centro: carátula y datos. Se desliza para cambiar de canción ----------
            SwipeArea(vm, Modifier.weight(1f).fillMaxWidth()) {
                if (vm.showLyrics) {
                    LyricsPanel(vm, Modifier.weight(1f).height(400.dp))
                } else {
                    Cover(vm)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                (vm.current?.label ?: "—").uppercase(),
                                color = p.onAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                                modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(p.accent).padding(horizontal = 11.dp, vertical = 5.dp),
                            )
                            val n = vm.now
                            if (n.count > 0) {
                                Text(
                                    "Carpeta: ${n.folder} · ${n.index + 1} de ${n.count}",
                                    color = p.muted, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        // Si el título no cabe: espera dos segundos y se desplaza sin parar,
                        // con dos segundos de pausa antes de cada vuelta. Empieza de nuevo con cada canción.
                        key(vm.now.path) {
                            Text(
                                vm.now.title.ifEmpty { "Sin música" },
                                color = p.text, fontSize = 76.sp, lineHeight = 90.sp, fontFamily = Tema.condensed,
                                maxLines = 1, softWrap = false,
                                modifier = Modifier.padding(bottom = 4.dp).basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = 2000,
                                    repeatDelayMillis = 2000,
                                    velocity = 70.dp,
                                ),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CarIcon(Ic.Person, 24.dp, p.accent)
                            Text(vm.now.artist, color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CarIcon(Ic.Disc, 22.dp, p.accent)
                            Text(vm.now.album, color = p.muted, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ModeButton(vm.favorite, { vm.toggleFavorite() }) {
                                CarIcon(if (vm.favorite) Ic.HeartOn else Ic.Heart, 24.dp, it, if (vm.favorite) "Quitar de favoritas" else "Añadir a favoritas")
                            }
                            ModeButton(vm.shuffle, { vm.toggleShuffle() }) { CarIcon(Ic.Shuffle, 24.dp, it, "Aleatorio") }
                            ModeButton(vm.repeat != Player.REPEAT_MODE_OFF, { vm.cycleRepeat() }) { tint ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CarIcon(Ic.Repeat, 24.dp, tint, "Repetir")
                                    if (vm.repeat == Player.REPEAT_MODE_ONE) Text("1", color = tint, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ---------- Barra de progreso ----------
            ProgressPanel(vm)

            // ---------- Barra inferior ----------
            Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CarButton(Modifier.weight(1f).fillMaxHeight(), panelStyle(), onClick = { vm.openLibrary() }) {
                    CarIcon(Ic.Library, 32.dp, p.text, "Biblioteca")
                }
                CarButton(Modifier.weight(1.2f).fillMaxHeight(), panelStyle(), onClick = { vm.cycleSource() }) {
                    val cur = vm.current
                    if (cur?.kind == Kind.BT) CarIcon(Ic.Bluetooth, 34.dp, p.text, "Fuente: Bluetooth")
                    else Text(shortLabel(cur?.label), color = p.text, fontSize = 34.sp, fontFamily = Tema.condensed)
                }
                CarButton(
                    Modifier.weight(1.4f).fillMaxHeight(),
                    if (vm.playing) accentStyle() else panelStyle(),
                    onClick = { vm.togglePlay() },
                ) {
                    val tint = if (vm.playing) p.accent else p.text
                    CarIcon(if (vm.playing) Ic.Pause else Ic.Play, 36.dp, tint, if (vm.playing) "Pausa" else "Reproducir")
                }
                CarButton(Modifier.weight(1f).fillMaxHeight(), panelStyle(), onClick = { vm.openEq() }) {
                    CarIcon(Ic.Eq, 30.dp, p.text, "Ecualizador")
                }
                CarButton(Modifier.weight(1f).fillMaxHeight(), panelStyle(), onClick = { vm.openSettings() }) {
                    CarIcon(Ic.Gear, 32.dp, p.text, "Configuración")
                }
            }
        }
        Toast(vm.toast)
    }
}

private fun shortLabel(label: String?): String = when (label) {
    null -> "—"
    "Tarjeta SD" -> "SD"
    "Memoria interna" -> "INT"
    else -> label
}

/**
 * Zona central deslizable, como las presintonías de la radio:
 * hacia la izquierda, canción siguiente; hacia la derecha, la anterior.
 */
@Composable
private fun SwipeArea(vm: MusicViewModel, modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var widthPx by remember { mutableStateOf(1f) }
    val tracker = remember { VelocityTracker() }
    Box(
        modifier
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().x
                        scope.launch {
                            val dx = offset.value
                            val dir = when {
                                dx < -widthPx * 0.18f || (v < -900f && dx < 0) -> 1
                                dx > widthPx * 0.18f || (v > 900f && dx > 0) -> -1
                                else -> 0
                            }
                            if (dir == 0) {
                                offset.animateTo(0f, tween(170))
                            } else {
                                offset.animateTo(-dir * widthPx, tween(170))
                                if (dir > 0) vm.swipeNext() else vm.swipePrevious()
                                offset.snapTo(dir * widthPx * 0.6f)
                                offset.animateTo(0f, tween(170))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, tween(170)) } },
                ) { change, amount ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch { offset.snapTo(offset.value + amount) }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).graphicsLayer {
                translationX = offset.value
                alpha = (1f - abs(offset.value) / 900f).coerceAtLeast(0.35f)
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            content = content,
        )
    }
}

@Composable
private fun ModeButton(on: Boolean, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
    val p = Tema.p
    val style = if (on) BtnStyle(p.accentBg, p.accent, p.accent) else BtnStyle(Color.Transparent, p.border2, p.dim)
    CarButton(Modifier.width(150.dp).height(54.dp), style, radius = 12.dp, onClick = onClick) { content(style.fg) }
}

@Composable
private fun Cover(vm: MusicViewModel) {
    val p = Tema.p
    val shape = RoundedCornerShape(22.dp)
    val bmp = vm.cover
    val base = Modifier.size(320.dp).clip(shape).border(1.dp, p.border2, shape)
        .pointerInput(Unit) { detectTapGestures { vm.toggleLyrics() } }
    if (bmp != null) {
        Image(bmp, contentDescription = "Carátula", contentScale = ContentScale.Crop, modifier = base.background(Color.Black))
    } else {
        val album = vm.now.album.ifEmpty { vm.now.folder }
        Box(base.background(albumColor(album)), contentAlignment = Alignment.Center) {
            Text(initials(album), color = Color(0xE6FFFFFF), fontSize = 104.sp, fontFamily = Tema.condensed)
        }
    }
}

/** Color tranquilo y fijo para cada álbum sin carátula. */
private fun albumColor(name: String): Color {
    val colors = listOf(0xFF5A4A6E, 0xFF4A5E6E, 0xFF6E4A4A, 0xFF3E4A5E, 0xFF6E5A3E, 0xFF4E6E52, 0xFF5E5A4A, 0xFF4A6E6A)
    return Color(colors[(name.hashCode() and 0x7fffffff) % colors.size])
}

private fun initials(name: String): String {
    val words = name.split(' ', '-', '_').map { w -> w.filter { it.isLetter() } }.filter { it.length > 2 }
    return words.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "♪" }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun ProgressPanel(vm: MusicViewModel) {
    val p = Tema.p
    var widthPx by remember { mutableStateOf(1f) }
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val dur = vm.durationMs
    val frac = dragFrac ?: if (dur > 0) (vm.positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val density = LocalDensity.current

    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).background(p.panel)
            .border(1.dp, p.border, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(fmt(if (dragFrac != null) (dur * frac).toLong() else vm.positionMs), color = p.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
        Box(
            Modifier.weight(1f).height(44.dp)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) { detectTapGestures { o -> vm.seekToFraction(o.x / widthPx) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragFrac = (o.x / widthPx).coerceIn(0f, 1f) },
                        onDragEnd = { dragFrac?.let { vm.seekToFraction(it) }; dragFrac = null },
                        onDragCancel = { dragFrac = null },
                    ) { change, _ -> dragFrac = (change.position.x / widthPx).coerceIn(0f, 1f) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.track))
            Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.accent))
            val knobX = with(density) { (widthPx * frac).toDp() } - 16.dp
            Box(
                Modifier.offset(x = knobX).size(32.dp).clip(CircleShape).background(p.glow),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(24.dp).clip(CircleShape).background(p.accent)) }
        }
        Text(fmt(dur), color = p.muted, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/**
 * Letra de la canción a todo el ancho. Al tocarla, vuelve la carátula.
 * Sincronizada: avanza sola con la línea actual en naranja, centrada.
 * Sin tiempos: el texto completo, para desplazarlo con el dedo.
 */
@Composable
private fun LyricsPanel(vm: MusicViewModel, modifier: Modifier) {
    val p = Tema.p
    val shape = RoundedCornerShape(22.dp)
    val ly = vm.lyrics
    Box(
        modifier.clip(shape).background(p.panel).border(1.dp, p.border2, shape)
            .pointerInput(Unit) { detectTapGestures { vm.toggleLyrics() } },
    ) {
        when {
            ly == null || ly.lines.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CarIcon(Ic.Note, 40.dp, p.muted)
                Text("Esta canción no tiene letra", color = p.muted, fontSize = 22.sp)
            }

            ly.synced -> {
                val lines = ly.lines
                val pos = vm.positionMs
                var cur = 0
                for (i in lines.indices) if ((lines[i].timeMs ?: 0L) <= pos) cur = i
                val lineH = 62.dp
                val target = with(LocalDensity.current) { (200.dp - lineH / 2 - lineH * cur).toPx() }
                val shift by animateFloatAsState(target, tween(400), label = "letra")
                Column(Modifier.fillMaxWidth().graphicsLayer { translationY = shift }) {
                    lines.forEachIndexed { i, l ->
                        val on = i == cur
                        Box(Modifier.fillMaxWidth().height(lineH).padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
                            Text(
                                l.text,
                                color = if (on) p.accent else p.dim,
                                fontSize = if (on) 46.sp else 30.sp,
                                fontFamily = if (on) Tema.condensed else null,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 70.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ly.lines.forEach { l ->
                    Text(l.text.ifEmpty { " " }, color = p.text, fontSize = 30.sp, lineHeight = 40.sp, textAlign = TextAlign.Center)
                }
            }
        }
        // Difuminado arriba y abajo.
        Box(Modifier.fillMaxWidth().height(90.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(p.panel, Color.Transparent))))
        Box(Modifier.fillMaxWidth().height(90.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, p.panel))))
    }
}
