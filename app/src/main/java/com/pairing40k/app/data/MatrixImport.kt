package com.pairing40k.app.data

import kotlin.math.abs
import kotlin.math.max

/**
 * Lecture d'une matrice de pairing depuis un tableau (CSV, Excel, copier-coller
 * Google Sheets). Deux dispositions reconnues :
 *  - grille : 1re ligne = joueurs adverses, 1re colonne = nos joueurs ;
 *    une 2e ligne / 2e colonne de texte est lue comme les factions ;
 *  - liste : une ligne par affiche « notre joueur ; joueur adverse ; score ».
 * Les noms « Jean (Orks) » donnent nom + faction.
 */
object MatrixImport {

    /** Échelles de notation courantes, ramenées sur 0 → maxScore. */
    enum class Scale(val label: String) {
        AUTO("Détection auto"),
        POINTS("Points (0 → max)"),
        FIVE("Note 1 → 5"),
        PLUS_MINUS("Relatif −2 → +2"),
        SYMBOLS("Symboles -- - = + ++"),
        PERCENT("Pourcentage 0 → 100")
    }

    class Result(
        val ourPlayers: List<Player>,
        val theirPlayers: List<Player>,
        val scores: List<List<Double>>,
        val scale: Scale,
        val layout: String,
        val warnings: List<String>,
        /** Texte de la case du coin (souvent le nom de l'équipe adverse). */
        val cornerName: String? = null
    ) {
        val size: Int get() = ourPlayers.size
    }

    class ImportException(message: String) : Exception(message)

    // --------------------------------------------------------------- CSV

    /** CSV/TSV avec guillemets ; séparateur deviné (« ; » d'Excel FR, « , », tab). */
    fun parseCsv(text: String): List<List<String>> {
        val clean = text.removePrefix("﻿")
        val firstLines = clean.lineSequence().take(5).joinToString("\n")
        val sep = listOf('\t', ';', ',').maxByOrNull { c -> firstLines.count { it == c } } ?: ','
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < clean.length) {
            val ch = clean[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < clean.length && clean[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(ch)
            } else when (ch) {
                '"' -> quoted = true
                sep -> { row.add(cell.toString()); cell.clear() }
                '\r' -> {}
                '\n' -> { row.add(cell.toString()); cell.clear(); rows.add(row); row = ArrayList() }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows.map { r -> r.map { it.trim() } }
    }

    // ----------------------------------------------------------- valeurs

    private val symbolRe = Regex("^[+\\-=]+$")
    private val scoreRe = Regex("^(\\d+(?:[.,]\\d+)?)\\s*[-/:]\\s*(\\d+(?:[.,]\\d+)?)$")

    /** Valeur brute lisible ? (nombre, « 13-7 », symbole) */
    fun isValue(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty()) return false
        return parseNumber(t) != null || scoreRe.matches(t) || symbolRe.matches(t)
    }

    private fun parseNumber(t: String): Double? =
        t.removeSuffix("%").trim().replace(',', '.').replace("−", "-").toDoubleOrNull()

    fun detectScale(values: List<String>, maxScore: Double): Scale {
        val v = values.map { it.trim() }.filter { it.isNotEmpty() }
        if (v.isEmpty()) return Scale.POINTS
        if (v.any { symbolRe.matches(it) && parseNumber(it) == null }) return Scale.SYMBOLS
        if (v.any { it.endsWith("%") }) return Scale.PERCENT
        if (v.any { scoreRe.matches(it) }) return Scale.POINTS
        val nums = v.mapNotNull { parseNumber(it) }
        if (nums.isEmpty()) return Scale.POINTS
        val lo = nums.min()
        val hi = nums.max()
        return when {
            lo < 0 && hi <= 3 && lo >= -3 -> Scale.PLUS_MINUS
            hi > maxScore && hi <= 100 -> Scale.PERCENT
            lo >= 1 && hi <= 5 && nums.all { it == Math.rint(it) } && nums.size >= 9 -> Scale.FIVE
            else -> Scale.POINTS
        }
    }

    /** Convertit une cellule en points sur 0 → maxScore, null si illisible. */
    fun toPoints(raw: String, scale: Scale, maxScore: Double, symbolRange: Int = 2): Double? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        scoreRe.matchEntire(t)?.let { m ->
            val a = m.groupValues[1].replace(',', '.').toDouble()
            val b = m.groupValues[2].replace(',', '.').toDouble()
            return if (a + b == 0.0) maxScore / 2 else maxScore * a / (a + b)
        }
        fun rel(r: Double) = (maxScore * (0.5 + 0.45 * r.coerceIn(-1.0, 1.0)))
        val points = when (scale) {
            Scale.SYMBOLS, Scale.AUTO -> {
                if (symbolRe.matches(t) && parseNumber(t) == null) {
                    val k = t.count { it == '+' } - t.count { it == '-' }
                    rel(k.toDouble() / max(2, symbolRange))
                } else parseNumber(t)
            }
            Scale.POINTS -> parseNumber(t)
            Scale.FIVE -> parseNumber(t)?.let { rel((it - 3) / 2) }
            Scale.PLUS_MINUS -> parseNumber(t)?.let { rel(it / max(2, symbolRange)) }
            Scale.PERCENT -> parseNumber(t)?.let { maxScore * it / 100 }
        } ?: return null
        return points.coerceIn(0.0, maxScore)
    }

