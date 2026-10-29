package com.pairing40k.app.engine

/** Origine d'un match dans le pairing, pour l'affichage du récapitulatif. */
enum class GameKind(val label: String) {
    OUR_DEFENDER("Notre défenseur"),
    THEIR_DEFENDER("Leur défenseur"),
    REFUSED("Attaquants refusés"),
    FORGOTTEN("Oubliés")
}

data class Game(val our: Int, val their: Int, val kind: GameKind, val round: Int)

enum class Phase { DEFENDERS, ATTACKERS, CHOICES, DONE }

/**
 * État d'un pairing en cours (immuable : l'historique des états sert d'annuler).
 * Les masques [ourLeft] / [theirLeft] désignent les joueurs encore dans le pool.
 */
data class PairingState(
    val ourLeft: Int,
    val theirLeft: Int,
    val round: Int = 1,
    val phase: Phase = Phase.DEFENDERS,
    val ourDefender: Int = -1,
    val theirDefender: Int = -1,
    val ourAttackers: List<Int> = emptyList(),
    val theirAttackers: List<Int> = emptyList(),
    val games: List<Game> = emptyList()
) {
    val remaining: Int get() = Integer.bitCount(ourLeft)
    val isLastRound: Boolean get() = remaining == 4

    fun withDefenders(ours: Int, theirs: Int): PairingState {
        check(phase == Phase.DEFENDERS)
        require(ourLeft and (1 shl ours) != 0 && theirLeft and (1 shl theirs) != 0)
        return copy(phase = Phase.ATTACKERS, ourDefender = ours, theirDefender = theirs)
    }

    fun withAttackers(ours: List<Int>, theirs: List<Int>): PairingState {
        check(phase == Phase.ATTACKERS)
        require(ours.size == 2 && theirs.size == 2 && ours.distinct().size == 2 && theirs.distinct().size == 2)
        require(ours.none { it == ourDefender || ourLeft and (1 shl it) == 0 })
        require(theirs.none { it == theirDefender || theirLeft and (1 shl it) == 0 })
        return copy(phase = Phase.CHOICES, ourAttackers = ours.sorted(), theirAttackers = theirs.sorted())
    }

    /**
     * [ourPick] : l'attaquant adverse retenu par notre défenseur ;
     * [theirPick] : notre attaquant retenu par leur défenseur.
     */
    fun withChoices(ourPick: Int, theirPick: Int): PairingState {
        check(phase == Phase.CHOICES)
        require(ourPick in theirAttackers && theirPick in ourAttackers)
        val newGames = games.toMutableList()
        newGames += Game(ourDefender, ourPick, GameKind.OUR_DEFENDER, round)
        newGames += Game(theirPick, theirDefender, GameKind.THEIR_DEFENDER, round)
        var a = ourLeft and (1 shl ourDefender).inv() and (1 shl theirPick).inv()
        var b = theirLeft and (1 shl theirDefender).inv() and (1 shl ourPick).inv()
        if (isLastRound) {
            val refusedA = ourAttackers.first { it != theirPick }
            val refusedB = theirAttackers.first { it != ourPick }
            newGames += Game(refusedA, refusedB, GameKind.REFUSED, round)
            a = a and (1 shl refusedA).inv()
            b = b and (1 shl refusedB).inv()
            newGames += Game(PairingSolver.lowest(a), PairingSolver.lowest(b), GameKind.FORGOTTEN, round)
            return PairingState(0, 0, round, Phase.DONE, games = newGames)
        }
        return PairingState(a, b, round + 1, Phase.DEFENDERS, games = newGames)
    }

    companion object {
        fun start(n: Int) = PairingState((1 shl n) - 1, (1 shl n) - 1)
    }
}
