package com.pairing40k.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Marqueurs : stratégies sur une matrice pondérée, valeurs en vrais points. */
class ScorersTest {

    private fun weighted(sc: List<List<Double>>, rows: Set<Int>, k: Double) =
        sc.mapIndexed { i, r -> if (i in rows) r.map { 10 + k * (it - 10) } else r }

    @Test
    fun sansPonderationIdentique() {
        val rnd = Random(3)
        val sc = List(6) { List(6) { rnd.nextInt(21).toDouble() } }
        val s = PairingSolver(sc, 20.0, sc)
        assertFalse(s.weighted)
        assertEquals(s.stateValue(s.fullMask, s.fullMask), s.expectedValue(s.fullMask, s.fullMask), 1e-9)
    }

    @Test
    fun valeursEnVraisPointsCoherentes() {
        val rnd = Random(11)
        repeat(3) {
            val sc = List(6) { List(6) { rnd.nextInt(21).toDouble() } }
            val s = PairingSolver(sc, 20.0, weighted(sc, setOf(0, 2), 2.0))
            assertTrue(s.weighted)
            val full = s.fullMask
            val v = s.expectedValue(full, full)
            // L'analyse d'étape et les probabilités d'affiches donnent la même espérance réelle
            assertEquals(v, s.analyzeDefenders(full, full).value, 1e-6)
            val p = s.matchProbabilities()
            assertEquals(v, (0 until 6).sumOf { i -> (0 until 6).sumOf { j -> p[i][j] * sc[i][j] } }, 1e-6)
            assertTrue(v in 0.0..120.0)
        }
    }
}