    // ----------------------------------------------------------- tableau

    fun parseTable(rows: List<List<String>>, maxScore: Double = 20.0, forced: Scale = Scale.AUTO): Result {
        val table = rows.map { r -> r.map { it.trim() } }.filter { r -> r.any { it.isNotEmpty() } }
        if (table.isEmpty()) throw ImportException("Fichier vide")
        val header = table.first()
        val headerNames = header.drop(1).count { it.isNotEmpty() && !isValue(it) }
        return if (headerNames >= 4) parseGrid(table, maxScore, forced) else parseLong(table, maxScore, forced)
    }

    private fun splitName(raw: String): Player {
        val m = Regex("^(.*?)\\s*[(\\[](.+?)[)\\]]\\s*$").matchEntire(raw.trim())
        return if (m != null) Player(m.groupValues[1].trim(), m.groupValues[2].trim()) else Player(raw.trim())
    }

    private fun parseGrid(table: List<List<String>>, maxScore: Double, forced: Scale): Result {
        val warnings = ArrayList<String>()
        val header = table[0]
        val cols0 = header.indices.drop(1).filter { header[it].isNotEmpty() && !isValue(header[it]) }
        var cols = cols0
        var body = table.drop(1)
        // Ligne de factions adverses : que du texte sous les en-têtes
        var theirFactions: List<String>? = null
        if (body.isNotEmpty()) {
            val cells = cols.map { body[0].getOrElse(it) { "" } }
            if (cells.none { isValue(it) } && cells.count { it.isNotEmpty() } >= (cols.size + 1) / 2) {
                theirFactions = cells
                body = body.drop(1)
            }
        }
        // Bloc contigu de joueurs : s'arrête à la ligne de totaux (sans nom) ou
        // aux notes sous la matrice, qui ne sont pas des affiches
        val isPlayerRow = { r: List<String> ->
            r.getOrElse(0) { "" }.let { it.isNotEmpty() && !isValue(it) } && cols.any { isValue(r.getOrElse(it) { "" }) }
        }
        val first = body.indexOfFirst(isPlayerRow)
        val rows = if (first < 0) emptyList() else body.drop(first).takeWhile(isPlayerRow)
        if (rows.isEmpty()) throw ImportException("Aucune ligne de scores trouvée sous l'en-tête")
        // Une colonne d'en-tête sans aucune valeur (ex. « Faction ») n'est pas un adversaire
        val textCols = cols.filter { c -> rows.none { isValue(it.getOrElse(c) { "" }) } }
        cols = cols - textCols.toSet()
        if (cols.isEmpty()) throw ImportException("Aucune colonne de scores")
        // Colonne de texte avant les scores = factions de nos joueurs
        val factionCol = (1 until cols.first()).firstOrNull { c ->
            rows.all { r -> r.getOrElse(c) { "" }.let { it.isNotEmpty() && !isValue(it) } }
        }
        theirFactions = theirFactions?.let { f -> cols.map { c -> f[cols0.indexOf(c)] } }

        val n = minOf(rows.size, cols.size)
        if (rows.size != cols.size) warnings += "Matrice ${rows.size}×${cols.size} : on garde ${n}×$n"
        val raw = rows.take(n).map { r -> cols.take(n).map { r.getOrElse(it) { "" } } }
        val scale = if (forced == Scale.AUTO) detectScale(raw.flatten(), maxScore) else forced
        val range = symbolRange(raw.flatten())
        var missing = 0
        val scores = raw.map { r -> r.map { toPoints(it, scale, maxScore, range) ?: run { missing++; maxScore / 2 } } }
        if (missing > 0) warnings += "$missing case(s) vide(s) ou illisible(s) → ${fmt(maxScore / 2)}"

        val ours = rows.take(n).map { r ->
            val p = splitName(r[0])
            if (factionCol != null && p.faction.isEmpty()) p.copy(faction = r[factionCol]) else p
        }
        val theirs = cols.take(n).mapIndexed { k, c ->
            val p = splitName(header[c])
            val f = theirFactions?.getOrNull(k).orEmpty()
            if (f.isNotEmpty() && p.faction.isEmpty()) p.copy(faction = f) else p
        }
        val corner = header[0].takeIf { it.isNotEmpty() && !isValue(it) }
        return finish(ours, theirs, scores, scale, "grille", warnings, corner)
    }

