package com.pairing40k.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/** Nombre de manches : 2 matchs par manche, puis 4 d'un coup à la dernière. */
private fun roundsFor(n: Int) = (n - 4) / 2 + 1

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
    val title = if (st.phase == Phase.DONE) "Pairing terminé"
        else "Manche ${st.round} / ${roundsFor(m.size)}" + if (st.isLastRound) " · dernière" else ""
    Section(title, subtitle = if (st.phase != Phase.DONE) "${st.remaining} joueurs restants de chaque côté" else null) {
        if (st.phase != Phase.DONE) StepTracker(st.phase)
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

/** Les 3 étapes d'une manche, l'étape en cours en or. */
@Composable
private fun StepTracker(phase: Phase) {
    val steps = listOf(Phase.DEFENDERS to "Défenseurs", Phase.ATTACKERS to "Attaquants", Phase.CHOICES to "Choix")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEachIndexed { k, (p, label) ->
            val current = p == phase
            val done = p.ordinal < phase.ordinal
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                    .background(if (current) Gold else Color(0x22FFFFFF)).padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (if (done) "✓ " else "${k + 1}. ") + label,
                    color = if (current) Color.Black else Muted,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp, maxLines = 1
                )
            }
        }
    }
}

/** Consigne de l'étape en cours, en clair. */
@Composable
private fun Instruction(text: String) {
    Text(
        text, color = Bone, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp)).background(Color(0x14FFFFFF)).padding(10.dp)
    )
}

/**
 * Rappel bien visible de l'adversaire : « Contre leur défenseur — Paul (Orks) ».
 * [color] = couleur du camp de [who].
 */
