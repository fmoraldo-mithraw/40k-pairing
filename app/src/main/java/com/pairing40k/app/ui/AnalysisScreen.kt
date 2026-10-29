package com.pairing40k.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pairing40k.app.Analysis
import com.pairing40k.app.AppViewModel
import com.pairing40k.app.data.Matchup

@Composable
fun AnalysisScreen(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val analysis by vm.analysis.collectAsState()
    val m = data.active
    val a = analysis
    if (a == null || a.matchupId != m.id) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Gold)
                Text("Calcul de toutes les séquences de pairing…", color = Muted, modifier = Modifier.padding(top = 12.dp))
            }
        }
        return
    }
    val total = m.maxScore * m.size
    val defenders = remember(a) { a.solver.analyzeDefenders(a.solver.fullMask, a.solver.fullMask) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Section("Pronostic", subtitle = "Si les deux équipes jouent parfaitement le pairing, avec vos estimations.") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("Score attendu", fmt1(a.value), color = scoreColor(a.value, total), sub = "sur ${fmt1(total)}")
                Stat("Plancher garanti", fmt1(a.secure), sub = "choix purs, adversaire devin")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("Idéal théorique", fmt1(a.best.total), color = Color(0xFF6FBF73), sub = "affiches au choix")
                Stat("Pire théorique", fmt1(a.worst.total), color = Color(0xFFE57373), sub = "affiches subies")
            }
            val verdict = when {
                a.value > total / 2 + m.size -> "Avantage net : le pairing devrait vous donner la victoire."
                a.value > total / 2 -> "Léger avantage : chaque carte compte."
                a.value > total / 2 - m.size -> "Match serré, légèrement défavorable : soignez le pairing."
                else -> "Défavorable sur le papier : visez les affiches qui limitent la casse."
            }
            Text(verdict, color = Bone, fontSize = 13.sp)
        }

        Section(
            "Premier défenseur", subtitle = "Stratégie optimale (mixte) : tire au sort selon les %, ne joue pas toujours le même. " +
                "Espér. = points attendus ; plancher = pire cas si l'adversaire lit votre jeu."
        ) {
            OptionHeader()
            defenders.ours.sortedByDescending { it.probability * 1000 + it.expected }.forEach {
                OptionRow(m.ourPlayers[it.option].label, it.probability, it.expected, it.floor, OurRed, highlight = it.probability > 0.01)
            }
            Text("Défenseur adverse probable", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            OptionHeader(expectedLabel = "Nous", floorLabel = null)
            defenders.theirs.sortedByDescending { it.probability }.forEach {
                OptionRow(m.theirPlayers[it.option].label, it.probability, it.expected, null, TheirBlue, highlight = it.probability > 0.01)
            }
        }

        Section("Affiches probables", subtitle = "Probabilité que chacun de nos joueurs affronte chaque adversaire, pairing joué à l'équilibre.") {
            ProbabilityGrid(m, a)
        }

        Section("Contribution par joueur", subtitle = "Points attendus de chaque joueur, et son adversaire le plus probable.") {
            m.ourPlayers.forEachIndexed { i, p ->
                val row = a.probabilities[i]
                val exp = row.indices.sumOf { row[it] * m.scores[i][it] }
                val likely = row.indices.maxByOrNull { row[it] } ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.label, color = Bone, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("→ ${m.theirPlayers[likely].name} ${pct(row[likely])}", color = Muted, fontSize = 12.sp,
                        modifier = Modifier.padding(end = 8.dp))
                    Box(Modifier.width(48.dp).clip(RoundedCornerShape(4.dp)).background(scoreColor(exp, m.maxScore)).padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center) {
                        Text(fmt1(exp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        Section("Affectation idéale", subtitle = "Ce que vous viseriez si vous choisissiez toutes les affiches (borne haute).") {
            m.ourPlayers.forEachIndexed { i, p ->
                val j = a.best.opponentOf[i]
                Row {
                    Text(p.name, color = OurRed, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(m.theirPlayers[j].name, color = TheirBlue, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(fmt1(m.scores[i][j]), color = Bone, fontSize = 13.sp)
                }
            }
        }

        Section("Profils", subtitle = "Moyenne de chaque joueur sur la ligne / colonne : les bons défenseurs ont peu de mauvaises affiches.") {
            m.ourPlayers.forEachIndexed { i, p ->
                val row = m.scores[i]
                Row {
                    Text(p.name, color = OurRed, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("moy. ${fmt1(row.average())}", color = Bone, fontSize = 12.sp, modifier = Modifier.width(70.dp))
                    Text("min ${fmt1(row.min())}", color = Muted, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                    Text("max ${fmt1(row.max())}", color = Muted, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                }
            }
            m.theirPlayers.forEachIndexed { j, p ->
                val col = m.scores.map { it[j] }
                Row {
                    Text(p.name, color = TheirBlue, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("moy. ${fmt1(col.average())}", color = Bone, fontSize = 12.sp, modifier = Modifier.width(70.dp))
                    Text("min ${fmt1(col.min())}", color = Muted, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                    Text("max ${fmt1(col.max())}", color = Muted, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                }
            }
        }
    }
}

@Composable
private fun ProbabilityGrid(m: Matchup, a: Analysis) {
    val cell = 46.dp
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        Column {
            Box(Modifier.width(90.dp).height(34.dp))
            m.ourPlayers.forEach { p ->
                Box(Modifier.width(90.dp).height(cell), contentAlignment = Alignment.CenterStart) {
                    Text(p.name, color = OurRed, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        m.theirPlayers.forEachIndexed { j, p ->
            Column {
                Box(Modifier.width(cell).height(34.dp), contentAlignment = Alignment.BottomCenter) {
                    Text(p.name, color = TheirBlue, fontSize = 10.sp, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 11.sp)
                }
                m.ourPlayers.indices.forEach { i ->
                    val pr = a.probabilities[i][j]
                    Box(
                        Modifier.size(cell).padding(2.dp).clip(RoundedCornerShape(5.dp))
                            .background(Gold.copy(alpha = (0.08 + 0.9 * pr).toFloat().coerceAtMost(1f))),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (pr < 0.005) "·" else pct(pr), color = if (pr > 0.45) Color.Black else Bone, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(fmt1(m.scores[i][j]), color = if (pr > 0.45) Color(0xFF333333) else Muted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}
