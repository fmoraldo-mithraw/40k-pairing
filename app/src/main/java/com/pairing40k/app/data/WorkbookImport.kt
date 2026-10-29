package com.pairing40k.app.data

/**
 * Import d'un classeur entier (Excel, Google Sheets) ou d'un CSV : chaque
 * onglet lisible devient une matrice, nommée d'après l'onglet (le nom de
 * l'équipe adverse). Ré-importer le même fichier met à jour les matrices
 * existantes au lieu de les dupliquer.
 */
object WorkbookImport {

    class Sheet(val name: String?, val rows: List<List<String>>)

    /** Lecture d'un onglet : [team] est le nom retenu pour l'équipe adverse. */
    class SheetResult(
        val sheet: Sheet,
        val team: String,
        val startRow: Int,
        val startCol: Int,
        val result: MatrixImport.Result?,
        val error: String?
    ) {
        val ok: Boolean get() = result != null
        val startLabel: String get() = MatrixImport.columnLabel(startCol) + startRow
    }

    class Merge(val data: AppData, val created: Int, val updated: Int)

    fun analyze(
        sheets: List<Sheet>, settings: Settings, scale: MatrixImport.Scale, maxScore: Double, fallbackName: String
    ): List<SheetResult> = sheets.map { sheet ->
        val (row, col) = if (settings.autoStart) MatrixImport.detectStart(sheet.rows) else settings.startRow to settings.startCol
        try {
            val r = MatrixImport.parseTable(MatrixImport.crop(sheet.rows, row, col), maxScore, scale)
            val team = sheet.name?.trim()?.takeIf { it.isNotEmpty() } ?: r.cornerName ?: fallbackName
            val error = when {
                // Ex. un tableau de bord « Estimé / Réel / Diff » répété : pas une matrice
                r.theirPlayers.map { it.name.lowercase() }.distinct().size < r.size ||
                    r.ourPlayers.map { it.name.lowercase() }.distinct().size < r.size -> "noms de joueurs en double : pas une matrice"
                else -> null
            }
            SheetResult(sheet, team, row, col, if (error == null) r else null, error)
        } catch (e: MatrixImport.ImportException) {
            SheetResult(sheet, sheet.name ?: fallbackName, row, col, null, e.message)
        }
    }

    /** Crée ou met à jour (même nom d'équipe) une matrice par onglet lisible. */
    fun merge(data: AppData, results: List<SheetResult>): Merge {
        val matchups = data.matchups.toMutableList()
        // La matrice vierge du premier lancement ne sert plus à rien
        matchups.removeAll { it.isPristine() && results.any { r -> r.ok } }
        var created = 0
        var updated = 0
        var firstId: String? = null
        for (r in results) {
            val res = r.result ?: continue
            val n = if (res.size in setOf(4, 6, 8)) res.size else listOf(8, 6, 4).first { it <= res.size }
            fun <T> cut(l: List<T>) = l.take(n)
            val idx = matchups.indexOfFirst { it.theirTeam.trim().equals(r.team.trim(), ignoreCase = true) }
            val m = if (idx >= 0) matchups[idx] else Matchup.blank(n, data.settings.defaultTeam)
            val next = m.copy(
                theirTeam = r.team,
                ourPlayers = cut(res.ourPlayers),
                theirPlayers = cut(res.theirPlayers),
                scores = cut(res.scores).map { cut(it) }
            )
            if (idx >= 0) { matchups[idx] = next; updated++ } else { matchups += next; created++ }
            if (firstId == null) firstId = next.id
        }
        if (matchups.isEmpty()) matchups += Matchup.blank(8, data.settings.defaultTeam)
        return Merge(data.copy(matchups = matchups, activeId = firstId ?: data.activeId), created, updated)
    }

    private fun Matchup.isPristine() =
        theirTeam == "Adversaire" && notes.isEmpty() && scores.all { row -> row.all { it == maxScore / 2 } }
}