    private fun parseLong(table: List<List<String>>, maxScore: Double, forced: Scale): Result {
        val warnings = ArrayList<String>()
        // Colonne de score : la première (après les deux noms) qui contient des valeurs
        val data = table.filter { r -> r.size >= 3 && r[0].isNotEmpty() && r[1].isNotEmpty() }
        val scoreCol = (2 until (data.maxOfOrNull { it.size } ?: 3)).firstOrNull { c ->
            data.count { isValue(it.getOrElse(c) { "" }) } >= data.size / 2
        } ?: throw ImportException("Format non reconnu : ni grille (joueurs adverses en 1re ligne), ni liste « joueur ; adversaire ; score »")
        val lines = data.filter { isValue(it.getOrElse(scoreCol) { "" }) }
        val ourNames = LinkedHashSet<String>()
        val theirNames = LinkedHashSet<String>()
        for (l in lines) { ourNames += l[0]; theirNames += l[1] }
        if (ourNames.size != theirNames.size) {
            throw ImportException("${ourNames.size} joueurs chez nous pour ${theirNames.size} en face")
        }
        val ourIdx = ourNames.withIndex().associate { it.value to it.index }
        val theirIdx = theirNames.withIndex().associate { it.value to it.index }
        val rawValues = lines.map { it[scoreCol] }
        val scale = if (forced == Scale.AUTO) detectScale(rawValues, maxScore) else forced
        val range = symbolRange(rawValues)
        val n = ourNames.size
        val grid = Array(n) { arrayOfNulls<Double>(n) }
        for (l in lines) grid[ourIdx.getValue(l[0])][theirIdx.getValue(l[1])] = toPoints(l[scoreCol], scale, maxScore, range)
        var missing = 0
        val scores = grid.map { r -> r.map { it ?: run { missing++; maxScore / 2 } } }
        if (missing > 0) warnings += "$missing affiche(s) absente(s) → ${fmt(maxScore / 2)}"
        return finish(ourNames.map(::splitName), theirNames.map(::splitName), scores, scale, "liste", warnings)
    }

    private fun finish(
        ours: List<Player>, theirs: List<Player>, scores: List<List<Double>>, scale: Scale,
        layout: String, warnings: MutableList<String>, corner: String? = null
    ): Result {
        val n = ours.size
        if (n < 4) throw ImportException("Seulement $n joueur(s) trouvé(s)")
        if (n !in setOf(4, 6, 8)) warnings += "$n joueurs : l'app gère 4, 6 ou 8 — ajuste la taille avant d'analyser"
        return Result(ours, theirs, scores, scale, layout, warnings, corner)
    }

    private fun symbolRange(values: List<String>): Int {
        var r = 2
        for (v in values) {
            val t = v.trim()
            if (symbolRe.matches(t) && parseNumber(t) == null) r = max(r, abs(t.count { it == '+' } - t.count { it == '-' }))
            else parseNumber(t)?.let { if (it < 0 || it in 2.5..3.0) r = max(r, abs(it).toInt()) }
        }
        return minOf(r, 3)
    }

    // ------------------------------------------------------ position

    /** Ne garde que le tableau à partir de la ligne / colonne de début (1 = première). */
    fun crop(rows: List<List<String>>, startRow: Int, startCol: Int): List<List<String>> =
        rows.drop((startRow - 1).coerceAtLeast(0)).map { it.drop((startCol - 1).coerceAtLeast(0)) }

