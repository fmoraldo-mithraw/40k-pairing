package com.pairing40k.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pairing40k.app.engine.OptionEval
import java.util.Locale

// Couleurs du logo : or impérial, rouge et bleu des deux camps, os
val Gold = Color(0xFFD4A23A)
val OurRed = Color(0xFFD0392F)
val TheirBlue = Color(0xFF3B7BD1)
val Bone = Color(0xFFEDE3CC)
val Muted = Color(0xFF9A948A)

private val Bad = Color(0xFFB3261E)
private val Even = Color(0xFF6B5E3C)
private val Good = Color(0xFF2E7D4F)

/** Rouge (défaite probable) → brun (égalité) → vert (victoire probable). */
fun scoreColor(v: Double, max: Double): Color {
    val t = (v / max).coerceIn(0.0, 1.0).toFloat()
    return if (t < 0.5f) lerp(Bad, Even, t * 2) else lerp(Even, Good, (t - 0.5f) * 2)
}

fun fmt1(v: Double): String = if (v == Math.rint(v)) v.toLong().toString() else String.format(Locale.FRANCE, "%.1f", v)
fun pct(p: Double): String = "${Math.round(p * 100)} %"

@Composable
fun Section(title: String, modifier: Modifier = Modifier, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title.uppercase(), color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        }
    }
}

@Composable
fun ProbBar(p: Double, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF))) {
        Box(Modifier.fillMaxWidth(p.toFloat().coerceIn(0f, 1f)).height(8.dp).background(color))
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, color: Color = Bone, sub: String? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 11.sp)
        if (sub != null) Text(sub, color = Muted, fontSize = 10.sp)
    }
}

/**
 * Ligne d'option : nom, barre et % (« à jouer » pour nous, « prévision » pour
 * eux), espérance, plancher. [dimmed] grise une option à ne pas jouer.
 */
@Composable
fun OptionRow(
    name: String, probability: Double, expected: Double?, floor: Double?, color: Color,
    highlight: Boolean = false, dimmed: Boolean = false, onClick: (() -> Unit)? = null
) {
    val mod = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
        .background(if (highlight) color.copy(alpha = 0.18f) else Color.Transparent)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 6.dp, vertical = 4.dp)
        .alpha(if (dimmed && !highlight) 0.4f else 1f)
    Row(mod, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, color = Bone, fontSize = 14.sp, maxLines = 1)
            ProbBar(probability, color, Modifier.fillMaxWidth().padding(top = 3.dp))
        }
        Text(pct(probability), color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(64.dp).padding(start = 8.dp))
        if (expected != null) Text(fmt1(expected), color = Bone, fontSize = 13.sp, modifier = Modifier.width(44.dp))
        if (floor != null) Text(fmt1(floor), color = Muted, fontSize = 13.sp, modifier = Modifier.width(56.dp))
    }
}

/** En-tête des colonnes : côté adversaire, [probLabel] = « Prévision ». */
@Composable
fun OptionHeader(probLabel: String = "À jouer", expectedLabel: String = "Espér.", floorLabel: String? = "Plancher") {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Text("", Modifier.weight(1f))
        Text(probLabel, color = Muted, fontSize = 11.sp, maxLines = 1, softWrap = false, modifier = Modifier.width(64.dp).padding(start = 8.dp))
        Text(expectedLabel, color = Muted, fontSize = 11.sp, modifier = Modifier.width(44.dp))
        if (floorLabel != null) Text(floorLabel, color = Muted, fontSize = 11.sp, maxLines = 1, softWrap = false, modifier = Modifier.width(56.dp))
    }
}

/** Les options à jouer (les autres sont à 0 %), de la plus fréquente à la moins. */
fun <T> playable(options: List<OptionEval<T>>) =
    options.filter { it.probability >= 0.01 }.sortedByDescending { it.probability }

private fun joinOr(names: List<String>) =
    if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString(", ") + " ou " + names.last()

/**
 * Conseil en clair : « pose Fabien ou Gaelle ». Plusieurs options = aussi
 * bonnes les unes que les autres ; on alterne pour rester imprévisible.
 */
@Composable
fun <T> Advice(verb: String, options: List<OptionEval<T>>, name: (T) -> String) {
    val best = playable(options)
    if (best.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Gold.copy(alpha = 0.14f))
            .border(1.dp, Gold.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(10.dp)
    ) {
        Text("Conseil : $verb ${joinOr(best.map { name(it.option) })}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(
            if (best.size == 1) "Le meilleur choix, quoi que fasse l'adversaire."
            else "Aussi bons les uns que les autres (même espérance). Ne joue pas toujours le même : " +
                "suis les % d'un pairing à l'autre, ou laisse le dé choisir. Les choix grisés sont à éviter.",
            color = Bone, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** Prévision de ce que posera un adversaire qui joue bien. */
@Composable
fun <T> Prediction(verb: String, options: List<OptionEval<T>>, name: (T) -> String) {
    val likely = playable(options)
    if (likely.isEmpty()) return
    Text(
        "Prévision : ils $verb sans doute " + joinOr(likely.map { "${name(it.option)} (${pct(it.probability)})" }) +
            ". Touche ce qu'ils ont vraiment révélé.",
        color = TheirBlue, fontSize = 12.sp
    )
}
