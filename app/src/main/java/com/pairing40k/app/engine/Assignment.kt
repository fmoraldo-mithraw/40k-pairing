package com.pairing40k.app.engine

/**
 * Affectation idéale (ou catastrophe) si l'on pouvait choisir librement toutes
 * les affiches : bornes théoriques qui encadrent ce que le pairing permet.
 * Programmation dynamique sur les masques : n ≤ 8, instantané.
 */
object Assignment {

    class Result(val total: Double, val opponentOf: IntArray)

    fun best(scores: List<List<Double>>): Result = solve(scores, maximize = true)

    fun worst(scores: List<List<Double>>): Result = solve(scores, maximize = false)

    private fun solve(scores: List<List<Double>>, maximize: Boolean): Result {
        val n = scores.size
        val size = 1 shl n
        val sign = if (maximize) 1.0 else -1.0
        val dp = DoubleArray(size) { Double.NEGATIVE_INFINITY }
        val choice = IntArray(size) { -1 }
        dp[0] = 0.0
        // dp[m] : meilleur total quand les joueurs adverses de m sont pris par
        // nos joueurs 0..popcount(m)-1
        for (m in 0 until size) {
            if (dp[m] == Double.NEGATIVE_INFINITY) continue
            val i = Integer.bitCount(m)
            if (i >= n) continue
            for (j in 0 until n) {
                if (m and (1 shl j) != 0) continue
                val nm = m or (1 shl j)
                val v = dp[m] + sign * scores[i][j]
                if (v > dp[nm]) { dp[nm] = v; choice[nm] = j }
            }
        }
        val opp = IntArray(n)
        var m = size - 1
        for (i in n - 1 downTo 0) {
            val j = choice[m]
            opp[i] = j
            m = m and (1 shl j).inv()
        }
        return Result(sign * dp[size - 1], opp)
    }
}