    /**
     * Trouve le coin haut-gauche du tableau (ligne d'en-tête, colonne des noms),
     * 1 = première ligne / colonne A. Gère titres, lignes vides et colonnes
     * décalées au-dessus / à gauche de la matrice.
     */
    fun detectStart(rows: List<List<String>>): Pair<Int, Int> {
        fun cell(r: Int, c: Int) = rows.getOrNull(r)?.getOrNull(c)?.trim().orEmpty()
        fun text(r: Int, c: Int) = cell(r, c).let { it.isNotEmpty() && !isValue(it) }
        val valueCols = rows.map { r -> r.indices.filter { isValue(r[it]) } }

        // Grille : lignes portant au moins 4 valeurs
        val gridRows = rows.indices.filter { valueCols[it].size >= 4 }
        if (gridRows.size >= 4) {
            val first = gridRows.first()
            // Colonnes de scores : celles remplies sur la majorité des lignes de la grille
            val counts = HashMap<Int, Int>()
            for (r in gridRows) for (c in valueCols[r]) counts[c] = (counts[c] ?: 0) + 1
            val cols = counts.filter { it.value * 2 >= gridRows.size }.keys.sorted()
            if (cols.size >= 4) {
                val firstCol = cols.first()
                // En-tête : bloc de lignes de texte juste au-dessus (en-tête + factions éventuelles)
                var header = first
                while (header - 1 >= 0 && cols.count { text(header - 1, it) } * 2 >= cols.size && first - header < 2) header--
                // Noms : bloc de colonnes de texte juste à gauche (noms + factions éventuelles)
                var nameCol = firstCol
                while (nameCol - 1 >= 0 && gridRows.count { text(it, nameCol - 1) } * 2 >= gridRows.size && firstCol - nameCol < 2) nameCol--
                if (header == first) header = (first - 1).coerceAtLeast(0)
                if (nameCol == firstCol) nameCol = (firstCol - 1).coerceAtLeast(0)
                return (header + 1) to (nameCol + 1)
            }
        }

        // Liste : « nom ; nom ; valeur » — première colonne où ce motif domine
        val width = rows.maxOfOrNull { it.size } ?: 0
        for (c in 0 until width - 2) {
            val hits = rows.indices.filter { text(it, c) && text(it, c + 1) && isValue(cell(it, c + 2)) }
            if (hits.size >= 4) {
                val first = hits.first()
                val start = if (first > 0 && text(first - 1, c) && text(first - 1, c + 1) && !isValue(cell(first - 1, c + 2))) first - 1 else first
                return (start + 1) to (c + 1)
            }
        }
        return 1 to 1
    }

    /** « B », « b », « 2 » → 2 ; « AA » → 27 ; null si illisible. */
    fun parseColumn(raw: String): Int? {
        val t = raw.trim().uppercase()
        if (t.isEmpty()) return null
        t.toIntOrNull()?.let { return it.takeIf { n -> n >= 1 } }
        if (!t.all { it in 'A'..'Z' }) return null
        return t.fold(0) { n, c -> n * 26 + (c - 'A' + 1) }
    }

    fun columnLabel(n: Int): String {
        var k = n
        val sb = StringBuilder()
        while (k > 0) { val r = (k - 1) % 26; sb.insert(0, 'A' + r); k = (k - 1) / 26 }
        return sb.toString()
    }

    // ------------------------------------------------------ Google Sheets

    /**
     * Lien de partage Google Sheets → lien d'export CSV (le fichier doit être
     * partagé « tous les utilisateurs disposant du lien »). Garde l'onglet (gid).
     */
    fun googleSheetsCsvUrl(url: String): String? {
        val id = Regex("/spreadsheets/d/([a-zA-Z0-9_-]+)").find(url)?.groupValues?.get(1) ?: return null
        if (url.contains("/d/e/")) {
            // Lien « Publier sur le Web »
            val gid = Regex("[#&?]gid=(\\d+)").find(url)?.groupValues?.get(1)
            return "https://docs.google.com/spreadsheets/d/e/$id/pub?output=csv" + (gid?.let { "&gid=$it" } ?: "")
        }
        val gid = Regex("[#&?]gid=(\\d+)").find(url)?.groupValues?.get(1)
        return "https://docs.google.com/spreadsheets/d/$id/export?format=csv" + (gid?.let { "&gid=$it" } ?: "")
    }

    /**
     * Export .xlsx du classeur entier : contrairement au CSV (un seul onglet),
     * il garde toutes les feuilles avec leur nom, pour choisir l'équipe.
     */
    fun googleSheetsXlsxUrl(url: String): String? {
        val id = Regex("/spreadsheets/d/(e/)?([a-zA-Z0-9_-]+)").find(url) ?: return null
        return if (id.groupValues[1].isNotEmpty()) "https://docs.google.com/spreadsheets/d/e/${id.groupValues[2]}/pub?output=xlsx"
        else "https://docs.google.com/spreadsheets/d/${id.groupValues[2]}/export?format=xlsx"
    }

    // ------------------------------------------------------------- export

    /** Export CSV « grille », relisible par l'import (séparateur « ; »). */
    fun toCsv(m: Matchup): String {
        fun q(s: String) = if (s.contains(';') || s.contains('"')) "\"" + s.replace("\"", "\"\"") + "\"" else s
        val sb = StringBuilder()
        sb.append(q(m.ourTeam + " \\ " + m.theirTeam))
        for (p in m.theirPlayers) sb.append(';').append(q(p.label))
        sb.append('\n')
        m.ourPlayers.forEachIndexed { i, p ->
            sb.append(q(p.label))
            for (v in m.scores[i]) sb.append(';').append(fmt(v))
            sb.append('\n')
        }
        return sb.toString()
    }

    fun fmt(v: Double): String =
        if (v == Math.rint(v)) v.toLong().toString() else String.format(java.util.Locale.ROOT, "%.1f", v)
}
