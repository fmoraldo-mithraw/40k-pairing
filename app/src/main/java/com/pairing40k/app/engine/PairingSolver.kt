package com.pairing40k.app.engine

import kotlin.math.min

/**
 * Solveur du « mini-jeu » de pairing en format équipe (WTC / ETC), 4, 6 ou 8
 * joueurs par équipe.
 *
 * Déroulé d'une manche, avec k joueurs restants de chaque côté :
 *  1. chaque équipe pose un défenseur (face cachée, révélation simultanée) ;
 *  2. chaque équipe pose deux attaquants contre le défenseur adverse ;
 *  3. chaque défenseur choisit son adversaire parmi les deux attaquants ; les
 *     attaquants refusés retournent dans le pool.
 * On forme ainsi deux matchs par manche. Quand il reste 4 joueurs (dernière
 * manche), les deux attaquants refusés s'affrontent, et les deux joueurs jamais
 * posés (les « oubliés ») s'affrontent aussi : 4 matchs d'un coup.
 *  - 8v8 : 2 + 2 + 4 matchs ;  6v6 : 2 + 4 ;  4v4 : 4.
 *
 * [scores] : scores[i][j] = points estimés de notre joueur i contre leur joueur
 * j (de 0 à [maxScore], somme nulle : l'adversaire marque maxScore − s).
 *
 * Deux lectures de chaque situation :
 *  - [Mode.NASH] : valeur d'équilibre (stratégies mixtes optimales des deux
 *    côtés) — l'espérance de points face à un adversaire qui joue bien ;
 *  - [Mode.SECURE] : le plancher garanti en jouant des choix purs, même si
 *    l'adversaire lit parfaitement nos cartes à chaque étape.
 *
 * Les équipes sont des masques de bits (joueur i présent ⇔ bit i), les valeurs
 * d'états sont mémorisées : un 8v8 complet se résout en une fraction de seconde.
 */
class PairingSolver(scores: List<List<Double>>, val maxScore: Double = 20.0) {

    enum class Mode { NASH, SECURE }

    val n: Int = scores.size
    private val s: Array<DoubleArray> = Array(n) { i -> DoubleArray(n) { j -> scores[i][j] } }
    private val stride = 1 shl n
    private val nashMemo = DoubleArray(stride * stride) { Double.NaN }
    private val secureMemo = DoubleArray(stride * stride) { Double.NaN }

    init {
        require(n in setOf(4, 6, 8)) { "Formats gérés : 4, 6 ou 8 joueurs (reçu $n)" }
        require(scores.all { it.size == n }) { "La matrice doit être carrée" }
    }

    val fullMask: Int get() = stride - 1

    fun score(our: Int, their: Int): Double = s[our][their]

    // ---------------------------------------------------------------- valeurs

    /** Points attendus pour nous sur les matchs restant à former. */
    fun stateValue(a: Int, b: Int, mode: Mode = Mode.NASH): Double {
        val memo = if (mode == Mode.NASH) nashMemo else secureMemo
        val key = a * stride + b
        val cached = memo[key]
        if (!cached.isNaN()) return cached
        val k = Integer.bitCount(a)
        require(k == Integer.bitCount(b)) { "Les deux équipes doivent avoir le même nombre de joueurs" }
        val v = when {
            k == 0 -> 0.0
            k == 1 -> s[lowest(a)][lowest(b)]
            k < 4 -> throw IllegalArgumentException("État impossible : $k joueurs restants")
            else -> aggregate(defenderMatrix(a, b, mode), mode)
        }
        memo[key] = v
        return v
    }

    private fun aggregate(m: Array<DoubleArray>, mode: Mode) =
        if (mode == Mode.NASH) MatrixGame.solve(m).value else MatrixGame.maximin(m)

    private fun defenderMatrix(a: Int, b: Int, mode: Mode): Array<DoubleArray> {
        val ours = bits(a)
        val theirs = bits(b)
        return Array(ours.size) { i ->
            DoubleArray(theirs.size) { j -> attackersValue(a, b, ours[i], theirs[j], mode) }
        }
    }

    private fun attackersValue(a: Int, b: Int, d: Int, e: Int, mode: Mode): Double =
        aggregate(attackersMatrix(a, b, d, e, mode), mode)

    private fun attackersMatrix(a: Int, b: Int, d: Int, e: Int, mode: Mode): Array<DoubleArray> {
        val ourPairs = pairs(a and (1 shl d).inv())
        val theirPairs = pairs(b and (1 shl e).inv())
        return Array(ourPairs.size) { i ->
            DoubleArray(theirPairs.size) { j -> choiceValue(a, b, d, e, ourPairs[i], theirPairs[j], mode) }
        }
    }

