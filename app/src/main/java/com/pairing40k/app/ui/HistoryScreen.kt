package com.pairing40k.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pairing40k.app.AppViewModel
import com.pairing40k.app.data.HistoryEntry
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val history = data.history
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Aucun pairing enregistré.\nTermine un pairing puis « Historique » pour le retrouver ici et saisir les vrais scores.",
                color = Muted)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Calibration(history) }
        items(history, key = { it.id }) { h -> HistoryCard(h, vm) }
    }
}

/** Les estimations sont-elles justes ? Écart moyen et biais sur les matchs joués. */
@Composable
private fun Calibration(history: List<HistoryEntry>) {
    val played = history.flatMap { h -> h.games.filter { it.actual != null }.map { it to h.maxScore } }
    if (played.isEmpty()) {
        Section("Fiabilité des estimations", subtitle = "Saisis les scores réels après les parties pour mesurer la justesse de votre matrice.") {}
        return
    }
    val bias = played.map { (g, _) -> g.actual!! - g.estimate }.average()
    val mae = played.map { (g, _) -> abs(g.actual!! - g.estimate) }.average()
    Section("Fiabilité des estimations", subtitle = "${played.size} match(s) joué(s) renseigné(s)") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("Écart moyen", fmt1(mae), sub = "points / partie")
            Stat("Biais", (if (bias >= 0) "+" else "") + fmt1(bias), color = if (bias >= 0) androidx.compose.ui.graphics.Color(0xFF6FBF73) else androidx.compose.ui.graphics.Color(0xFFE57373), sub = "réel − estimé")
        }
        Text(
            when {
                bias > 1 -> "Vos estimations sont pessimistes : vous faites mieux que prévu."
                bias < -1 -> "Vos estimations sont optimistes : revoyez la matrice à la baisse."
                else -> "Estimations bien calibrées."
            }, color = Bone, fontSize = 13.sp
        )
        // Par joueur : qui sur- ou sous-performe
        val byPlayer = history.flatMap { it.games }.filter { it.actual != null }.groupBy { it.ourPlayer }
        byPlayer.entries.sortedByDescending { e -> e.value.map { it.actual!! - it.estimate }.average() }.forEach { (p, gs) ->
            val b = gs.map { it.actual!! - it.estimate }.average()
            Row {
                Text(p, color = OurRed, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${gs.size} partie(s)", color = Muted, fontSize = 12.sp, modifier = Modifier.width(80.dp))
                Text((if (b >= 0) "+" else "") + fmt1(b), color = Bone, fontSize = 13.sp, modifier = Modifier.width(48.dp))
            }
        }
    }
}

@Composable
private fun HistoryCard(h: HistoryEntry, vm: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(h.date))
    Section("${h.ourTeam} vs ${h.theirTeam}", subtitle = date) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Estimé ${fmt1(h.estimateTotal)}", color = Bone, modifier = Modifier.weight(1f))
            h.actualTotal?.let { Text("Réel ${fmt1(it)}", color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp)) }
            IconButton({ open = !open }) { Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Muted) }
            IconButton({ vm.deleteHistory(h.id) }) { Icon(Icons.Rounded.Delete, null, tint = Muted) }
        }
        if (open) {
            h.games.forEachIndexed { k, g ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${g.ourPlayer} vs ${g.theirPlayer}", color = Bone, fontSize = 13.sp, maxLines = 2)
                        Text("${g.kind} · estimé ${fmt1(g.estimate)}", color = Muted, fontSize = 11.sp)
                    }
                    var text by remember(h.id, k) { mutableStateOf(g.actual?.let(::fmt1) ?: "") }
                    OutlinedTextField(
                        text, { t ->
                            text = t
                            val v = t.replace(',', '.').toDoubleOrNull()
                            if (t.isBlank()) vm.setActual(h.id, k, null)
                            else if (v != null) vm.setActual(h.id, k, v.coerceIn(0.0, h.maxScore))
                        },
                        label = { Text("Réel") }, singleLine = true, modifier = Modifier.width(84.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
        }
    }
}
