package com.pairing40k.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pairing40k.app.data.Matchup
import com.pairing40k.app.engine.RoundMatch
import com.pairing40k.app.engine.StepAnalysis

/** Écart signé lisible : « +1,5 » / « −2 ». */
private fun signed(v: Double) = (if (v >= 0) "+" else "−") + fmt1(kotlin.math.abs(v))

/**
 * Cadre dépliable « Pourquoi ce conseil ? » : pour chaque option conseillée,
 * la meilleure parade adverse, le meilleur cas et les matchs probables de la
 * manche ; pourquoi alterner (stratégie mixte) ; ce que coûtent les autres
 * options. Les calculs lourds ne se font qu'à l'ouverture.
 *
 * [base] = points déjà acquis (échelle du calcul) : les valeurs affichées sont
 * des projections sur tout le pairing, comme « Projection » en haut d'écran.
 * [context] = ce qui se joue à cette étape ; [detail] = lignes propres à une option.
 */
@Composable
fun <O, T> WhyCard(
    m: Matchup,
    an: StepAnalysis<O, T>,
    base: Double,
    ourName: (O) -> String,
    theirName: (T) -> String,
    context: List<String>,
    detail: (O) -> List<String>,
    outlook: (O) -> List<RoundMatch>
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .border(1.dp, Muted.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Lightbulb, null, tint = Gold)
            Text(" Pourquoi ce conseil ?", color = Bone, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Muted)
        }
        if (open) WhyContent(m, an, base, ourName, theirName, context, detail, outlook)
    }
}

@Composable
private fun <O, T> WhyContent(
    m: Matchup, an: StepAnalysis<O, T>, base: Double,
    ourName: (O) -> String, theirName: (T) -> String,
    context: List<String>, detail: (O) -> List<String>, outlook: (O) -> List<RoundMatch>
) {
    val value = base + an.value
    val played = an.ours.indices.filter { an.ours[it].probability >= 0.01 }.sortedByDescending { an.ours[it].probability }
    val others = an.ours.indices.filter { it !in played }.sortedByDescending { an.ours[it].expected }.take(4)
    // Matchs probables : seulement pour les options conseillées, calculés une fois
    val outlooks = remember(an) { played.associateWith { outlook(an.ours[it].option) } }
    fun parry(i: Int) = an.theirs.indices.minBy { an.payoff[i][it] }
    fun bestCase(i: Int) = an.theirs.indices.maxBy { an.payoff[i][it] }

    Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
        Heading("Ce qui se joue")
        context.forEach { Line(it) }

        Heading("Le conseil")
        played.forEach { i ->
            val o = an.ours[i]
            val p = parry(i)
            val b = bestCase(i)
            Column(
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(6.dp))
                    .background(OurRed.copy(alpha = 0.10f)).padding(8.dp)
            ) {
                Text(
                    ourName(o.option) + (if (played.size > 1) " (${pct(o.probability)})" else "") + " → projection ${fmt1(base + o.expected)}",
                    color = Bone, fontWeight = FontWeight.Bold, fontSize = 13.sp
                )
                detail(o.option).forEach { Line(it) }
                Line("Leur meilleure parade : ${theirName(an.theirs[p].option)} → ${fmt1(base + an.payoff[i][p])}")
                if (an.payoff[i][b] - an.payoff[i][p] > 0.05)
                    Line("S'ils se trompent, jusqu'à ${fmt1(base + an.payoff[i][b])} (${theirName(an.theirs[b].option)})")
                val likely = outlooks[i].orEmpty().filter { it.probability >= 0.05 }.take(6)
                if (likely.isNotEmpty()) {
                    Text("Matchs probables de cette manche :", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    likely.forEach { r ->
                        Row(Modifier.fillMaxWidth()) {
                            Text("${m.ourPlayers[r.our].name} vs ${m.theirPlayers[r.their].name}", color = Bone, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(fmt1(m.scores[r.our][r.their]), color = scoreColor(m.scores[r.our][r.their], m.maxScore),
                                fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                            Text(pct(r.probability), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }

        if (played.size > 1) {
            Heading("Pourquoi alterner")
            played.forEach { i ->
                val p = parry(i)
                Line("Toujours « ${ourName(an.ours[i].option)} » ? Ils le devinent et répondent « ${theirName(an.theirs[p].option)} » : " +
                    "${fmt1(base + an.payoff[i][p])} (${signed(an.payoff[i][p] - an.value)}).")
            }
            Line("En mélangeant selon les %, tu t'assures ${fmt1(value)} en moyenne quoi qu'ils fassent : aucune parade ne marche à chaque fois.")
        } else if (played.size == 1) {
            val i = played.first()
            Line("C'est le meilleur choix quoi qu'ils fassent : même leur meilleure parade te laisse ${fmt1(base + an.payoff[i][parry(i)])}, " +
                "et aucune autre option ne garantit autant.", top = 6)
        }

        if (others.isNotEmpty()) {
            Heading("Les autres options")
            others.forEach { i ->
                val o = an.ours[i]
                val p = parry(i)
                Line("${ourName(o.option)} : ${fmt1(base + o.expected)} (${signed(o.expected - an.value)}) — " +
                    "leur parade « ${theirName(an.theirs[p].option)} » te laisse ${fmt1(base + an.payoff[i][p])}.")
                detail(o.option).take(1).forEach { Line("   $it") }
            }
        }

        Text(
            "Valeurs = points d'équipe projetés sur tout le pairing (déjà acquis + suite jouée au mieux des deux côtés). " +
                "Scores des matchs = ta matrice.",
            color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun Heading(text: String) =
    Text(text, color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun Line(text: String, top: Int = 2) =
    Text(text, color = Bone, fontSize = 12.sp, modifier = Modifier.padding(top = top.dp))
