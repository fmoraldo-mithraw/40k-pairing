package com.pairing40k.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.pairing40k.app.AppViewModel
import com.pairing40k.app.data.MatrixImport
import com.pairing40k.app.data.Matchup
import com.pairing40k.app.data.Player

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MatrixScreen(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val m = data.active
    val context = LocalContext.current

    var editCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editPlayer by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var askUrl by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importUri(uri)
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.writeTo(uri, "﻿" + MatrixImport.toCsv(data.active))
    }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.writeTo(uri, vm.backupJson())
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restoreBackup(uri)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Section("Confrontation") {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = m.title, onValueChange = {}, readOnly = true,
                    label = { Text("Matrice active (${data.matchups.size})") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    data.matchups.forEach { x ->
                        DropdownMenuItem(text = { Text("${x.title} · ${x.size}v${x.size}") }, onClick = { vm.select(x.id); expanded = false })
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip("Nouvelle", Icons.Rounded.Add) { vm.newMatchup() }
                ActionChip("Dupliquer", Icons.Rounded.ContentCopy) { vm.duplicate() }
                ActionChip("Supprimer", Icons.Rounded.Delete) { confirmDelete = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(m.ourTeam, { v -> vm.updateMatchup { it.copy(ourTeam = v) } }, label = { Text("Notre équipe") },
                    singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(m.theirTeam, { v -> vm.updateMatchup { it.copy(theirTeam = v) } }, label = { Text("Adversaire") },
                    singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Format", color = Muted)
                for (n in listOf(4, 6, 8)) {
                    FilterChip(selected = m.size == n, onClick = { vm.updateMatchup { it.resized(n) } }, label = { Text("${n}v$n") })
                }
            }
        }

        Section("Importer / exporter", subtitle = "CSV, Excel (.xlsx) ou lien Google Sheets. Grille (adversaires en 1re ligne, nos joueurs en 1re colonne) ou liste « joueur ; adversaire ; score ».") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip("Fichier", Icons.Rounded.FolderOpen) { openFile.launch(arrayOf("*/*")) }
                ActionChip("Google Sheets", Icons.Rounded.Link) { askUrl = true }
                ActionChip("Coller", Icons.Rounded.ContentPaste) {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = cb.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (text.isNullOrBlank()) vm.toast("Presse-papiers vide — copie la plage dans Sheets/Excel")
                    else vm.importText("Presse-papiers", text)
                }
                ActionChip("Exporter CSV", Icons.Rounded.Download) { saveCsv.launch("${m.theirTeam}.csv") }
                ActionChip("Partager CSV", Icons.Rounded.Share) { shareText(context, m.title, MatrixImport.toCsv(m)) }
                ActionChip("Sauvegarde", Icons.Rounded.Upload) { saveBackup.launch("pairing40k-sauvegarde.json") }
                ActionChip("Restaurer", Icons.Rounded.FolderOpen) { openBackup.launch(arrayOf("application/json", "*/*")) }
                ActionChip("Inverser", Icons.Rounded.SwapHoriz) {
                    // Point de vue adverse : utile pour préparer « leur » pairing
                    vm.updateMatchup { x ->
                        x.copy(
                            ourTeam = x.theirTeam, theirTeam = x.ourTeam,
                            ourPlayers = x.theirPlayers, theirPlayers = x.ourPlayers,
                            scores = List(x.size) { i -> List(x.size) { j -> x.maxScore - x.scores[j][i] } }
                        )
                    }
                }
            }
        }

        Section("Matrice des scores estimés", subtitle = "Lignes : nos joueurs · colonnes : adversaires · 0-${fmt1(m.maxScore)} (${fmt1(m.maxScore / 2)} = égalité). Touche une case ou un nom pour modifier.") {
            MatrixGrid(m, onCell = { i, j -> editCell = i to j }, onPlayer = { ours, i -> editPlayer = ours to i })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val avg = m.scores.flatten().average()
                Text("Moyenne : ${fmt1(avg)}", color = Muted, fontSize = 12.sp)
                Text("Total si moyenne : ${fmt1(avg * m.size)} / ${fmt1(m.maxScore * m.size)}", color = Muted, fontSize = 12.sp)
            }
        }

        Section("Notes") {
            OutlinedTextField(m.notes, { v -> vm.updateMatchup { it.copy(notes = v) } },
                placeholder = { Text("Listes, missions, consignes…") }, modifier = Modifier.fillMaxWidth().height(110.dp))
        }
    }

    editCell?.let { (i, j) ->
        CellDialog(m, i, j, onDismiss = { editCell = null }) { v ->
            vm.updateMatchup { it.withScore(i, j, v) }
        }
    }
    editPlayer?.let { (ours, i) ->
        val p = if (ours) m.ourPlayers[i] else m.theirPlayers[i]
        PlayerDialog(p, onDismiss = { editPlayer = null }) { vm.renamePlayer(ours, i, it); editPlayer = null }
    }
    if (askUrl) UrlDialog(onDismiss = { askUrl = false }) { askUrl = false; vm.importUrl(it) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer « ${m.title} » ?") },
            confirmButton = { TextButton({ vm.deleteActive(); confirmDelete = false }) { Text("Supprimer") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Annuler") } }
        )
    }
    ImportPreviewDialog(vm)
}

@Composable
fun ActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick, label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize)) }
    )
}

