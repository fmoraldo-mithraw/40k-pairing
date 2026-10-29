package com.pairing40k.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structure des vraies matrices d'équipe (Google Sheets, un onglet par équipe
 * adverse) : matrice en B3, nom de l'équipe dans le coin, factions en
 * colonnes, ligne et colonne de totaux, cases vides, notes de pairing dessous.
 */
class WorkbookImportTest {

    private val teamSheet = listOf(
        listOf("Mission I :"),
        listOf("Hidden Supplies / Search and Destroy"),
        listOf("", "Les Rouges", "Necrons", "Daemons", "Custo", "TS", "Ad mec", "Astra"),
        listOf("", "Alpha", "3", "5", "3", "3", "8", "12", "34"),
        listOf("", "Bravo", "9", "8", "5", "7", "9", "11", "49"),
        listOf("", "Charlie", "12", "6", "9", "5", "8", "12", "52"),
        listOf("", "Delta", "5", "6", "5", "8", "10", "10", "44"),
        listOf("", "Echo", "2", "3", "10", "10", "6", "8", "39"),
        listOf("", "Fox", "8", "", "8", "", "9", "12", "37"),
        listOf("", "", "39", "31", "40", "33", "50", "65"),
        listOf(),
        listOf("", "0", "0/1", "DEF1 : Echo", "", "", "Daemons: Bravo / Fox"),
        listOf("", "1", "2/5", "", "", "", "Necrons : Alpha"),
        listOf("", "5", "16+", "DEF2 : Delta")
    )

    private val dashboard = listOf(
        listOf(), listOf(), listOf(), listOf(),
        listOf("", "", "Estimé", "Réel", "Diff", "Estimé", "Réel", "Diff"),
        listOf("", "Alpha", "5", "0", "0", "10", "1", "0"),
        listOf("", "Bravo", "5", "0", "0", "10", "1", "0"),
        listOf("", "Charlie", "5", "0", "0", "10", "1", "0"),
        listOf("", "Delta", "5", "0", "0", "10", "1", "0"),
        listOf("", "Echo", "5", "0", "0", "10", "1", "0"),
        listOf("", "Fox", "5", "0", "0", "10", "1", "0")
    )

    @Test
    fun detectionDuDebut() {
        assertEquals(3 to 2, MatrixImport.detectStart(teamSheet))
        val long = listOf(listOf("Titre"), listOf(), listOf("", "", "Joueur", "Adv", "Score")) +
            List(16) { listOf("", "", "J${it / 4}", "A${it % 4}", "${it % 20}") }
        assertEquals(3 to 3, MatrixImport.detectStart(long))
        assertEquals(1 to 1, MatrixImport.detectStart(MatrixImport.parseCsv("a;b;c;d;e\nx;1;2;3;4\ny;1;2;3;4\nz;1;2;3;4\nw;1;2;3;4")))
    }

    @Test
    fun colonnes() {
        assertEquals(2, MatrixImport.parseColumn("B"))
        assertEquals(27, MatrixImport.parseColumn("aa"))
        assertEquals(3, MatrixImport.parseColumn("3"))
        assertNull(MatrixImport.parseColumn("B2"))
        assertEquals("AA", MatrixImport.columnLabel(27))
        assertEquals("B", MatrixImport.columnLabel(2))
    }

