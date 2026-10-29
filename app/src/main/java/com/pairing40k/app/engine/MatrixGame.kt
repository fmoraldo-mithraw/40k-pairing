package com.pairing40k.app.engine

import kotlin.math.max
import kotlin.math.min

/**
 * Solution d'un jeu matriciel à somme nulle : [value] est le gain espéré du
 * joueur « ligne » (nous) quand les deux camps jouent leur stratégie mixte
 * optimale [row] / [col] (probabilités de chaque option).
 */
class GameSolution(val value: Double, val row: DoubleArray, val col: DoubleArray)

/**
 * Les étapes du pairing sont des choix simultanés (cartes posées face cachée,
 * révélées ensemble) : la bonne réponse n'est pas toujours un choix pur mais un
 * mélange. On résout donc chaque étape comme un jeu à somme nulle.
 */
object MatrixGame {
    private const val EPS = 1e-9

    fun solve(m: Array<DoubleArray>): GameSolution {
        val rows = m.size
        val cols = m[0].size

        // Point selle : un choix pur est optimal pour les deux camps
        var maximin = Double.NEGATIVE_INFINITY
        var bi = 0
        for (i in 0 until rows) {
            var mn = Double.POSITIVE_INFINITY
            for (j in 0 until cols) mn = min(mn, m[i][j])
            if (mn > maximin) { maximin = mn; bi = i }
        }
        var minimax = Double.POSITIVE_INFINITY
        var bj = 0
        for (j in 0 until cols) {
            var mx = Double.NEGATIVE_INFINITY
            for (i in 0 until rows) mx = max(mx, m[i][j])
            if (mx < minimax) { minimax = mx; bj = j }
        }
        if (maximin >= minimax - EPS) {
            return GameSolution(maximin, oneHot(rows, bi), oneHot(cols, bj))
        }
        if (rows == 2 && cols == 2) {
            val a = m[0][0]; val b = m[0][1]; val c = m[1][0]; val d = m[1][1]
            val den = a - b - c + d
            val p = (d - c) / den
            val q = (d - b) / den
            return GameSolution(
                (a * d - b * c) / den,
                doubleArrayOf(p, 1 - p),
                doubleArrayOf(q, 1 - q)
            )
        }
        return simplex(m)
    }

    /** Valeur seule d'un 2×2 — le cas le plus fréquent, sans allocation. */
    fun value2x2(a: Double, b: Double, c: Double, d: Double): Double {
        val maximin = max(min(a, b), min(c, d))
        val minimax = min(max(a, c), max(b, d))
        if (maximin >= minimax - EPS) return maximin
        return (a * d - b * c) / (a - b - c + d)
    }

    /** Ce qu'on s'assure en choix pur, même si l'adversaire devine notre carte. */
    fun maximin(m: Array<DoubleArray>): Double {
        var best = Double.NEGATIVE_INFINITY
        for (r in m) {
            var mn = Double.POSITIVE_INFINITY
            for (v in r) mn = min(mn, v)
            best = max(best, mn)
        }
        return best
    }

    fun maximin2x2(a: Double, b: Double, c: Double, d: Double): Double =
        max(min(a, b), min(c, d))

    private fun oneHot(n: Int, k: Int) = DoubleArray(n).also { it[k] = 1.0 }

    /**
     * Programme linéaire classique : avec une matrice décalée strictement
     * positive, l'adversaire maximise Σy sous M·y ≤ 1. Le tableau final donne
     * sa stratégie (variables de base) et la nôtre (coûts réduits des écarts).
     * Règle de Bland : pas de cyclage, les matrices sont petites (≤ 21×21).
     */
    private fun simplex(m: Array<DoubleArray>): GameSolution {
        val r = m.size
        val c = m[0].size
        var lo = Double.POSITIVE_INFINITY
        for (row in m) for (v in row) lo = min(lo, v)
        val shift = 1.0 - lo
        val w = c + r + 1
        val t = Array(r + 1) { DoubleArray(w) }
        for (i in 0 until r) {
            for (j in 0 until c) t[i][j] = m[i][j] + shift
            t[i][c + i] = 1.0
            t[i][w - 1] = 1.0
        }
        for (j in 0 until c) t[r][j] = -1.0
        val basis = IntArray(r) { c + it }

        var guard = 0
        while (guard++ < 10_000) {
            var pc = -1
            for (j in 0 until w - 1) if (t[r][j] < -EPS) { pc = j; break }
            if (pc < 0) break
            var pr = -1
            var best = Double.POSITIVE_INFINITY
            for (i in 0 until r) {
                if (t[i][pc] > EPS) {
                    val ratio = t[i][w - 1] / t[i][pc]
                    if (ratio < best - EPS || (ratio < best + EPS && pr >= 0 && basis[i] < basis[pr])) {
                        best = ratio; pr = i
                    }
                }
            }
            if (pr < 0) break
            val pivot = t[pr][pc]
            for (j in 0 until w) t[pr][j] /= pivot
            for (k in 0..r) {
                if (k == pr) continue
                val f = t[k][pc]
                if (f != 0.0) for (j in 0 until w) t[k][j] -= f * t[pr][j]
            }
            basis[pr] = pc
        }

        val z = t[r][w - 1]
        val col = DoubleArray(c)
        for (i in 0 until r) if (basis[i] < c) col[basis[i]] = t[i][w - 1] / z
        val row = DoubleArray(r) { t[r][c + it] / z }
        return GameSolution(1.0 / z - shift, normalize(row), normalize(col))
    }

    private fun normalize(p: DoubleArray): DoubleArray {
        for (i in p.indices) if (p[i] < 0) p[i] = 0.0
        val s = p.sum()
        if (s > 0) for (i in p.indices) p[i] /= s
        return p
    }
}