@Composable
fun MatrixGrid(m: Matchup, onCell: (Int, Int) -> Unit, onPlayer: (Boolean, Int) -> Unit) {
    val cell = 52.dp
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        Column {
            Box(Modifier.width(104.dp).height(56.dp))
            m.ourPlayers.forEachIndexed { i, p ->
                Column(
                    Modifier.width(104.dp).height(cell).clickable { onPlayer(true, i) }.padding(end = 6.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(p.name, color = OurRed, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (p.faction.isNotBlank()) Text(p.faction, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        m.theirPlayers.forEachIndexed { j, p ->
            Column {
                Column(
                    Modifier.width(cell).height(56.dp).clickable { onPlayer(false, j) }.padding(2.dp),
                    verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(p.name, color = TheirBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                        textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis, lineHeight = 12.sp)
                    if (p.faction.isNotBlank()) Text(p.faction, color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                m.ourPlayers.indices.forEach { i ->
                    val v = m.scores[i][j]
                    Box(
                        Modifier.size(cell).padding(2.dp).clip(RoundedCornerShape(6.dp))
                            .background(scoreColor(v, m.maxScore)).clickable { onCell(i, j) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(fmt1(v), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CellDialog(m: Matchup, i: Int, j: Int, onDismiss: () -> Unit, onSet: (Double) -> Unit) {
    var v by remember(i, j) { mutableStateOf(m.scores[i][j]) }
    var text by remember(i, j) { mutableStateOf(fmt1(m.scores[i][j])) }
    val max = m.maxScore
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${m.ourPlayers[i].label}\ncontre ${m.theirPlayers[j].label}", fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(8.dp)).background(scoreColor(v, max)), contentAlignment = Alignment.Center) {
                    Text("${fmt1(v)} – ${fmt1(max - v)}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Slider(value = v.toFloat(), onValueChange = { v = Math.round(it * 2) / 2.0; text = fmt1(v) }, valueRange = 0f..max.toFloat())
                // Raccourcis : grosse défaite → grosse victoire
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val presets = listOf("--" to 0.15, "-" to 0.35, "=" to 0.5, "+" to 0.65, "++" to 0.85)
                    presets.forEach { (lbl, f) ->
                        val pv = Math.round(max * f * 2) / 2.0
                        FilterChip(selected = v == pv, onClick = { v = pv; text = fmt1(pv) }, label = { Text("$lbl ${fmt1(pv)}") })
                    }
                }
                OutlinedTextField(text, { t ->
                    text = t
                    t.replace(',', '.').toDoubleOrNull()?.let { v = it.coerceIn(0.0, max) }
                }, label = { Text("Valeur") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = { TextButton({ onSet(v); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun PlayerDialog(p: Player, onDismiss: () -> Unit, onSave: (Player) -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var faction by remember { mutableStateOf(p.faction) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Joueur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true)
                OutlinedTextField(faction, { faction = it }, label = { Text("Faction / liste") }, singleLine = true)
            }
        },
        confirmButton = { TextButton({ onSave(Player(name.trim().ifEmpty { p.name }, faction.trim())) }) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun UrlDialog(onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lien Google Sheets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Colle le lien du fichier (partagé « tous les utilisateurs disposant du lien »). L'onglet ouvert dans le lien (gid) est celui importé.",
                    fontSize = 13.sp, color = Muted)
                OutlinedTextField(url, { url = it }, placeholder = { Text("https://docs.google.com/spreadsheets/d/…") }, singleLine = true)
            }
        },
        confirmButton = { TextButton({ onOk(url) }, enabled = url.isNotBlank()) { Text("Importer") } },
        dismissButton = { TextButton(onDismiss) { Text("Annuler") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportPreviewDialog(vm: AppViewModel) {
    val pending by vm.pendingImport.collectAsState()
    val p = pending ?: return
    AlertDialog(
        onDismissRequest = { vm.cancelImport() },
        title = { Text("Import : ${p.source}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p.error != null) Text(p.error, color = MaterialTheme.colorScheme.error)
                val r = p.result
                if (r != null) {
                    Text("${r.size}×${r.size} · disposition ${r.layout} · échelle « ${r.scale.label} »", color = Bone)
                    r.warnings.forEach { Text("⚠ $it", color = Gold, fontSize = 13.sp) }
                    Text("Nous : " + r.ourPlayers.joinToString { it.label }, fontSize = 12.sp, color = OurRed)
                    Text("Eux : " + r.theirPlayers.joinToString { it.label }, fontSize = 12.sp, color = TheirBlue)
                    // Mini aperçu coloré
                    Column {
                        r.scores.forEach { row ->
                            Row {
                                row.forEach { v ->
                                    Box(Modifier.size(26.dp).padding(1.dp).background(scoreColor(v, 20.0)), contentAlignment = Alignment.Center) {
                                        Text(fmt1(v), fontSize = 9.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
                if (p.rows.isNotEmpty()) {
                    Text("Échelle des valeurs", color = Muted, fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MatrixImport.Scale.entries.forEach { s ->
                            FilterChip(selected = p.scale == s, onClick = { vm.preview(p.source, p.rows, s) }, label = { Text(s.label, fontSize = 11.sp) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (p.result != null) Row {
                TextButton({ vm.confirmImport(asNew = true) }) { Text("Nouvelle matrice") }
                TextButton({ vm.confirmImport(asNew = false) }) { Text("Remplacer") }
            }
        },
        dismissButton = { TextButton({ vm.cancelImport() }) { Text("Annuler") } }
    )
}

fun shareText(context: Context, subject: String, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(i, "Partager"))
}
