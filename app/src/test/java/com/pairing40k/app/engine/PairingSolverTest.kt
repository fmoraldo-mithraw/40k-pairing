package com.pairing40k.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingSolverTest {

    private fun random(n: Int, seed: Long): List<List<Double>> {
        val rnd = java.util.Random(seed)
        return List(n) { List(n) { (rnd.nextInt(21)).toDouble() } }
    }

    @Test
    fun matriceConstante() {
        val solver = PairingSolver(List(8) { List(8) { 10.0 } })
        assertEquals(80.0, solver.stateValue(solver.fullMask, solver.fullMask), 1e-9)
    }

    @Test
    fun scoreQueDeNotreJoueur() {
        // Si le score ne dépend que de notre joueur, le pairing ne change rien
        val c = listOf(3.0, 7.0, 12.0, 18.0, 5.0, 9.0)
        val solver = PairingSolver(List(6) { i -> List(6) { c[i] } })
        assertEquals(c.sum(), solver.stateValue(solver.fullMask, solver.fullMask), 1e-9)
        assertEquals(c.sum(), solver.stateValue(solver.fullMask, solver.fullMask, PairingSolver.Mode.SECURE), 1e-9)
    }

    @Test
    fun encadrementParLesBornes() {
        for (n in listOf(4, 6, 8)) for (seed in 1L..3L) {
            val sc = random(n, seed)
            val solver = PairingSolver(sc)
            val nash = solver.stateValue(solver.fullMask, solver.fullMask)
            val secure = solver.stateValue(solver.fullMask, solver.fullMask, PairingSolver.Mode.SECURE)
            assertTrue(secure <= nash + 1e-9)
            assertTrue(nash <= Assignment.best(sc).total + 1e-9)
            assertTrue(nash >= Assignment.worst(sc).total - 1e-9)
        }
    }

    @Test
    fun sommeNulleVueAdverse() {
        // Résoudre du point de vue adverse (transposée, 20 − s) donne le complément
        for (n in listOf(4, 6, 8)) {
            val sc = random(n, 42L + n)
            val theirs = List(n) { j -> List(n) { i -> 20.0 - sc[i][j] } }
            val ours = PairingSolver(sc)
            val opp = PairingSolver(theirs)
            assertEquals(
                20.0 * n,
                ours.stateValue(ours.fullMask, ours.fullMask) + opp.stateValue(opp.fullMask, opp.fullMask),
                1e-6
            )
        }
    }

    @Test
    fun probabilitesDesAffichesCoherentes() {
        for (n in listOf(4, 6, 8)) {
            val sc = random(n, 100L + n)
            val solver = PairingSolver(sc)
            val prob = solver.matchProbabilities()
            for (i in 0 until n) assertEquals(1.0, prob[i].sum(), 1e-6)
            for (j in 0 until n) assertEquals(1.0, (0 until n).sumOf { prob[it][j] }, 1e-6)
            // L'espérance des affiches redonne la valeur d'équilibre
            var expected = 0.0
            for (i in 0 until n) for (j in 0 until n) expected += prob[i][j] * sc[i][j]
            assertEquals(solver.stateValue(solver.fullMask, solver.fullMask), expected, 1e-6)
        }
    }

    @Test
    fun analysesDEtapeCoherentes() {
        val sc = random(8, 5L)
        val solver = PairingSolver(sc)
        val full = solver.fullMask
        val def = solver.analyzeDefenders(full, full)
        assertEquals(solver.stateValue(full, full), def.value, 1e-9)
        assertEquals(1.0, def.ours.sumOf { it.probability }, 1e-9)
        val att = solver.analyzeAttackers(full, full, 0, 0)
        assertEquals(21, att.ours.size)
        val choice = solver.analyzeChoice(full, full, 0, 0, att.ours[0].option, att.theirs[0].option)
        assertEquals(2, choice.ours.size)
    }

    @Test
    fun huitContreHuitRapide() {
        val t0 = System.nanoTime()
        val solver = PairingSolver(random(8, 9L))
        solver.stateValue(solver.fullMask, solver.fullMask)
        solver.stateValue(solver.fullMask, solver.fullMask, PairingSolver.Mode.SECURE)
        solver.matchProbabilities()
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("8v8 résolu en $ms ms")
        assertTrue(ms < 20_000)
    }

    @Test
    fun sessionCompleteHuitJoueurs() {
        var st = PairingState.start(8)
        st = st.withDefenders(0, 0).withAttackers(listOf(1, 2), listOf(1, 2)).withChoices(1, 2)
        assertEquals(2, st.round)
        assertEquals(6, st.remaining)
        st = st.withDefenders(1, 2).withAttackers(listOf(3, 4), listOf(3, 4)).withChoices(3, 4)
        st = st.withDefenders(3, 4).withAttackers(listOf(5, 6), listOf(5, 6)).withChoices(5, 6)
        assertEquals(Phase.DONE, st.phase)
        assertEquals(8, st.games.size)
        assertEquals((0..7).toSet(), st.games.map { it.our }.toSet())
        assertEquals((0..7).toSet(), st.games.map { it.their }.toSet())
        assertEquals(GameKind.FORGOTTEN, st.games.last().kind)
        assertEquals(7, st.games.last().our)
        assertEquals(7, st.games.last().their)
    }
}
