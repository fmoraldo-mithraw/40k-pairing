package com.pairing40k.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Sérialisation JSON de toutes les données (matrices, historique). Sert à la
 * sauvegarde locale et à l'export / import d'une sauvegarde complète à
 * partager entre coéquipiers.
 */
object Store {

    fun toJson(data: AppData): String = JSONObject().apply {
        put("version", 1)
        put("activeId", data.activeId ?: JSONObject.NULL)
        put("matchups", JSONArray(data.matchups.map(::matchupJson)))
        put("history", JSONArray(data.history.map(::historyJson)))
        put("settings", JSONObject().apply {
            put("defaultTeam", data.settings.defaultTeam)
            put("autoStart", data.settings.autoStart)
            put("startRow", data.settings.startRow)
            put("startCol", data.settings.startCol)
        })
    }.toString()

    fun fromJson(text: String): AppData {
        val o = JSONObject(text)
        val matchups = o.optJSONArray("matchups")?.let { a -> List(a.length()) { parseMatchup(a.getJSONObject(it)) } }
            ?.filter { it.size in setOf(4, 6, 8) }.orEmpty()
        val history = o.optJSONArray("history")?.let { a -> List(a.length()) { parseHistory(a.getJSONObject(it)) } }.orEmpty()
        return AppData(
            matchups = matchups.ifEmpty { listOf(Matchup.blank(8)) },
            activeId = if (o.isNull("activeId")) null else o.optString("activeId"),
            history = history,
            settings = o.optJSONObject("settings")?.let { st ->
                Settings(
                    defaultTeam = st.optString("defaultTeam", "Nous"),
                    autoStart = st.optBoolean("autoStart", true),
                    startRow = st.optInt("startRow", 1).coerceAtLeast(1),
                    startCol = st.optInt("startCol", 1).coerceAtLeast(1)
                )
            } ?: Settings()
        )
    }

    fun matchupJson(m: Matchup) = JSONObject().apply {
        put("id", m.id)
        put("ourTeam", m.ourTeam)
        put("theirTeam", m.theirTeam)
        put("ourPlayers", JSONArray(m.ourPlayers.map(::playerJson)))
        put("theirPlayers", JSONArray(m.theirPlayers.map(::playerJson)))
        put("scores", JSONArray(m.scores.map { JSONArray(it) }))
        put("maxScore", m.maxScore)
        put("notes", m.notes)
    }

    fun parseMatchup(o: JSONObject): Matchup {
        val scores = o.getJSONArray("scores")
        return Matchup(
            id = o.getString("id"),
            ourTeam = o.optString("ourTeam", "Nous"),
            theirTeam = o.optString("theirTeam", "Adversaire"),
            ourPlayers = players(o.getJSONArray("ourPlayers")),
            theirPlayers = players(o.getJSONArray("theirPlayers")),
            scores = List(scores.length()) { i ->
                val r = scores.getJSONArray(i)
                List(r.length()) { r.getDouble(it) }
            },
            maxScore = o.optDouble("maxScore", 20.0),
            notes = o.optString("notes", "")
        )
    }

    private fun playerJson(p: Player) = JSONObject().put("name", p.name).put("faction", p.faction)

    private fun players(a: JSONArray) = List(a.length()) {
        val p = a.getJSONObject(it)
        Player(p.getString("name"), p.optString("faction", ""))
    }

    private fun historyJson(h: HistoryEntry) = JSONObject().apply {
        put("id", h.id)
        put("date", h.date)
        put("ourTeam", h.ourTeam)
        put("theirTeam", h.theirTeam)
        put("maxScore", h.maxScore)
        put("expectedBefore", h.expectedBefore)
        put("games", JSONArray(h.games.map { g ->
            JSONObject().apply {
                put("our", g.ourPlayer)
                put("their", g.theirPlayer)
                put("kind", g.kind)
                put("estimate", g.estimate)
                put("actual", g.actual ?: JSONObject.NULL)
            }
        }))
    }

    private fun parseHistory(o: JSONObject): HistoryEntry {
        val games = o.getJSONArray("games")
        return HistoryEntry(
            id = o.getString("id"),
            date = o.getLong("date"),
            ourTeam = o.optString("ourTeam"),
            theirTeam = o.optString("theirTeam"),
            maxScore = o.optDouble("maxScore", 20.0),
            expectedBefore = o.optDouble("expectedBefore", 0.0),
            games = List(games.length()) { i ->
                val g = games.getJSONObject(i)
                PlayedGame(
                    g.getString("our"), g.getString("their"), g.optString("kind"),
                    g.getDouble("estimate"), if (g.isNull("actual")) null else g.getDouble("actual")
                )
            }
        )
    }
}