    private fun choiceValue(a: Int, b: Int, d: Int, e: Int, p: IntArray, q: IntArray, mode: Mode): Double {
        val v00 = choiceOutcome(a, b, d, e, p, q, 0, 0, mode)
        val v01 = choiceOutcome(a, b, d, e, p, q, 0, 1, mode)
        val v10 = choiceOutcome(a, b, d, e, p, q, 1, 0, mode)
        val v11 = choiceOutcome(a, b, d, e, p, q, 1, 1, mode)
        return if (mode == Mode.NASH) MatrixGame.value2x2(v00, v01, v10, v11)
        else MatrixGame.maximin2x2(v00, v01, v10, v11)
    }

    /**
     * Notre défenseur [d] prend leur attaquant q[i] ; leur défenseur [e] prend
     * notre attaquant p[j]. Retourne nos points pour ces matchs + la suite.
     */
    private fun choiceOutcome(
        a: Int, b: Int, d: Int, e: Int, p: IntArray, q: IntArray, i: Int, j: Int, mode: Mode
    ): Double {
        var v = s[d][q[i]] + s[p[j]][e]
        val restA = a and (1 shl d).inv() and (1 shl p[j]).inv()
        val restB = b and (1 shl e).inv() and (1 shl q[i]).inv()
        if (Integer.bitCount(a) == 4) {
            val refusedA = p[1 - j]
            val refusedB = q[1 - i]
            v += s[refusedA][refusedB]
            v += s[lowest(restA and (1 shl refusedA).inv())][lowest(restB and (1 shl refusedB).inv())]
        } else {
            v += stateValue(restA, restB, mode)
        }
        return v
    }

    // ------------------------------------------------------ analyses d'étape

    /** Défenseurs : nos options (joueur) et les leurs. */
    fun analyzeDefenders(a: Int, b: Int): StepAnalysis<Int, Int> {
        val ours = bits(a).toList()
        val theirs = bits(b).toList()
        return analyze(ours, theirs, defenderMatrix(a, b, Mode.NASH), defenderMatrix(a, b, Mode.SECURE))
    }

    /** Attaquants (défenseurs connus) : paires de joueurs des deux côtés. */
    fun analyzeAttackers(a: Int, b: Int, d: Int, e: Int): StepAnalysis<List<Int>, List<Int>> {
        val ours = pairs(a and (1 shl d).inv()).map { it.toList() }
        val theirs = pairs(b and (1 shl e).inv()).map { it.toList() }
        return analyze(ours, theirs, attackersMatrix(a, b, d, e, Mode.NASH), attackersMatrix(a, b, d, e, Mode.SECURE))
    }

    /**
     * Choix des défenseurs : nos options = l'attaquant adverse que prend notre
     * défenseur ; les leurs = notre attaquant que prend leur défenseur.
     */
    fun analyzeChoice(a: Int, b: Int, d: Int, e: Int, p: List<Int>, q: List<Int>): StepAnalysis<Int, Int> {
        val pa = p.toIntArray()
        val qa = q.toIntArray()
        fun matrix(mode: Mode) = Array(2) { i ->
            DoubleArray(2) { j -> choiceOutcome(a, b, d, e, pa, qa, i, j, mode) }
        }
        return analyze(q, p, matrix(Mode.NASH), matrix(Mode.SECURE))
    }

    private fun <O, T> analyze(
        ours: List<O>, theirs: List<T>, nash: Array<DoubleArray>, secure: Array<DoubleArray>
    ): StepAnalysis<O, T> {
        val sol = MatrixGame.solve(nash)
        val ourEvals = ours.mapIndexed { i, o ->
            var exp = 0.0
            for (j in theirs.indices) exp += nash[i][j] * sol.col[j]
            var floor = Double.POSITIVE_INFINITY
            for (j in theirs.indices) floor = min(floor, secure[i][j])
            OptionEval(o, sol.row[i], exp, floor)
        }
        val theirEvals = theirs.mapIndexed { j, t ->
            var exp = 0.0
            for (i in ours.indices) exp += sol.row[i] * nash[i][j]
            var best = Double.NEGATIVE_INFINITY
            for (i in ours.indices) best = maxOf(best, secure[i][j])
            OptionEval(t, sol.col[j], exp, best)
        }
        return StepAnalysis(sol.value, MatrixGame.maximin(secure), ourEvals, theirEvals)
    }