    @Test
    fun ongletDEquipe() {
        val r = WorkbookImport.analyze(
            listOf(WorkbookImport.Sheet("Les Rouges", teamSheet)), Settings(), MatrixImport.Scale.AUTO, 20.0, "x"
        ).single()
        val res = r.result!!
        assertEquals("B3", r.startLabel)
        assertEquals("Les Rouges", r.team)
        assertEquals(6, res.size)
        assertEquals(listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Fox"), res.ourPlayers.map { it.name })
        assertEquals("Necrons", res.theirPlayers[0].name)
        assertEquals(listOf(3.0, 5.0, 3.0, 3.0, 8.0, 12.0), res.scores[0])
        assertEquals(10.0, res.scores[5][1], 1e-9) // case vide → égalité
        assertEquals("Les Rouges", res.cornerName)
    }

    @Test
    fun debutManuel() {
        val shifted = teamSheet.map { listOf("", "") + it }
        val auto = WorkbookImport.analyze(listOf(WorkbookImport.Sheet("T", shifted)), Settings(), MatrixImport.Scale.AUTO, 20.0, "x").single()
        assertEquals("D3", auto.startLabel)
        val manual = WorkbookImport.analyze(
            listOf(WorkbookImport.Sheet("T", shifted)), Settings(autoStart = false, startRow = 3, startCol = 4), MatrixImport.Scale.AUTO, 20.0, "x"
        ).single()
        assertEquals(auto.result!!.scores, manual.result!!.scores)
        val wrong = WorkbookImport.analyze(
            listOf(WorkbookImport.Sheet("T", shifted)), Settings(autoStart = false, startRow = 1, startCol = 1), MatrixImport.Scale.AUTO, 20.0, "x"
        ).single()
        assertTrue(!wrong.ok || wrong.result!!.ourPlayers[0].name != "Alpha")
    }

    @Test
    fun classeurComplet() {
        val sheets = listOf(
            WorkbookImport.Sheet("Dashbord", dashboard),
            WorkbookImport.Sheet("Les Rouges", teamSheet),
            WorkbookImport.Sheet("Les Bleus", teamSheet.map { r -> r.map { if (it == "Les Rouges") "Les Bleus" else it } })
        )
        val data = AppData(settings = Settings(defaultTeam = "Mon Club"))
        val results = WorkbookImport.analyze(sheets, data.settings, MatrixImport.Scale.AUTO, 20.0, "x")
        assertTrue(!results[0].ok) // tableau de bord ignoré
        val merge = WorkbookImport.merge(data, results)
        assertEquals(2, merge.created)
        // La matrice vierge du premier lancement est remplacée
        assertEquals(listOf("Les Rouges", "Les Bleus"), merge.data.matchups.map { it.theirTeam })
        assertTrue(merge.data.matchups.all { it.ourTeam == "Mon Club" })
        assertEquals("Les Rouges", merge.data.active.theirTeam)

        // Ré-import : mise à jour par nom d'équipe, pas de doublon, notes conservées
        val withNotes = merge.data.copy(matchups = merge.data.matchups.map { it.copy(notes = "def Echo") })
        val again = WorkbookImport.merge(withNotes, results)
        assertEquals(0, again.created)
        assertEquals(2, again.updated)
        assertEquals(2, again.data.matchups.size)
        assertTrue(again.data.matchups.all { it.notes == "def Echo" })
    }

    @Test
    fun csvSansNomDOnglet() {
        val csv = "Les Verts;A;B;C;D\nJ1;1;2;3;4\nJ2;5;6;7;8\nJ3;9;10;11;12\nJ4;13;14;15;16"
        val r = WorkbookImport.analyze(listOf(WorkbookImport.Sheet(null, MatrixImport.parseCsv(csv))), Settings(), MatrixImport.Scale.AUTO, 20.0, "fichier").single()
        assertEquals("Les Verts", r.team)
        val r2 = WorkbookImport.analyze(listOf(WorkbookImport.Sheet(null, MatrixImport.parseCsv(csv.replaceFirst("Les Verts", "")))), Settings(), MatrixImport.Scale.AUTO, 20.0, "fichier").single()
        assertEquals("fichier", r2.team)
    }

    @Test
    fun reglagesSauvegardes() {
        val d = AppData(settings = Settings(defaultTeam = "Mon Club", autoStart = false, startRow = 3, startCol = 2))
        assertEquals(d.settings, Store.fromJson(Store.toJson(d)).settings)
    }
}