@Composable
private fun VersusBanner(caption: String, who: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⚔", color = color, fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
        Column {
            Text(caption, color = Muted, fontSize = 11.sp)
            Text(who, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Points déjà acquis, dans l'échelle du calcul (comme la projection). */
private fun acquired(a: Analysis, st: PairingState) = st.games.sumOf { a.scores[it.our][it.their] }

/** Affiches d'un candidat défenseur contre leurs joueurs restants (scores de la matrice). */
private fun defenderDetail(m: Matchup, st: PairingState, d: Int): List<String> {
    val opp = PairingSolver.bits(st.theirLeft).sortedBy { m.scores[d][it] }
    if (opp.size < 2) return emptyList()
    fun show(l: List<Int>) = l.joinToString(", ") { "${m.theirPlayers[it].name} ${fmt1(m.scores[d][it])}" }
    return listOf(
        "Pires affiches : ${show(opp.take(2))} · meilleures : ${show(opp.takeLast(2).reversed())}",
        "En défense, s'ils lui envoient ses 2 pires, il prend la moins mauvaise : environ ${fmt1(m.scores[d][opp[1]])}."
    )
}

/** Score d'un joueur face à l'adversaire rappelé, pour choisir d'un coup d'œil. */
private fun withScore(name: String, v: Double) = "$name ${fmt1(v)}"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefendersStep(m: Matchup, a: Analysis, st: PairingState, training: Boolean, onNext: (PairingState) -> Unit) {
    val an = remember(st) { a.solver.analyzeDefenders(st.ourLeft, st.theirLeft) }
    var ours by remember(st) { mutableStateOf<Int?>(null) }
    var theirs by remember(st) { mutableStateOf<Int?>(null) }

    Instruction(
        "Chaque équipe pose un défenseur face cachée, puis on révèle en même temps." +
            if (training) " Choisis le nôtre et valide." else " ① Choisis le nôtre. ② Après révélation, touche le leur. ③ Valide."
    )
    Section("① Notre défenseur", subtitle = "Espér. = points d'équipe attendus · Plancher = pire cas si l'adversaire devine ton choix.") {
        Advice("pose", an.ours) { m.ourPlayers[it].name }
        WhyCard(
            m, an, acquired(a, st), { m.ourPlayers[it].name }, { "poser " + m.theirPlayers[it].name },
            context = listOf(
                "Notre défenseur choisira son adversaire entre 2 attaquants… mais c'est l'adversaire qui choisit lesquels " +
                    "lui envoyer : il enverra ceux contre qui il est le plus faible. Un bon défenseur a donc peu de mauvaises affiches ; " +
                    "un joueur qui a 2 ou 3 très mauvais matchs est vulnérable en défense.",
                "Le choix compte aussi pour la suite : qui reste dans le pool pour les manches suivantes. " +
                    "Le calcul joue toutes les manches jusqu'au bout."
            ),
            detail = { d -> defenderDetail(m, st, d) },
            outlook = { d -> a.solver.outlookDefender(st.ourLeft, st.theirLeft, d) }
        )
        OptionHeader()
        an.ours.sortedByDescending { it.probability * 1000 + it.expected }.forEach {
            OptionRow(m.ourPlayers[it.option].label, it.probability, it.expected, it.floor, OurRed,
                highlight = ours == it.option, dimmed = it.probability < 0.01, onClick = { ours = it.option })
        }
        Button({ ours = draw(an.ours) }, colors = ButtonDefaults.buttonColors(containerColor = OurRed)) {
            Icon(Icons.Rounded.Casino, null); Text(" Tirer notre défenseur")
        }
    }
    Section("② Leur défenseur", subtitle = if (training) "Choisi par l'adversaire virtuel à la validation." else "Touche le défenseur qu'ils ont révélé. « Nous » = nos points attendus s'il le pose.") {
        if (!training) Prediction("poseront", an.theirs) { m.theirPlayers[it].name }
        OptionHeader(probLabel = "Prévision", expectedLabel = "Nous", floorLabel = null)
        an.theirs.sortedByDescending { it.probability }.forEach {
            OptionRow(m.theirPlayers[it.option].label, it.probability, it.expected, null, TheirBlue,
                highlight = theirs == it.option, onClick = if (training) null else ({ theirs = it.option }))
        }
    }
    ValidateButton(
        missing = when {
            ours == null -> "Choisis notre défenseur"
            !training && theirs == null -> "Touche leur défenseur"
            else -> null
        },
        summary = ours?.let { o ->
            "Défenseurs : ${m.ourPlayers[o].name} (nous)" + (theirs?.takeIf { !training }?.let { " · ${m.theirPlayers[it].name} (eux)" } ?: "")
        }
    ) {
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
    val ourDef = m.ourPlayers[st.ourDefender]
    val theirDef = m.theirPlayers[st.theirDefender]
    // Score de chacun face au défenseur qu'il attaque
    val ourAtk = { p: Int -> withScore(m.ourPlayers[p].name, m.scores[p][st.theirDefender]) }
    val theirAtk = { q: Int -> withScore(m.theirPlayers[q].name, m.scores[st.ourDefender][q]) }

    Instruction(
        "Chaque équipe envoie 2 attaquants contre le défenseur d'en face : nos attaquants vont sur ${theirDef.name}, " +
            "les leurs sur ${ourDef.name}. Les chiffres = nos points estimés dans ce match."
    )
    Section("① Nos 2 attaquants") {
        VersusBanner("Ils attaquent leur défenseur", theirDef.label, TheirBlue)
        Advice("envoie", an.ours, suffix = " sur ${theirDef.name}") { p -> p.joinToString(" + ") { m.ourPlayers[it].name } }
        WhyCard(
            m, an, acquired(a, st), { p -> p.joinToString(" + ") { m.ourPlayers[it].name } },
            { q -> "envoyer " + q.joinToString(" + ") { m.theirPlayers[it].name } },
            context = listOf(
                "${theirDef.name} va garder celui de nos 2 attaquants qui l'arrange (a priori le moins bon pour nous) ; l'autre " +
                    (if (st.isLastRound) "jouera contre leur attaquant refusé." else "retourne dans le pool pour la suite."),
                "Nos scores contre ${theirDef.name} : " + PairingSolver.bits(st.ourLeft).filter { it != st.ourDefender }
                    .sortedByDescending { m.scores[it][st.theirDefender] }
                    .joinToString(" · ") { "${m.ourPlayers[it].name} ${fmt1(m.scores[it][st.theirDefender])}" },
                "Envoyer 2 joueurs bons contre lui limite les dégâts ; y envoyer un joueur « sacrifiable » garde nos meilleurs pour la suite. " +
                    "En même temps, ils envoient 2 attaquants sur ${ourDef.name}, qui choisira."
            ),
            detail = { p ->
                val sc = p.sortedBy { m.scores[it][st.theirDefender] }
                listOf(
                    "Contre ${theirDef.name} : " + p.joinToString(" · ") { "${m.ourPlayers[it].name} ${fmt1(m.scores[it][st.theirDefender])}" } +
                        " → il gardera a priori ${m.ourPlayers[sc.first()].name}"
                )
            },
            outlook = { p -> a.solver.outlookAttackers(st.ourLeft, st.theirLeft, st.ourDefender, st.theirDefender, p) }
        )
        OptionHeader()
        an.ours.sortedByDescending { it.probability * 1000 + it.expected }.take(8).forEach { o ->
            OptionRow(o.option.joinToString(" + ") { ourAtk(it) }, o.probability, o.expected, o.floor, OurRed,
                highlight = ours.sorted() == o.option, dimmed = o.probability < 0.01, onClick = { ours = o.option })
        }
        Text("Ou choisis les 2 joueurs (score contre ${theirDef.name}) :", color = Muted, fontSize = 12.sp)
        PlayerPicker(PairingSolver.bits(st.ourLeft).filter { it != st.ourDefender }, ours, ourAtk, OurRed) { ours = it }
        Button({ ours = draw(an.ours) }, colors = ButtonDefaults.buttonColors(containerColor = OurRed)) {
            Icon(Icons.Rounded.Casino, null); Text(" Tirer nos attaquants")
        }
    }
    Section("② Leurs 2 attaquants", subtitle = if (training) "Choisis par l'adversaire virtuel à la validation." else "Touche les 2 joueurs qu'ils ont envoyés.") {
        VersusBanner("Ils attaquent notre défenseur", ourDef.label, OurRed)
        if (!training) Prediction("enverront", an.theirs, suffix = " sur ${ourDef.name}") { p -> p.joinToString(" + ") { m.theirPlayers[it].name } }
        OptionHeader(probLabel = "Prévision", expectedLabel = "Nous", floorLabel = null)
        an.theirs.sortedByDescending { it.probability }.take(6).forEach { o ->
            OptionRow(o.option.joinToString(" + ") { theirAtk(it) }, o.probability, o.expected, null, TheirBlue,
                highlight = theirs.sorted() == o.option, onClick = if (training) null else ({ theirs = o.option }))
        }
        if (!training) {
            Text("Ou choisis les 2 joueurs (notre score avec ${ourDef.name}) :", color = Muted, fontSize = 12.sp)
            PlayerPicker(PairingSolver.bits(st.theirLeft).filter { it != st.theirDefender }, theirs, theirAtk, TheirBlue) { theirs = it }
        }
    }
    ValidateButton(
        missing = when {
            ours.size < 2 -> "Choisis nos 2 attaquants (${ours.size}/2)"
            !training && theirs.size < 2 -> "Touche leurs 2 attaquants (${theirs.size}/2)"
            else -> null
        },
        summary = if (ours.size == 2) "${ours.joinToString(" + ") { m.ourPlayers[it].name }} → ${theirDef.name}" +
            (if (!training && theirs.size == 2) "\n${theirs.joinToString(" + ") { m.theirPlayers[it].name }} → ${ourDef.name}" else "")
        else null
    ) {
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
    val ourDef = m.ourPlayers[st.ourDefender]
    val theirDef = m.theirPlayers[st.theirDefender]

    Instruction(
        "Chaque défenseur garde un des 2 attaquants d'en face ; l'autre " +
            (if (st.isLastRound) "joue contre l'attaquant refusé d'en face, et les 2 oubliés jouent ensemble."
            else "retourne dans le pool.") + " Les chiffres = nos points estimés dans ce match."
    )
    Section("① Notre défenseur choisit") {
        VersusBanner("${ourDef.name} choisit entre leurs attaquants",
            st.theirAttackers.joinToString("  ou  ") { m.theirPlayers[it].name }, TheirBlue)
        Advice("${ourDef.name} prend", an.ours) { m.theirPlayers[it].name }
        WhyCard(
            m, an, acquired(a, st), { "${ourDef.name} prend ${m.theirPlayers[it].name}" }, { "prendre " + m.ourPlayers[it].name },
            context = listOf(
                "${ourDef.name} affronte l'attaquant qu'il garde ; l'autre " +
                    (if (st.isLastRound) "joue contre notre attaquant refusé, et les 2 oubliés jouent ensemble."
                    else "retourne dans leur pool et pourra être posé contre nous plus tard."),
                "Le meilleur score immédiat n'est donc pas toujours le meilleur choix : le calcul compte aussi ce que devient l'attaquant refusé.",
                "En même temps, ${theirDef.name} choisit entre " + st.ourAttackers.joinToString(" et ") { m.ourPlayers[it].name } + "."
            ),
            detail = { t ->
                val other = st.theirAttackers.first { it != t }
                listOf("${ourDef.name} vs ${m.theirPlayers[t].name} : ${fmt1(m.scores[st.ourDefender][t])} · refusé : ${m.theirPlayers[other].name}")
            },
            outlook = { t -> a.solver.outlookChoice(st.ourLeft, st.theirLeft, st.ourDefender, st.theirDefender, st.ourAttackers, st.theirAttackers, t) }
        )
        OptionHeader()
        an.ours.forEach {
            OptionRow("${ourDef.name} vs ${m.theirPlayers[it.option].label} · ${fmt1(m.scores[st.ourDefender][it.option])}",
                it.probability, it.expected, it.floor, OurRed,
                highlight = ours == it.option, dimmed = it.probability < 0.01, onClick = { ours = it.option })
        }
        Button({ ours = draw(an.ours) }, colors = ButtonDefaults.buttonColors(containerColor = OurRed)) {
            Icon(Icons.Rounded.Casino, null); Text(" Tirer")
        }
    }
    Section("② Leur défenseur choisit", subtitle = if (training) "Choix de l'adversaire virtuel à la validation." else "Touche l'attaquant qu'il a pris.") {
        VersusBanner("${theirDef.name} choisit entre nos attaquants",
            st.ourAttackers.joinToString("  ou  ") { m.ourPlayers[it].name }, OurRed)
        if (!training) Prediction("prendront", an.theirs) { m.ourPlayers[it].name }
        OptionHeader(probLabel = "Prévision", expectedLabel = "Nous", floorLabel = null)
        an.theirs.forEach {
            OptionRow("${m.ourPlayers[it.option].label} vs ${theirDef.name} · ${fmt1(m.scores[it.option][st.theirDefender])}",
                it.probability, it.expected, null, TheirBlue,
                highlight = theirs == it.option, onClick = if (training) null else ({ theirs = it.option }))
        }
    }
    if (st.isLastRound && ours != null && theirs != null) {
        // Aperçu des 4 derniers matchs, oubliés compris
        Section("Aperçu") { GamesList(m, st.withChoices(ours!!, theirs!!).games.drop(st.games.size)) }
    }
    ValidateButton(
        missing = when {
            ours == null -> "Choisis l'adversaire de ${ourDef.name}"
            !training && theirs == null -> "Touche le choix de ${theirDef.name}"
            else -> null
        },
        summary = ours?.let { o ->
            "${ourDef.name} vs ${m.theirPlayers[o].name}" +
                (theirs?.takeIf { !training }?.let { "\n${m.ourPlayers[it].name} vs ${theirDef.name}" } ?: "")
        }
    ) {
        val t = if (training) draw(an.theirs) else theirs!!
        onNext(st.withChoices(ours!!, t))
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
        // FlowRow : sur un écran étroit, les boutons passent à la ligne au lieu d'être écrasés
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onSave, colors = ButtonDefaults.buttonColors(containerColor = Gold)) { Icon(Icons.Rounded.Save, null); Text(" Historique") }
            OutlinedButton({ shareText(context, m.title, summary(m, st.games)) }) { Icon(Icons.Rounded.Share, null); Text(" Partager") }
            OutlinedButton(onNew) { Icon(Icons.Rounded.RestartAlt, null); Text(" Nouveau pairing") }
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

/** Bouton de validation : dit ce qui manque, puis récapitule ce qui sera validé. */
@Composable
private fun ValidateButton(missing: String?, summary: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (missing == null && summary != null) Text(summary, color = Bone, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        Button(
            onClick, enabled = missing == null,
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) { Text(missing ?: "Révéler et valider", fontWeight = FontWeight.Bold) }
    }
}
