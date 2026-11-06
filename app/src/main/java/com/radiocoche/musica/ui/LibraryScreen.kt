package com.radiocoche.musica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radiocoche.musica.LibRow
import com.radiocoche.musica.LibView
import com.radiocoche.musica.MusicViewModel
import com.radiocoche.musica.RowKind
import com.radiocoche.musica.Screen
import com.radiocoche.musica.data.Kind

@Composable
fun LibraryScreen(vm: MusicViewModel) {
    val p = Tema.p
    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------- Arriba: volver y fuentes ----------
            Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CarButton(Modifier.width(96.dp).fillMaxHeight(), BtnStyle(p.panel, p.border2, p.text), onClick = { vm.screen = Screen.PLAYER }) {
                    CarIcon(Ic.Back, 30.dp, p.text, "Volver al reproductor")
                }
                SourceButton(vm, Kind.USB, vm.usbLabel())
                SourceButton(vm, Kind.SD, "Tarjeta SD")
                SourceButton(vm, Kind.INTERNAL, "Memoria interna")
                SourceButton(vm, Kind.BT, "Bluetooth")
            }

            // ---------- Centro: vistas y lista ----------
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(220.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (v in LibView.entries) {
                        val on = vm.libView == v
                        CarButton(
                            Modifier.weight(1f).fillMaxWidth(),
                            if (on) BtnStyle(p.accentBg, p.accent, p.accent) else panelStyle(),
                            onClick = { vm.setView(v) },
                        ) {
                            Text(
                                v.label, color = if (on) p.accent else p.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            )
                        }
                    }
                }

                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(p.panel)
                        .border(1.dp, p.border, RoundedCornerShape(20.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (vm.canGoUp) {
                            CarButton(Modifier.height(48.dp).width(110.dp), BtnStyle(p.raised, p.border2, p.text), radius = 12.dp, onClick = { vm.goUp() }) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CarIcon(Ic.Back, 20.dp, p.text)
                                    Text("Atrás", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Text(vm.heading, color = p.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        val songs = vm.rows.count { it.kind == RowKind.SONG }
                        val count = if (songs > 0) (if (songs == 1) "1 canción" else "$songs canciones") else if (vm.rows.isNotEmpty()) "${vm.rows.size}" else ""
                        Text(count, color = p.muted, fontSize = 17.sp)
                        if (songs > 0) {
                            CarButton(Modifier.height(48.dp).width(200.dp), accentStyle(), radius = 12.dp, onClick = { vm.playAll() }) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CarIcon(Ic.Play, 18.dp, p.onAccent)
                                    Text("Reproducir todo", color = p.onAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (vm.indexText.isNotEmpty() && vm.libView != LibView.FOLDERS) {
                        Text(vm.indexText, color = p.accent, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    }

                    val listState = rememberLazyListState()
                    LaunchedEffect(vm.heading, vm.libView) { listState.scrollToItem(0) }
                    if (vm.rows.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().weight(1f).border(1.dp, p.border2, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(vm.emptyMsg, color = p.muted, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxWidth().weight(1f),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(vm.rows, key = { i, r -> r.path.ifEmpty { r.title } + "#" + i }) { _, r ->
                                RowItem(r, playingNow = r.kind == RowKind.SONG && r.path == vm.now.path) { vm.tapRow(r) }
                            }
                        }
                    }
                }
            }

            // ---------- Abajo: lo que suena ----------
            Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CarButton(Modifier.weight(1f).fillMaxHeight(), panelStyle(), onClick = { vm.screen = Screen.PLAYER }) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text(vm.now.title.ifEmpty { "Sin música" }, color = p.text, fontSize = 26.sp, fontFamily = Tema.condensed, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (vm.now.artist.isNotEmpty()) Text(vm.now.artist, color = p.muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                CarButton(Modifier.width(140.dp).fillMaxHeight(), if (vm.playing) accentStyle() else panelStyle(), onClick = { vm.togglePlay() }) {
                    CarIcon(if (vm.playing) Ic.Pause else Ic.Play, 34.dp, if (vm.playing) p.onAccent else p.text, if (vm.playing) "Pausa" else "Reproducir")
                }
                CarButton(Modifier.width(110.dp).fillMaxHeight(), panelStyle(), onClick = { vm.next() }) {
                    CarIcon(Ic.Next, 32.dp, p.text, "Canción siguiente")
                }
            }
        }
        Toast(vm.toast)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SourceButton(vm: MusicViewModel, kind: Kind, label: String) {
    val p = Tema.p
    val available = vm.sources.any { it.kind == kind }
    val on = available && kind != Kind.BT && vm.libSource?.kind == kind
    val style = when {
        !available -> BtnStyle(Color.Transparent, p.border.copy(alpha = 0.45f), p.dim.copy(alpha = 0.45f))
        on -> accentStyle()
        else -> panelStyle()
    }
    CarButton(Modifier.weight(1f).fillMaxHeight(), style, enabled = available, onClick = { vm.pickLibrarySource(kind) }) {
        if (kind == Kind.BT) CarIcon(Ic.Bluetooth, 34.dp, style.fg, "Bluetooth")
        else Text(label, color = style.fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun RowItem(r: LibRow, playingNow: Boolean, onClick: () -> Unit) {
    val p = Tema.p
    val style = if (playingNow) BtnStyle(p.accentBg, p.accent, p.text) else BtnStyle(p.raised, p.border, p.text)
    CarButton(Modifier.fillMaxWidth().heightIn(min = 64.dp), style, radius = 14.dp, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(if (playingNow) p.accent else if (r.kind == RowKind.SONG) p.panel else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                when (r.kind) {
                    RowKind.FOLDER -> CarIcon(Ic.Folder, 26.dp, p.accent)
                    RowKind.ARTIST -> CarIcon(Ic.Person, 26.dp, p.accent)
                    RowKind.ALBUM -> CarIcon(Ic.Disc, 26.dp, p.accent)
                    RowKind.SONG -> Text("${r.songIndex + 1}", color = if (playingNow) p.onAccent else p.muted, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(r.title, color = if (playingNow) p.accent else p.text, fontSize = 26.sp, fontFamily = Tema.condensed, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.sub.isNotEmpty()) Text(r.sub, color = p.muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (r.end.isNotEmpty()) Text(r.end, color = p.muted, fontSize = 17.sp)
        }
    }
}
