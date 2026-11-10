package com.pairing40k.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Matchs probables d'une manche, utilisés pour expliquer les conseils. */
class OutlookTest {

    private fun solver(n: Int, seed: Int): PairingSolver {
        val rnd = Random(seed)
        return PairingSolver(List(n) { List(n) { rnd.nextInt(21).toDouble() } })
    }

    @Test
    fun deuxMatchsParMancheEtQuatreALaDerniere() {
        for (n in listOf(4, 6, 8)) {
            val s = solver(n, n)
            val full = s.fullMask
            val perRound = if (n == 4) 4.0 else 2.0
            val o = s.outlookDefender(full, full, 0)
            assertEquals(perRound, o.sumOf { it.probability }, 1e-6)
            // Notre défenseur joue forcément un match dans la manche
            assertEquals(1.0, o.filter { it.our == 0 }.sumOf { it.probability }, 1e-6)
        }
    }

    @Test
    fun choixFixeEtAttaquantsFixes() {
        val s = solver(8, 3)
        val full = s.fullMask
        val att = s.outlookAttackers(full, full, 0, 0, listOf(2, 1))
        assertEquals(2.0, att.sumOf { it.probability }, 1e-6)
        // Leur défenseur 0 joue l'un de nos attaquants 1 ou 2
        assertEquals(1.0, att.filter { it.their == 0 }.sumOf { it.probability }, 1e-6)
        assertTrue(att.filter { it.their == 0 }.all { it.our in setOf(1, 2) })

        val ch = s.outlookChoice(full, full, 0, 0, listOf(1, 2), listOf(3, 4), 4)
        assertEquals(1.0, ch.first { it.our == 0 && it.their == 4 }.probability, 1e-9)
        assertEquals(1.0, ch.filter { it.their == 0 }.sumOf { it.probability }, 1e-6)
    }

    @Test
    fun matriceDesIssuesCoherente() {
        val s = solver(6, 7)
        val an = s.analyzeDefenders(s.fullMask, s.fullMask)
        assertEquals(an.ours.size, an.payoff.size)
        an.ours.forEachIndexed { i, o ->
            val exp = an.theirs.indices.sumOf { j -> an.payoff[i][j] * an.theirs[j].probability }
            assertEquals(o.expected, exp, 1e-9)
        }
    }
}
