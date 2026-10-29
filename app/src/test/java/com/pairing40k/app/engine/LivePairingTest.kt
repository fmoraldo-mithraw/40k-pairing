package com.pairing40k.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Déroulé complet d'un pairing en direct, comme l'écran Pairing : à chaque
 * étape on lit l'analyse, on tire nos cartes selon la stratégie conseillée et
 * celles de l'adversaire au hasard (il ne joue pas forcément optimal).
 */
class LivePairingTest {

    private fun <T> draw(options: List<OptionEval<T>>, rnd: Random): T {
        var r = rnd.nextDouble()
        for (o in options) { r -= o.probability; if (r <= 0) return o.option }
        return options.maxBy { it.probability }.option
    }

    private fun play(n: Int, seed: Int) {
        val rnd = Random(seed)
        val scores = List(n) { List(n) { rnd.nextInt(21).toDouble() } }
        val solver = PairingSolver(scores)
        var st = PairingState.start(n)
        var rounds = 0
        while (st.phase != Phase.DONE) {
            val def = solver.analyzeDefenders(st.ourLeft, st.theirLeft)
            // Projection affichée = valeur de l'état
            assertEquals(solver.stateValue(st.ourLeft, st.theirLeft), def.value, 1e-9)
            val theirDef = PairingSolver.bits(st.theirLeft).let { it[rnd.nextInt(it.size)] }
            st = st.withDefenders(draw(def.ours, rnd), theirDef)

            val att = solver.analyzeAttackers(st.ourLeft, st.theirLeft, st.ourDefender, st.theirDefender)
            assertEquals(1.0, att.ours.sumOf { it.probability }, 1e-6)
            val theirAtt = att.theirs[rnd.nextInt(att.theirs.size)].option
            st = st.withAttackers(draw(att.ours, rnd), theirAtt)

            val ch = solver.analyzeChoice(st.ourLeft, st.theirLeft, st.ourDefender, st.theirDefender, st.ourAttackers, st.theirAttackers)
            assertEquals(st.theirAttackers.toSet(), ch.ours.map { it.option }.toSet())
            assertEquals(st.ourAttackers.toSet(), ch.theirs.map { it.option }.toSet())
            st = st.withChoices(draw(ch.ours, rnd), st.ourAttackers[rnd.nextInt(2)])
            rounds++
        }
        assertEquals(n / 2 - 1, rounds)
        assertEquals(n, st.games.size)
        assertEquals((0 until n).toSet(), st.games.map { it.our }.toSet())
        assertEquals((0 until n).toSet(), st.games.map { it.their }.toSet())
        assertEquals(1, st.games.count { it.kind == GameKind.FORGOTTEN })
        assertEquals(1, st.games.count { it.kind == GameKind.REFUSED })
    }

    @Test
    fun pairingsComplets() {
        for (seed in 1..10) { play(6, seed); play(8, seed) }
    }

    @Test
    fun jouerLaStrategieNeFaitPasPerdreDePointsEnMoyenne() {
        // Contre un adversaire aléatoire, suivre les conseils rapporte au moins la valeur d'équilibre en moyenne
        val rnd = Random(3)
        val scores = List(6) { List(6) { rnd.nextInt(21).toDouble() } }
        val solver = PairingSolver(scores)
        val v = solver.stateValue(solver.fullMask, solver.fullMask)
        var total = 0.0
        val runs = 400
        repeat(runs) {
            var st = PairingState.start(6)
            while (st.phase != Phase.DONE) {
                val def = solver.analyzeDefenders(st.ourLeft, st.theirLeft)
                st = st.withDefenders(draw(def.ours, rnd), PairingSolver.bits(st.theirLeft).random(rnd))
                val att = solver.analyzeAttackers(st.ourLeft, st.theirLeft, st.ourDefender, st.theirDefender)
                st = st.withAttackers(draw(att.ours, rnd), att.theirs.random(rnd).option)
                val ch = solver.analyzeChoice(st.ourLeft, st.theirLeft, st.ourDefender, st.theirDefender, st.ourAttackers, st.theirAttackers)
                st = st.withChoices(draw(ch.ours, rnd), st.ourAttackers.random(rnd))
            }
            total += st.games.sumOf { scores[it.our][it.their] }
        }
        assertTrue("moyenne ${total / runs} < équilibre $v", total / runs >= v - 1.0)
    }
}
