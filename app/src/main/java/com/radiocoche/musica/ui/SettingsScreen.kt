package com.radiocoche.musica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radiocoche.musica.MusicViewModel
import com.radiocoche.musica.Screen

private val DangerBg = Color(0xFF2A1416)
private val DangerBorder = Color(0xFF8A3A34)
private val DangerText = Color(0xFFFF8A7E)

@Composable
fun SettingsScreen(vm: MusicViewModel) {
    val p = Tema.p
    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CarButton(Modifier.width(96.dp).fillMaxHeight(), BtnStyle(p.panel, p.border2, p.text), onClick = { vm.screen = Screen.PLAYER }) {
                    CarIcon(Ic.Back, 30.dp, p.text, "Volver al reproductor")
                }
                Text("Configuración", color = p.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            }

            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // ---------- Columna izquierda ----------
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Section("INICIO") {
                        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Reanudar la música", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (vm.resumeOnStart) "Continuar la reproducción en el mismo punto" else "Se queda en pausa hasta que pulses Play",
                                    color = p.muted, fontSize = 16.sp,
                                )
                            }
                            Switch(vm.resumeOnStart) { vm.toggleResume() }
                        }
                    }
                    Section("CANCIONES", Modifier.weight(1f)) {
                        Segmented(
                            listOf("Título de la canción" to false, "Nombre del archivo" to true),
                            vm.useFileNames,
                        ) { vm.setFileNames(it) }
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, p.border2, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("Ejemplo", color = p.dim, fontSize = 15.sp)
                            Text(
                                if (vm.useFileNames) "03 - The Smiths - This Charming Man" else "This Charming Man",
                                color = p.text, fontSize = 28.sp, fontFamily = Tema.condensed, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (vm.useFileNames) "Tal cual se llama el archivo, sin la extensión." else "Si el archivo no trae título, se usa su nombre.",
                                color = p.muted, fontSize = 16.sp,
                            )
                        }
                    }
                }

                // ---------- Columna derecha ----------
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Section("MEMORIA INTERNA") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { CarIcon(Ic.Folder, 26.dp, p.accent) }
                            Column(Modifier.weight(1f)) {
                                Text("Carpeta con tu música", color = p.muted, fontSize = 16.sp)
                                Text(vm.internalFolderLabel(), color = p.text, fontSize = 28.sp, fontFamily = Tema.condensed, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            CarButton(Modifier.height(56.dp).width(120.dp), BtnStyle(p.raised, p.border2, p.text), radius = 14.dp, onClick = { vm.openPicker() }) {
                                Text("Cambiar", color = p.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Section("BIBLIOTECA", Modifier.weight(1f)) {
                        Column {
                            Text("Volver a preparar la biblioteca", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text("Útil si cambiaste etiquetas o carátulas y no se ven. Vuelve a leer todas las canciones.", color = p.muted, fontSize = 16.sp)
                        }
                        CarButton(Modifier.fillMaxWidth().height(56.dp), BtnStyle(p.raised, p.border2, p.text), radius = 14.dp, onClick = { vm.updateLibrary() }) {
                            val txt = if (vm.updating) vm.indexText.ifEmpty { "Actualizando…" }.removePrefix("Preparando biblioteca de ") else "Actualizar"
                            Text(txt, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        CarButton(Modifier.fillMaxWidth().height(56.dp), BtnStyle(DangerBg, DangerBorder, DangerText), radius = 14.dp, onClick = { vm.confirmClearFavs = true }) {
                            Text("Borrar favoritas", color = DangerText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        if (vm.confirmClearFavs) ConfirmFavs(vm)
        if (vm.picking) FolderPicker(vm)
        Box(Modifier.fillMaxSize().padding(bottom = 0.dp)) { Toast(vm.toast) }
    }
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = Tema.p
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.panel)
            .border(1.dp, p.border, RoundedCornerShape(20.dp)).padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = p.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        content()
    }
}

@Composable
private fun Switch(on: Boolean, onToggle: () -> Unit) {
    val p = Tema.p
    CarButton(
        Modifier.width(64.dp).height(36.dp),
        if (on) BtnStyle(p.accent, p.accent, p.onAccent) else BtnStyle(p.bg, p.border2, p.dim),
        radius = 18.dp,
        onClick = onToggle,
    ) {
        Box(
            Modifier.align(Alignment.CenterStart).offset(x = if (on) 31.dp else 3.dp).size(28.dp).clip(CircleShape)
                .background(if (on) p.onAccent else p.dim)
        )
    }
}

@Composable
private fun Segmented(options: List<Pair<String, Boolean>>, selected: Boolean, onPick: (Boolean) -> Unit) {
    val p = Tema.p
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.bg).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((label, value) in options) {
            val on = value == selected
            CarButton(
                Modifier.weight(1f).height(52.dp),
                if (on) BtnStyle(p.accent, null, p.onAccent) else BtnStyle(Color.Transparent, null, p.muted),
                radius = 10.dp,
                onClick = { onPick(value) },
            ) {
                Text(label, color = if (on) p.onAccent else p.muted, fontSize = 19.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Scrim(content: @Composable () -> Unit) {
    val p = Tema.p
    // El fondo oscuro también recoge los toques, para no pulsar lo que hay debajo.
    CarButton(Modifier.fillMaxSize(), BtnStyle(p.bg.copy(alpha = 0.8f), null, p.text), radius = 0.dp, onClick = {}) {
        content()
    }
}

@Composable
private fun ConfirmFavs(vm: MusicViewModel) {
    val p = Tema.p
    Scrim {
        Column(
            Modifier.width(540.dp).clip(RoundedCornerShape(22.dp)).background(p.panel)
                .border(1.dp, p.border2, RoundedCornerShape(22.dp)).padding(horizontal = 32.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("¿Borrar todas las favoritas?", color = p.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Se quitará el corazón de todas las canciones. Las canciones no se borran. Esta acción no se puede deshacer.",
                color = p.muted, fontSize = 21.sp, lineHeight = 29.sp,
            )
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CarButton(Modifier.weight(1f).height(64.dp), BtnStyle(p.raised, p.border2, p.text), onClick = { vm.confirmClearFavs = false }) {
                    Text("Cancelar", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                CarButton(Modifier.weight(1f).height(64.dp), BtnStyle(DangerText, null, Color(0xFF1F0B09)), onClick = { vm.clearFavorites() }) {
                    Text("Borrar", color = Color(0xFF1F0B09), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FolderPicker(vm: MusicViewModel) {
    val p = Tema.p
    Scrim {
        Column(
            Modifier.width(720.dp).height(500.dp).clip(RoundedCornerShape(22.dp)).background(p.panel)
                .border(1.dp, p.border2, RoundedCornerShape(22.dp)).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (vm.canPickUp()) {
                    CarButton(Modifier.size(52.dp), BtnStyle(p.raised, p.border2, p.text), radius = 14.dp, onClick = { vm.pickUp() }) {
                        CarIcon(Ic.Back, 22.dp, p.text, "Subir un nivel")
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Elige la carpeta de música", color = p.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text("Memoria interna" + vm.pickLabel(), color = p.muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                CarButton(Modifier.size(52.dp), BtnStyle(p.raised, p.border2, p.text), radius = 14.dp, onClick = { vm.closePicker() }) {
                    Text("✕", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (vm.pickDirs.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().weight(1f).border(1.dp, p.border2, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No hay más carpetas aquí dentro", color = p.muted, fontSize = 19.sp, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.pickDirs, key = { it.absolutePath }) { d ->
                        CarButton(Modifier.fillMaxWidth().height(60.dp), BtnStyle(p.raised, p.border, p.text), radius = 14.dp, onClick = { vm.browse(d) }) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                CarIcon(Ic.Folder, 26.dp, p.accent)
                                Text(d.name, color = p.text, fontSize = 26.sp, fontFamily = Tema.condensed, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            CarButton(Modifier.fillMaxWidth().height(60.dp), accentStyle(), onClick = { vm.useThisFolder() }) {
                Text("Usar esta carpeta", color = p.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
