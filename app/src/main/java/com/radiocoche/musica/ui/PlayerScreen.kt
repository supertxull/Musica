package com.radiocoche.musica.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

@Composable
fun PlayerScreen(vm: MusicViewModel) {
    val p = Tema.p
    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------- Centro: anterior · carátula y datos · siguiente ----------
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CarButton(
                    Modifier.width(96.dp).height(150.dp),
                    BtnStyle(p.panel, p.border2, p.text),
                    radius = 22.dp,
                    onHold = { vm.seekBy(-5000) },
                    onClick = { vm.previous() },
                ) { CarIcon(Ic.Prev, 44.dp, p.text, "Canción anterior") }

                Row(
                    Modifier.weight(1f).padding(start = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
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
                        Text(
                            vm.now.title.ifEmpty { "Sin música" },
                            color = p.text, fontSize = 76.sp, lineHeight = 90.sp, fontFamily = Tema.condensed,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
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

                CarButton(
                    Modifier.width(96.dp).height(150.dp),
                    BtnStyle(p.panel, p.border2, p.text),
                    radius = 22.dp,
                    onHold = { vm.seekBy(5000) },
                    onClick = { vm.next() },
                ) { CarIcon(Ic.Next, 44.dp, p.text, "Canción siguiente") }
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
                    val tint = if (vm.playing) p.onAccent else p.text
                    CarIcon(if (vm.playing) Ic.Pause else Ic.Play, 36.dp, tint, if (vm.playing) "Pausa" else "Reproducir")
                }
                CarButton(Modifier.weight(1f).fillMaxHeight(), panelStyle(), onClick = { vm.openEq() }) {
                    CarIcon(Ic.Eq, 30.dp, p.text, "Ecualizador")
                }
                CarButton(Modifier.weight(1f).fillMaxHeight(), panelStyle(), onClick = { vm.showToast("Configuración de Música (próximamente)") }) {
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
    val base = Modifier.size(220.dp).clip(shape).border(1.dp, p.border2, shape)
    if (bmp != null) {
        Image(bmp, contentDescription = "Carátula", contentScale = ContentScale.Crop, modifier = base)
    } else {
        val album = vm.now.album.ifEmpty { vm.now.folder }
        Box(base.background(albumColor(album)), contentAlignment = Alignment.Center) {
            Text(initials(album), color = Color(0xE6FFFFFF), fontSize = 72.sp, fontFamily = Tema.condensed)
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
