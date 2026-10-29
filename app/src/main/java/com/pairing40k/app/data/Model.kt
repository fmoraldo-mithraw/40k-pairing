package com.pairing40k.app.data

import java.util.UUID

data class Player(val name: String, val faction: String = "") {
    val label: String get() = if (faction.isBlank()) name else "$name ($faction)"
}

/**
 * Une confrontation préparée : nos joueurs, les leurs et la matrice des
 * scores estimés scores[nous][eux] (0 → [maxScore], 10/20 = égalité).
 */
data class Matchup(
    val id: String = UUID.randomUUID().toString(),
    val ourTeam: String = "Nous",
    val theirTeam: String = "Adversaire",
    val ourPlayers: List<Player>,
    val theirPlayers: List<Player>,
    val scores: List<List<Double>>,
    val maxScore: Double = 20.0,
    val notes: String = ""
) {
    val size: Int get() = ourPlayers.size
    val title: String get() = "$ourTeam vs $theirTeam"

    /** Change de format (4/6/8) en gardant ce qui peut l'être. */
    fun resized(n: Int): Matchup {
        val mid = maxScore / 2
        fun grow(p: List<Player>, prefix: String) =
            List(n) { p.getOrNull(it) ?: Player("$prefix ${it + 1}") }
        return copy(
            ourPlayers = grow(ourPlayers, "Joueur"),
            theirPlayers = grow(theirPlayers, "Adv."),
            scores = List(n) { i -> List(n) { j -> scores.getOrNull(i)?.getOrNull(j) ?: mid } }
        )
    }

    fun withScore(i: Int, j: Int, v: Double) = copy(
        scores = scores.mapIndexed { r, row -> if (r != i) row else row.mapIndexed { c, x -> if (c == j) v else x } }
    )

    companion object {
        fun blank(n: Int, ourTeam: String = "Nous", ourPlayers: List<Player> = emptyList()) =
            Matchup(ourTeam = ourTeam, ourPlayers = ourPlayers, theirPlayers = emptyList(), scores = emptyList())
                .resized(n)
    }
}

/** Un match d'un pairing joué, avec le résultat réel saisi après la partie. */
data class PlayedGame(
    val ourPlayer: String,
    val theirPlayer: String,
    val kind: String,
    val estimate: Double,
    val actual: Double? = null
)

data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val ourTeam: String,
    val theirTeam: String,
    val maxScore: Double,
    val expectedBefore: Double,
    val games: List<PlayedGame>
) {
    val estimateTotal: Double get() = games.sumOf { it.estimate }
    val actualTotal: Double? get() = if (games.all { it.actual != null }) games.sumOf { it.actual!! } else null
}

data class AppData(
    val matchups: List<Matchup> = listOf(Matchup.blank(8)),
    val activeId: String? = null,
    val history: List<HistoryEntry> = emptyList()
) {
    val active: Matchup get() = matchups.firstOrNull { it.id == activeId } ?: matchups.first()
}
