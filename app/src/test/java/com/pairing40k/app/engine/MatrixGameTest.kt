package com.pairing40k.app.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MatrixGameTest {

    private fun m(vararg rows: DoubleArray) = arrayOf(*rows)
    private fun r(vararg v: Double) = v

    @Test
    fun pointSelle() {
        val sol = MatrixGame.solve(m(r(3.0, 5.0), r(1.0, 2.0)))
        assertEquals(3.0, sol.value, 1e-9)
        assertArrayEquals(doubleArrayOf(1.0, 0.0), sol.row, 1e-9)
        assertArrayEquals(doubleArrayOf(1.0, 0.0), sol.col, 1e-9)
    }

    @Test
    fun pileOuFace() {
        val sol = MatrixGame.solve(m(r(1.0, -1.0), r(-1.0, 1.0)))
        assertEquals(0.0, sol.value, 1e-9)
        assertArrayEquals(doubleArrayOf(0.5, 0.5), sol.row, 1e-9)
        assertEquals(0.0, MatrixGame.value2x2(1.0, -1.0, -1.0, 1.0), 1e-9)
    }

    @Test
    fun pierreFeuilleCiseaux() {
        val sol = MatrixGame.solve(m(r(0.0, -1.0, 1.0), r(1.0, 0.0, -1.0), r(-1.0, 1.0, 0.0)))
        assertEquals(0.0, sol.value, 1e-9)
        for (p in sol.row) assertEquals(1.0 / 3, p, 1e-9)
        for (p in sol.col) assertEquals(1.0 / 3, p, 1e-9)
    }

    @Test
    fun jeuNonCarre() {
        // Valeur 1.4 (vérifiée à la main : x = (0.4, 0.6) sur les lignes, mélange de y)
        val g = m(r(2.0, -1.0, 3.0), r(1.0, 3.0, 2.0))
        val sol = MatrixGame.solve(g)
        // La stratégie ligne garantit la valeur contre chaque colonne…
        for (j in 0..2) {
            val v = sol.row[0] * g[0][j] + sol.row[1] * g[1][j]
            assert(v >= sol.value - 1e-9)
        }
        // …et la stratégie colonne la plafonne contre chaque ligne
        for (i in 0..1) {
            val v = (0..2).sumOf { sol.col[it] * g[i][it] }
            assert(v <= sol.value + 1e-9)
        }
        assertEquals(1.4, sol.value, 1e-9)
    }

    @Test
    fun grandeMatriceAleatoireCoherente() {
        val rnd = java.util.Random(7)
        repeat(20) {
            val g = Array(9) { DoubleArray(12) { rnd.nextDouble() * 20 } }
            val sol = MatrixGame.solve(g)
            for (j in 0 until 12) assert((0 until 9).sumOf { sol.row[it] * g[it][j] } >= sol.value - 1e-7)
            for (i in 0 until 9) assert((0 until 12).sumOf { sol.col[it] * g[i][it] } <= sol.value + 1e-7)
        }
    }
}