    // ------------------------------------------- probabilités des affiches

    /**
     * Probabilité que notre joueur i affronte leur joueur j si les deux équipes
     * jouent l'équilibre, depuis l'état (a, b). Propagation par niveaux : on
     * accumule la probabilité d'atteindre chaque état intermédiaire.
     */
    fun matchProbabilities(a: Int = fullMask, b: Int = fullMask): Array<DoubleArray> {
        val prob = Array(n) { DoubleArray(n) }
        var level = HashMap<Long, Double>()
        level[key(a, b)] = 1.0
        while (level.isNotEmpty()) {
            val next = HashMap<Long, Double>()
            for ((k, p) in level) {
                val sa = (k shr 32).toInt()
                val sb = k.toInt()
                val cnt = Integer.bitCount(sa)
                if (cnt == 1) { prob[lowest(sa)][lowest(sb)] += p; continue }
                if (cnt == 0) continue
                val ours = bits(sa)
                val theirs = bits(sb)
                val def = MatrixGame.solve(defenderMatrix(sa, sb, Mode.NASH))
                for (di in ours.indices) for (ei in theirs.indices) {
                    val pDef = p * def.row[di] * def.col[ei]
                    if (pDef < 1e-12) continue
                    val d = ours[di]; val e = theirs[ei]
                    val ourPairs = pairs(sa and (1 shl d).inv())
                    val theirPairs = pairs(sb and (1 shl e).inv())
                    val att = MatrixGame.solve(attackersMatrix(sa, sb, d, e, Mode.NASH))
                    for (pi in ourPairs.indices) for (qi in theirPairs.indices) {
                        val pAtt = pDef * att.row[pi] * att.col[qi]
                        if (pAtt < 1e-12) continue
                        val pp = ourPairs[pi]; val qq = theirPairs[qi]
                        val m = Array(2) { i -> DoubleArray(2) { j -> choiceOutcome(sa, sb, d, e, pp, qq, i, j, Mode.NASH) } }
                        val ch = MatrixGame.solve(m)
                        for (i in 0..1) for (j in 0..1) {
                            val pc = pAtt * ch.row[i] * ch.col[j]
                            if (pc < 1e-12) continue
                            prob[d][qq[i]] += pc
                            prob[pp[j]][e] += pc
                            val restA = sa and (1 shl d).inv() and (1 shl pp[j]).inv()
                            val restB = sb and (1 shl e).inv() and (1 shl qq[i]).inv()
                            if (cnt == 4) {
                                prob[pp[1 - j]][qq[1 - i]] += pc
                                prob[lowest(restA and (1 shl pp[1 - j]).inv())][lowest(restB and (1 shl qq[1 - i]).inv())] += pc
                            } else {
                                val nk = key(restA, restB)
                                next[nk] = (next[nk] ?: 0.0) + pc
                            }
                        }
                    }
                }
            }
            level = next
        }
        return prob
    }

    private fun key(a: Int, b: Int) = (a.toLong() shl 32) or (b.toLong() and 0xffffffffL)

    companion object {
        fun bits(mask: Int): IntArray {
            val out = IntArray(Integer.bitCount(mask))
            var m = mask
            var k = 0
            while (m != 0) {
                val low = Integer.numberOfTrailingZeros(m)
                out[k++] = low
                m = m and (m - 1)
            }
            return out
        }

        fun lowest(mask: Int) = Integer.numberOfTrailingZeros(mask)

        fun pairs(mask: Int): List<IntArray> {
            val b = bits(mask)
            val out = ArrayList<IntArray>(b.size * (b.size - 1) / 2)
            for (i in b.indices) for (j in i + 1 until b.size) out.add(intArrayOf(b[i], b[j]))
            return out
        }

        fun maskOf(players: Collection<Int>): Int = players.fold(0) { m, p -> m or (1 shl p) }
    }
}

/**
 * Une option d'une étape : [probability] = poids dans la stratégie optimale,
 * [expected] = nos points attendus si cette option est jouée (face à la
 * stratégie optimale d'en face), [floor] = pour nos options, le pire cas en
 * choix purs ; pour les options adverses, ce qu'on s'assure au mieux contre elle.
 */
class OptionEval<T>(val option: T, val probability: Double, val expected: Double, val floor: Double)

class StepAnalysis<O, T>(
    val value: Double,
    val secureValue: Double,
    val ours: List<OptionEval<O>>,
    val theirs: List<OptionEval<T>>
)
