package com.pairing40k.app.engine

import kotlin.math.abs
import kotlin.math.sign

/**
 * Les matrices sont remplies « au feeling » : un gros écart (3/20, 17/20) est
 * en général une vraie lecture du match, alors qu'un 12 ou un 8 tient souvent
 * de l'impression et vaut à peine mieux qu'une égalité.
 *
 * On rapproche donc les scores serrés de l'égalité, sans toucher aux gros
 * écarts : écart d = s − milieu, x = |d| / demi-échelle ;
 *   s' = milieu + d × (k + (1 − k) × lissage(x / seuil))
 * k = part de l'écart gardée tout près de l'égalité ; au-delà du [THRESHOLD]
 * (≥ 15 ou ≤ 5 sur 20) le score est gardé tel quel. Transformation croissante :
 * l'ordre des scores est conservé.
 */
object Reliability {

    /** Écart relatif (sur la demi-échelle) à partir duquel on fait pleinement confiance. */
    const val THRESHOLD = 0.5

    fun adjust(v: Double, maxScore: Double, keep: Double): Double {
        if (keep >= 1.0) return v
        val mid = maxScore / 2
        val d = v - mid
        val x = (abs(d) / mid).coerceIn(0.0, 1.0)
        val t = (x / THRESHOLD).coerceAtMost(1.0)
        val smooth = t * t * (3 - 2 * t)
        return mid + sign(d) * abs(d) * (keep + (1 - keep) * smooth)
    }

    fun adjust(scores: List<List<Double>>, maxScore: Double, keep: Double): List<List<Double>> =
        if (keep >= 1.0) scores else scores.map { row -> row.map { adjust(it, maxScore, keep) } }
}
