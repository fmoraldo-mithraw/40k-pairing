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
 * [play] (facultatif) : matrice « pondérée » qui sert à choisir les cartes
 * (ex. joueurs marqueurs dont les écarts comptent plus). Les stratégies
 * d'équilibre sont calculées dessus, mais toutes les valeurs affichées
 * (espérances, plancher, projections) restent en vrais points de [scores].
 *
 * Les équipes sont des masques de bits (joueur i présent ⇔ bit i), les valeurs
 * d'états sont mémorisées : un 8v8 complet se résout en une fraction de seconde.
 */
class PairingSolver(
    scores: List<List<Double>>,
    val maxScore: Double = 20.0,
    play: List<List<Double>>? = null
) {

    enum class Mode { NASH, SECURE }

    val n: Int = scores.size
    /** Vrais points. */
    private val r: Array<DoubleArray> = Array(n) { i -> DoubleArray(n) { j -> scores[i][j] } }
    /** Points servant à choisir les cartes (= [r] sans pondération). */
    private val s: Array<DoubleArray> = if (play == null) r else Array(n) { i -> DoubleArray(n) { j -> play[i][j] } }
    /** Les stratégies sont-elles calculées sur une matrice pondérée ? */
    val weighted: Boolean = play != null && (0 until n).any { i -> (0 until n).any { j -> play[i][j] != scores[i][j] } }
    private val stride = 1 shl n
    private val nashMemo = DoubleArray(stride * stride) { Double.NaN }
    private val secureMemo = DoubleArray(stride * stride) { Double.NaN }
    private val realMemo = DoubleArray(stride * stride) { Double.NaN }

    init {
        require(n in setOf(4, 6, 8)) { "Formats gérés : 4, 6 ou 8 joueurs (reçu $n)" }
        require(scores.all { it.size == n }) { "La matrice doit être carrée" }
    }

    val fullMask: Int get() = stride - 1

    fun score(our: Int, their: Int): Double = r[our][their]

    /** NASH : matrice des stratégies ; SECURE : vrais points (garantie réelle). */
    private fun sc(mode: Mode) = if (mode == Mode.NASH) s else r

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
            k == 1 -> sc(mode)[lowest(a)][lowest(b)]
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
        val m = sc(mode)
        var v = m[d][q[i]] + m[p[j]][e]
        val restA = a and (1 shl d).inv() and (1 shl p[j]).inv()
        val restB = b and (1 shl e).inv() and (1 shl q[i]).inv()
        if (Integer.bitCount(a) == 4) {
            val refusedA = p[1 - j]
            val refusedB = q[1 - i]
            v += m[refusedA][refusedB]
            v += m[lowest(restA and (1 shl refusedA).inv())][lowest(restB and (1 shl refusedB).inv())]
        } else {
            v += stateValue(restA, restB, mode)
        }
        return v
    }

    // ------------------------------------------------- vrais points attendus

    /*
     * Vrais points attendus quand les deux équipes jouent les stratégies
     * d'équilibre de la matrice [s] (pondérée ou non). Sans pondération,
     * identique à la valeur NASH.
     */

    /** Vrais points attendus sur les matchs restant à former. */
    fun expectedValue(a: Int, b: Int): Double {
        if (!weighted) return stateValue(a, b)
        val key = a * stride + b
        val cached = realMemo[key]
        if (!cached.isNaN()) return cached
        val k = Integer.bitCount(a)
        val v = when (k) {
            0 -> 0.0
            1 -> r[lowest(a)][lowest(b)]
            else -> mixed(defenderMatrix(a, b, Mode.NASH), realDefenderMatrix(a, b))
        }
        realMemo[key] = v
        return v
    }

    /** Valeur réelle d'un sous-jeu : stratégies tirées de [play], gains de [real]. */
    private fun mixed(play: Array<DoubleArray>, real: Array<DoubleArray>): Double {
        val sol = MatrixGame.solve(play)
        var v = 0.0
        for (i in real.indices) for (j in real[i].indices) v += sol.row[i] * sol.col[j] * real[i][j]
        return v
    }

    private fun realDefenderMatrix(a: Int, b: Int): Array<DoubleArray> {
        val ours = bits(a)
        val theirs = bits(b)
        return Array(ours.size) { i -> DoubleArray(theirs.size) { j -> realAttackersValue(a, b, ours[i], theirs[j]) } }
    }

    private fun realAttackersValue(a: Int, b: Int, d: Int, e: Int): Double =
        mixed(attackersMatrix(a, b, d, e, Mode.NASH), realAttackersMatrix(a, b, d, e))

    private fun realAttackersMatrix(a: Int, b: Int, d: Int, e: Int): Array<DoubleArray> {
        val ourPairs = pairs(a and (1 shl d).inv())
        val theirPairs = pairs(b and (1 shl e).inv())
        return Array(ourPairs.size) { i ->
            DoubleArray(theirPairs.size) { j -> realChoiceValue(a, b, d, e, ourPairs[i], theirPairs[j]) }
        }
    }

    private fun realChoiceValue(a: Int, b: Int, d: Int, e: Int, p: IntArray, q: IntArray): Double =
        mixed(choiceMatrix(a, b, d, e, p, q, Mode.NASH), realChoiceMatrix(a, b, d, e, p, q))

    private fun choiceMatrix(a: Int, b: Int, d: Int, e: Int, p: IntArray, q: IntArray, mode: Mode) =
        Array(2) { i -> DoubleArray(2) { j -> choiceOutcome(a, b, d, e, p, q, i, j, mode) } }

    private fun realChoiceMatrix(a: Int, b: Int, d: Int, e: Int, p: IntArray, q: IntArray) =
        Array(2) { i -> DoubleArray(2) { j -> realChoiceOutcome(a, b, d, e, p, q, i, j) } }

    private fun realChoiceOutcome(a: Int, b: Int, d: Int, e: Int, p: IntArray, q: IntArray, i: Int, j: Int): Double {
        var v = r[d][q[i]] + r[p[j]][e]
        val restA = a and (1 shl d).inv() and (1 shl p[j]).inv()
        val restB = b and (1 shl e).inv() and (1 shl q[i]).inv()
        if (Integer.bitCount(a) == 4) {
            val refusedA = p[1 - j]
            val refusedB = q[1 - i]
            v += r[refusedA][refusedB]
            v += r[lowest(restA and (1 shl refusedA).inv())][lowest(restB and (1 shl refusedB).inv())]
        } else {
            v += expectedValue(restA, restB)
        }
        return v
    }

    // ------------------------------------------------------ analyses d'étape

    /** Défenseurs : nos options (joueur) et les leurs. */
    fun analyzeDefenders(a: Int, b: Int): StepAnalysis<Int, Int> {
        val ours = bits(a).toList()
        val theirs = bits(b).toList()
        val nash = defenderMatrix(a, b, Mode.NASH)
        return analyze(ours, theirs, nash, defenderMatrix(a, b, Mode.SECURE), if (weighted) realDefenderMatrix(a, b) else nash)
    }

    /** Attaquants (défenseurs connus) : paires de joueurs des deux côtés. */
    fun analyzeAttackers(a: Int, b: Int, d: Int, e: Int): StepAnalysis<List<Int>, List<Int>> {
        val ours = pairs(a and (1 shl d).inv()).map { it.toList() }
        val theirs = pairs(b and (1 shl e).inv()).map { it.toList() }
        val nash = attackersMatrix(a, b, d, e, Mode.NASH)
        return analyze(ours, theirs, nash, attackersMatrix(a, b, d, e, Mode.SECURE), if (weighted) realAttackersMatrix(a, b, d, e) else nash)
    }

    /**
     * Choix des défenseurs : nos options = l'attaquant adverse que prend notre
     * défenseur ; les leurs = notre attaquant que prend leur défenseur.
     */
    fun analyzeChoice(a: Int, b: Int, d: Int, e: Int, p: List<Int>, q: List<Int>): StepAnalysis<Int, Int> {
        val pa = p.toIntArray()
        val qa = q.toIntArray()
        val nash = choiceMatrix(a, b, d, e, pa, qa, Mode.NASH)
        return analyze(q, p, nash, choiceMatrix(a, b, d, e, pa, qa, Mode.SECURE), if (weighted) realChoiceMatrix(a, b, d, e, pa, qa) else nash)
    }

    private fun <O, T> analyze(
        ours: List<O>, theirs: List<T>, nash: Array<DoubleArray>, secure: Array<DoubleArray>, real: Array<DoubleArray>
    ): StepAnalysis<O, T> {
        // Stratégies : matrice de jeu [nash] ; gains affichés : vrais points [real]
        val sol = MatrixGame.solve(nash)
        val ourEvals = ours.mapIndexed { i, o ->
            var exp = 0.0
            for (j in theirs.indices) exp += real[i][j] * sol.col[j]
            var floor = Double.POSITIVE_INFINITY
            for (j in theirs.indices) floor = min(floor, secure[i][j])
            OptionEval(o, sol.row[i], exp, floor)
        }
        val theirEvals = theirs.mapIndexed { j, t ->
            var exp = 0.0
            for (i in ours.indices) exp += sol.row[i] * real[i][j]
            var best = Double.NEGATIVE_INFINITY
            for (i in ours.indices) best = maxOf(best, secure[i][j])
            OptionEval(t, sol.col[j], exp, best)
        }
        var value = 0.0
        for (i in ours.indices) for (j in theirs.indices) value += sol.row[i] * sol.col[j] * real[i][j]
        return StepAnalysis(value, MatrixGame.maximin(secure), ourEvals, theirEvals, real)
    }

    // ------------------------------------------------- matchs probables

    /*
     * Pour expliquer un choix : quels matchs se forment dans la manche en cours
     * si on joue cette option, l'adversaire (qui ne la connaît pas) et les
     * étapes suivantes jouant l'équilibre. Probabilités par match.
     */

    /** Matchs de la manche si notre défenseur est [d]. */
    fun outlookDefender(a: Int, b: Int, d: Int): List<RoundMatch> {
        val acc = HashMap<Long, Double>()
        val theirs = bits(b)
        val sol = MatrixGame.solve(defenderMatrix(a, b, Mode.NASH))
        for (ei in theirs.indices) if (sol.col[ei] > 1e-9) attackersDist(a, b, d, theirs[ei], null, sol.col[ei], acc)
        return toMatches(acc)
    }

    /** Matchs de la manche si nos attaquants sont [pair] (défenseurs [d] / [e] connus). */
    fun outlookAttackers(a: Int, b: Int, d: Int, e: Int, pair: List<Int>): List<RoundMatch> {
        val acc = HashMap<Long, Double>()
        attackersDist(a, b, d, e, pair.sorted().toIntArray(), 1.0, acc)
        return toMatches(acc)
    }

    /** Matchs de la manche si notre défenseur prend leur attaquant [pick]. */
    fun outlookChoice(a: Int, b: Int, d: Int, e: Int, p: List<Int>, q: List<Int>, pick: Int): List<RoundMatch> {
        val acc = HashMap<Long, Double>()
        choiceDist(a, b, d, e, p.toIntArray(), q.toIntArray(), q.indexOf(pick), 1.0, acc)
        return toMatches(acc)
    }

    private fun attackersDist(a: Int, b: Int, d: Int, e: Int, ourPair: IntArray?, w: Double, acc: HashMap<Long, Double>) {
        val ourPairs = pairs(a and (1 shl d).inv())
        val theirPairs = pairs(b and (1 shl e).inv())
        val sol = MatrixGame.solve(attackersMatrix(a, b, d, e, Mode.NASH))
        for (pi in ourPairs.indices) {
            val pr = if (ourPair == null) sol.row[pi] else if (ourPairs[pi].contentEquals(ourPair)) 1.0 else 0.0
            if (pr < 1e-9) continue
            for (qi in theirPairs.indices) {
                val pc = w * pr * sol.col[qi]
                if (pc > 1e-9) choiceDist(a, b, d, e, ourPairs[pi], theirPairs[qi], -1, pc, acc)
            }
        }
    }

    /** [ourPick] : index dans [q] de l'attaquant pris par notre défenseur, −1 = à l'équilibre. */
    private fun choiceDist(
        a: Int, b: Int, d: Int, e: Int, p: IntArray, q: IntArray, ourPick: Int, w: Double, acc: HashMap<Long, Double>
    ) {
        val sol = MatrixGame.solve(Array(2) { i -> DoubleArray(2) { j -> choiceOutcome(a, b, d, e, p, q, i, j, Mode.NASH) } })
        val row = if (ourPick < 0) sol.row else DoubleArray(2) { if (it == ourPick) 1.0 else 0.0 }
        val last = Integer.bitCount(a) == 4
        for (i in 0..1) for (j in 0..1) {
            val pc = w * row[i] * sol.col[j]
            if (pc < 1e-9) continue
            addMatch(acc, d, q[i], pc)
            addMatch(acc, p[j], e, pc)
            if (last) {
                val ra = p[1 - j]
                val rb = q[1 - i]
                addMatch(acc, ra, rb, pc)
                val restA = a and (1 shl d).inv() and (1 shl p[j]).inv() and (1 shl ra).inv()
                val restB = b and (1 shl e).inv() and (1 shl q[i]).inv() and (1 shl rb).inv()
                addMatch(acc, lowest(restA), lowest(restB), pc)
            }
        }
    }

    private fun addMatch(acc: HashMap<Long, Double>, our: Int, their: Int, p: Double) {
        val k = key(our, their)
        acc[k] = (acc[k] ?: 0.0) + p
    }

    private fun toMatches(acc: HashMap<Long, Double>) =
        acc.map { (k, p) -> RoundMatch((k shr 32).toInt(), k.toInt(), p) }.sortedByDescending { it.probability }

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

/**
 * [payoff][i][j] = nos points sur les matchs restants si on joue ours[i] et
 * eux theirs[j] (suite jouée à l'équilibre) : sert à expliquer les choix.
 */
class StepAnalysis<O, T>(
    val value: Double,
    val secureValue: Double,
    val ours: List<OptionEval<O>>,
    val theirs: List<OptionEval<T>>,
    val payoff: Array<DoubleArray>
)

/** Un match qui se forme dans la manche, avec sa probabilité. */
class RoundMatch(val our: Int, val their: Int, val probability: Double)
