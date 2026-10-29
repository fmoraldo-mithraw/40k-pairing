package com.pairing40k.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pairing40k.app.Analysis
import com.pairing40k.app.AppViewModel
import com.pairing40k.app.data.Matchup
import com.pairing40k.app.engine.Game
import com.pairing40k.app.engine.OptionEval
import com.pairing40k.app.engine.PairingSolver
import com.pairing40k.app.engine.PairingState
import com.pairing40k.app.engine.Phase
import kotlin.random.Random

/** Tirage selon une stratégie mixte : c'est ce qui rend le jeu imprévisible. */
private fun <T> draw(options: List<OptionEval<T>>): T {
    var r = Random.nextDouble()
    for (o in options) {
        r -= o.probability
        if (r <= 0) return o.option
    }
    return options.maxBy { it.probability }.option
}

@Composable
fun PairingScreen(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val analysis by vm.analysis.collectAsState()
    val stack by vm.pairing.collectAsState()
    val m = data.active
    val a = analysis
    val st = stack.last()
    var training by rememberSaveable { mutableStateOf(false) }

    if (a == null || a.matchupId != m.id) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        StatusCard(m, a, st, stack.size > 1, training, onTraining = { training = it }, onUndo = vm::undoPairing, onReset = vm::resetPairing)
        when (st.phase) {
            Phase.DEFENDERS -> DefendersStep(m, a, st, training) { vm.pushPairing(it) }
            Phase.ATTACKERS -> AttackersStep(m, a, st, training) { vm.pushPairing(it) }
            Phase.CHOICES -> ChoicesStep(m, a, st, training) { vm.pushPairing(it) }
            Phase.DONE -> DoneStep(m, a, st, onSave = vm::savePairingToHistory, onNew = vm::resetPairing)
        }
        if (st.games.isNotEmpty() && st.phase != Phase.DONE) {
            Section("Matchs déjà fixés") { GamesList(m, st.games) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusCard(
    m: Matchup, a: Analysis, st: PairingState, canUndo: Boolean, training: Boolean,
    onTraining: (Boolean) -> Unit, onUndo: () -> Unit, onReset: () -> Unit
) {
    val fixed = st.games.sumOf { m.scores[it.our][it.their] }
    val rest = remember(st, a) {
        when (st.phase) {
        Phase.DEFENDERS -> a.solver.stateValue(st.ourLeft, st.theirLeft)
        Phase.ATTACKERS -> a.solver.analyzeAttackers(st.ourLeft, st.theirLeft, st.ourDefender, st.theirDefender).value
        Phase.CHOICES -> a.solver.analyzeChoice(st.ourLeft, st.theirLeft, st.ourDefender, st.theirDefender, st.ourAttackers, st.theirAttackers).value
        Phase.DONE -> 0.0
        }
    }
    val total = m.maxScore * m.size
    val title = when (st.phase) {
        Phase.DEFENDERS -> "Manche ${st.round} · défenseurs"
        Phase.ATTACKERS -> "Manche ${st.round} · attaquants"
        Phase.CHOICES -> "Manche ${st.round} · choix des défenseurs"
        Phase.DONE -> "Pairing terminé"
    }
    Section(title, subtitle = if (st.phase != Phase.DONE) "${st.remaining} joueurs restants de chaque côté" + if (st.isLastRound) " — dernière manche : refusés et oubliés jouent entre eux" else "" else null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("Projection", fmt1(fixed + rest), color = scoreColor(fixed + rest, total), sub = "sur ${fmt1(total)}")
            Stat("Déjà acquis", fmt1(fixed), sub = "${st.games.size} match(s)")
            Stat("Départ", fmt1(a.value), sub = "avant pairing")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            OutlinedButton(onUndo, enabled = canUndo) { Icon(Icons.Rounded.Undo, null); Text(" Annuler") }
            OutlinedButton(onReset, enabled = canUndo) { Icon(Icons.Rounded.RestartAlt, null); Text(" Recommencer") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SmartToy, null, tint = if (training) Gold else Muted)
                Text(" Entraînement", color = Muted, fontSize = 13.sp)
                Switch(training, onTraining, modifier = Modifier.padding(start = 4.dp))
            }
        }
        if (training) Text("Mode entraînement : l'adversaire virtuel joue sa stratégie optimale (tirage aléatoire) quand tu valides.",
            color = Gold, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefendersStep(m: Matchup, a: Analysis, st: PairingState, training: Boolean, onNext: (PairingState) -> Unit) {
    val an = remember(st) { a.solver.analyzeDefenders(st.ourLeft, st.theirLeft) }
    var ours by remember(st) { mutableStateOf<Int?>(null) }
    var theirs by remember(st) { mutableStateOf<Int?>(null) }

    Section("Notre défenseur", subtitle = "Tire au sort selon les % (bouton dé) : jouer toujours le « meilleur » se fait lire.") {
        OptionHeader()
        an.ours.sortedByDescending { it.probability * 1000 + it.expected }.forEach {
            OptionRow(m.ourPlayers[it.option].label, it.probability, it.expected, it.floor, OurRed,
                highlight = ours == it.option, onClick = { ours = it.option })
        }
        Button({ ours = draw(an.ours) }, colors = ButtonDefaults.buttonColors(containerColor = OurRed)) {
            Icon(Icons.Rounded.Casino, null); Text(" Tirer notre défenseur")
        }
    }
    Section("Leur défenseur", subtitle = if (training) "Choisi par l'adversaire virtuel à la validation." else "Saisis la carte révélée. % = ce qu'un adversaire optimal ferait ; « Nous » = nos points s'il le pose.") {
        OptionHeader(expectedLabel = "Nous", floorLabel = null)
        an.theirs.sortedByDescending { it.probability }.forEach {
            OptionRow(m.theirPlayers[it.option].label, it.probability, it.expected, null, TheirBlue,
                highlight = theirs == it.option, onClick = if (training) null else ({ theirs = it.option }))
        }
    }
    ValidateButton(enabled = ours != null && (training || theirs != null)) {
        val t = if (training) draw(an.theirs) else theirs!!
        onNext(st.withDefenders(ours!!, t))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttackersStep(m: Matchup, a: Analysis, st: PairingState, training: Boolean, onNext: (PairingState) -> Unit) {
    val an = remember(st) { a.solver.analyzeAttackers(st.ourLeft, st.theirLeft, st.ourDefender, st.theirDefender) }
    var ours by remember(st) { mutableStateOf<List<Int>>(emptyList()) }
    var theirs by remember(st) { mutableStateOf<List<Int>>(emptyList()) }

    Section("Défenseurs révélés") {
        Row {
            Text(m.ourPlayers[st.ourDefender].label, color = OurRed, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("vs", color = Muted)
            Text(m.theirPlayers[st.theirDefender].label, color = TheirBlue, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 12.dp))
        }
    }
    Section("Nos 2 attaquants", subtitle = "Contre ${m.theirPlayers[st.theirDefender].name}. Touche une paire ou choisis les joueurs.") {
        OptionHeader()
        an.ours.sortedByDescending { it.probability * 1000 + it.expected }.take(8).forEach { o ->
            OptionRow(o.option.joinToString(" + ") { m.ourPlayers[it].name }, o.probability, o.expected, o.floor, OurRed,
                highlight = ours.sorted() == o.option, onClick = { ours = o.option })
        }
        PlayerPicker(PairingSolver.bits(st.ourLeft).filter { it != st.ourDefender }, ours, { m.ourPlayers[it].name }, OurRed) { ours = it }
        Button({ ours = draw(an.ours) }, colors = ButtonDefaults.buttonColors(containerColor = OurRed)) {
            Icon(Icons.Rounded.Casino, null); Text(" Tirer nos attaquants")
        }
    }
    Section("Leurs 2 attaquants", subtitle = if (training) "Choisis par l'adversaire virtuel à la validation." else "Contre ${m.ourPlayers[st.ourDefender].name}.") {
        OptionHeader(expectedLabel = "Nous", floorLabel = null)
        an.theirs.sortedByDescending { it.probability }.take(6).forEach { o ->
            OptionRow(o.option.joinToString(" + ") { m.theirPlayers[it].name }, o.probability, o.expected, null, TheirBlue,
                highlight = theirs.sorted() == o.option, onClick = if (training) null else ({ theirs = o.option }))
        }
        if (!training) PlayerPicker(PairingSolver.bits(st.theirLeft).filter { it != st.theirDefender }, theirs, { m.theirPlayers[it].name }, TheirBlue) { theirs = it }
    }
    ValidateButton(enabled = ours.size == 2 && (training || theirs.size == 2)) {
        val t = if (training) draw(an.theirs) else theirs
        onNext(st.withAttackers(ours, t))
    }
}

@Composable
private fun ChoicesStep(m: Matchup, a: Analysis, st: PairingState, training: Boolean, onNext: (PairingState) -> Unit) {
    val an = remember(st) {
        a.solver.analyzeChoice(st.ourLeft, st.theirLeft, st.ourDefender, st.theirDefender, st.ourAttackers, st.theirAttackers)
    }
    var ours by remember(st) { mutableStateOf<Int?>(null) }
    var theirs by remember(st) { mutableStateOf<Int?>(null) }

    Section("${m.ourPlayers[st.ourDefender].name} choisit son adversaire", subtitle = "Notre défenseur prend l'un de leurs deux attaquants ; l'autre retourne dans le pool" +
        if (st.isLastRound) " (et jouera contre notre attaquant refusé)." else ".") {
        OptionHeader()
        an.ours.forEach {
            OptionRow(m.theirPlayers[it.option].label + "  (${fmt1(m.scores[st.ourDefender][it.option])})", it.probability, it.expected, it.floor, OurRed,
                highlight = ours == it.option, onClick = { ours = it.option })
        }
        Button({ ours = draw(an.ours) }, colors = ButtonDefaults.buttonColors(containerColor = OurRed)) {
            Icon(Icons.Rounded.Casino, null); Text(" Tirer")
        }
    }
    Section("${m.theirPlayers[st.theirDefender].name} choisit parmi nos attaquants", subtitle = if (training) "Choix de l'adversaire virtuel à la validation." else "Saisis son choix.") {
        OptionHeader(expectedLabel = "Nous", floorLabel = null)
        an.theirs.forEach {
            OptionRow(m.ourPlayers[it.option].label + "  (${fmt1(m.scores[it.option][st.theirDefender])})", it.probability, it.expected, null, TheirBlue,
                highlight = theirs == it.option, onClick = if (training) null else ({ theirs = it.option }))
        }
    }
    if (st.isLastRound && ours != null && theirs != null) {
        // Aperçu des 4 derniers matchs, oubliés compris
        Section("Aperçu") { GamesList(m, st.withChoices(ours!!, theirs!!).games.drop(st.games.size)) }
    }
    ValidateButton(enabled = ours != null && (training || theirs != null)) {
        val t = if (training) draw(an.theirs) else theirs!!
        onNext(st.withChoices(ours!!, t))
    }
}

@Composable
private fun DoneStep(m: Matchup, a: Analysis, st: PairingState, onSave: () -> Unit, onNew: () -> Unit) {
    val context = LocalContext.current
    val total = st.games.sumOf { m.scores[it.our][it.their] }
    Section("Résultat du pairing") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("Total estimé", fmt1(total), color = scoreColor(total, m.maxScore * m.size), sub = "sur ${fmt1(m.maxScore * m.size)}")
            Stat("Écart / équilibre", (if (total >= a.value) "+" else "") + fmt1(total - a.value), sub = "vs ${fmt1(a.value)} attendu")
        }
        GamesList(m, st.games)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onSave, colors = ButtonDefaults.buttonColors(containerColor = Gold)) { Icon(Icons.Rounded.Save, null); Text(" Historique") }
            OutlinedButton({ shareText(context, m.title, summary(m, st.games)) }) { Icon(Icons.Rounded.Share, null); Text(" Partager") }
            OutlinedButton(onNew) { Text("Nouveau") }
        }
    }
}

fun summary(m: Matchup, games: List<Game>): String {
    val sb = StringBuilder("Pairing ${m.title}\n")
    var t = 0.0
    games.forEachIndexed { k, g ->
        val v = m.scores[g.our][g.their]
        t += v
        sb.append("${k + 1}. ${m.ourPlayers[g.our].label} vs ${m.theirPlayers[g.their].label} — ${fmt1(v)}/${fmt1(m.maxScore)} (${g.kind.label})\n")
    }
    sb.append("Total estimé : ${fmt1(t)} / ${fmt1(m.maxScore * m.size)}")
    return sb.toString()
}

@Composable
fun GamesList(m: Matchup, games: List<Game>) {
    games.forEach { g ->
        val v = m.scores[g.our][g.their]
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
            Column(Modifier.weight(1f)) {
                Row {
                    Text(m.ourPlayers[g.our].name, color = OurRed, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(m.theirPlayers[g.their].name, color = TheirBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Text("Manche ${g.round} · ${g.kind.label}", color = Muted, fontSize = 11.sp)
            }
            Box(Modifier.width(52.dp).clip(RoundedCornerShape(5.dp)).background(scoreColor(v, m.maxScore)).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                Text(fmt1(v), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerPicker(players: List<Int>, selected: List<Int>, name: (Int) -> String, color: Color, onChange: (List<Int>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        players.forEach { p ->
            val sel = p in selected
            FilterChip(
                selected = sel,
                onClick = {
                    onChange(
                        when {
                            sel -> selected - p
                            selected.size < 2 -> (selected + p).sorted()
                            else -> (listOf(selected.last(), p)).sorted()
                        }
                    )
                },
                label = { Text(name(p)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.35f))
            )
        }
    }
}

@Composable
private fun ValidateButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick, enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
    ) { Text("Révéler et valider", fontWeight = FontWeight.Bold) }
}
