package com.pairing40k.app.data

import com.pairing40k.app.data.MatrixImport.Scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MatrixImportTest {

    private val grid6 = """
        Nous \ Eux;Alex (Orks);Bea (Necrons);Chris;Dan;Eve;Fred
        Jean (Tau);12;8;10;15;5;10
        Luc;10;10;10;10;10;10
        Marc;13,5;7;10;10;10;10
        Nico;10;10;10;10;10;10
        Olaf;10;10;10;10;10;10
        Paul;10;10;10;10;10;20
    """.trimIndent()

    @Test
    fun grillePointVirgule() {
        val r = MatrixImport.parseTable(MatrixImport.parseCsv(grid6))
        assertEquals(6, r.size)
        assertEquals("grille", r.layout)
        assertEquals(Scale.POINTS, r.scale)
        assertEquals(Player("Jean", "Tau"), r.ourPlayers[0])
        assertEquals(Player("Alex", "Orks"), r.theirPlayers[0])
        assertEquals(13.5, r.scores[2][0], 1e-9)
        assertEquals(20.0, r.scores[5][5], 1e-9)
    }

    @Test
    fun grilleAvecLigneEtColonneDeFactions() {
        val csv = """
            ,,A,B,C,D
            ,Faction,Orks,Eldar,Tau,Necrons
            Jean,Custodes,++,+,=,-
            Luc,Orks,--,=,=,+
            Marc,Tau,=,=,=,=
            Nico,Eldar,+,-,=,=
        """.trimIndent()
        val r = MatrixImport.parseTable(MatrixImport.parseCsv(csv))
        assertEquals(4, r.size)
        assertEquals(Scale.SYMBOLS, r.scale)
        assertEquals(Player("Jean", "Custodes"), r.ourPlayers[0])
        assertEquals(Player("A", "Orks"), r.theirPlayers[0])
        assertEquals(19.0, r.scores[0][0], 1e-9)
        assertEquals(10.0, r.scores[0][2], 1e-9)
        assertEquals(1.0, r.scores[1][0], 1e-9)
        assertTrue(r.scores[0][1] in 14.0..15.0)
    }

    @Test
    fun formatListe() {
        val sb = StringBuilder("Joueur,Adversaire,Score\n")
        val ours = listOf("J1", "J2", "J3", "J4")
        val theirs = listOf("A1", "A2", "A3", "A4")
        for ((i, o) in ours.withIndex()) for ((j, t) in theirs.withIndex()) sb.append("$o,$t,${i * 4 + j}\n")
        val r = MatrixImport.parseTable(MatrixImport.parseCsv(sb.toString()))
        assertEquals("liste", r.layout)
        assertEquals(4, r.size)
        assertEquals(15.0, r.scores[3][3], 1e-9)
        assertEquals(6.0, r.scores[1][2], 1e-9)
    }

    @Test
    fun echelles() {
        assertEquals(Scale.FIVE, MatrixImport.detectScale(List(16) { "${it % 5 + 1}" }, 20.0))
        assertEquals(Scale.PLUS_MINUS, MatrixImport.detectScale(listOf("-2", "0", "1", "2"), 20.0))
        assertEquals(Scale.PERCENT, MatrixImport.detectScale(listOf("30", "65", "90"), 20.0))
        assertEquals(19.0, MatrixImport.toPoints("5", Scale.FIVE, 20.0)!!, 1e-9)
        assertEquals(10.0, MatrixImport.toPoints("3", Scale.FIVE, 20.0)!!, 1e-9)
        assertEquals(13.0, MatrixImport.toPoints("13-7", Scale.POINTS, 20.0)!!, 1e-9)
        assertEquals(13.0, MatrixImport.toPoints("65%", Scale.PERCENT, 20.0)!!, 1e-9)
    }

    @Test
    fun guillemetsEtTabulations() {
        val rows = MatrixImport.parseCsv("a\t\"b\tc\"\td\n1\t2\t3")
        assertEquals(listOf("a", "b\tc", "d"), rows[0])
        assertEquals(listOf("1", "2", "3"), rows[1])
    }

    @Test
    fun lienGoogleSheets() {
        assertEquals(
            "https://docs.google.com/spreadsheets/d/AbC-123_x/export?format=csv&gid=456",
            MatrixImport.googleSheetsCsvUrl("https://docs.google.com/spreadsheets/d/AbC-123_x/edit#gid=456")
        )
        assertEquals(null, MatrixImport.googleSheetsCsvUrl("https://example.com"))
    }

    @Test
    fun allerRetourCsv() {
        val r = MatrixImport.parseTable(MatrixImport.parseCsv(grid6))
        val m = Matchup(ourPlayers = r.ourPlayers, theirPlayers = r.theirPlayers, scores = r.scores)
        val back = MatrixImport.parseTable(MatrixImport.parseCsv(MatrixImport.toCsv(m)))
        assertEquals(r.scores, back.scores)
        assertEquals(r.ourPlayers, back.ourPlayers)
        assertEquals(r.theirPlayers, back.theirPlayers)
    }

    @Test
    fun lectureXlsx() {
        val shared = """<?xml version="1.0"?><sst><si><t>Nous</t></si><si><t>A</t></si><si><t>B</t></si><si><t>C</t></si><si><t>D</t></si><si><r><t>Je</t></r><r><t>an</t></r></si></sst>"""
        val cells = StringBuilder("""<?xml version="1.0"?><worksheet><sheetData>""")
        cells.append("""<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c><c r="E1" t="s"><v>4</v></c></row>""")
        for (i in 2..5) {
            cells.append("""<row r="$i">""")
            if (i == 2) cells.append("""<c r="A2" t="s"><v>5</v></c>""") else cells.append("""<c r="A$i" t="inlineStr"><is><t>P$i</t></is></c>""")
            for ((k, col) in listOf("B", "C", "D", "E").withIndex()) cells.append("""<c r="$col$i"><v>${i + k}.0000000001</v></c>""")
            cells.append("</row>")
        }
        cells.append("</sheetData></worksheet>")
        val wb = """<?xml version="1.0"?><workbook><sheets><sheet name="Matrice" sheetId="1" r:id="rId1"/></sheets></workbook>"""
        val rels = """<?xml version="1.0"?><Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>"""
        val bytes = ByteArrayOutputStream().also { bo ->
            ZipOutputStream(bo).use { z ->
                for ((name, content) in listOf(
                    "xl/workbook.xml" to wb, "xl/_rels/workbook.xml.rels" to rels,
                    "xl/sharedStrings.xml" to shared, "xl/worksheets/sheet1.xml" to cells.toString()
                )) { z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray()); z.closeEntry() }
            }
        }.toByteArray()
        assertTrue(Xlsx.isXlsx(bytes))
        val book = Xlsx.read(ByteArrayInputStream(bytes))
        assertEquals(listOf("Matrice"), book.sheetNames)
        val r = MatrixImport.parseTable(book.sheet(0))
        assertEquals("Jean", r.ourPlayers[0].name)
        assertEquals(listOf("A", "B", "C", "D"), r.theirPlayers.map { it.name })
        assertEquals(listOf(2.0, 3.0, 4.0, 5.0), r.scores[0])
    }

    @Test
    fun sauvegardeAllerRetour() {
        val m = Matchup.blank(6).withScore(1, 2, 14.5).copy(theirTeam = "Les Orks")
        val data = AppData(
            matchups = listOf(m), activeId = m.id,
            history = listOf(HistoryEntry(ourTeam = "Nous", theirTeam = "Les Orks", maxScore = 20.0, expectedBefore = 61.2,
                games = listOf(PlayedGame("J1", "A1", "Oubliés", 12.0, null), PlayedGame("J2", "A2", "x", 9.0, 15.0))))
        )
        val back = Store.fromJson(Store.toJson(data))
        assertEquals(data, back)
    }
}

class ExemplesTest {
    // Les tests JVM tournent depuis app/ : les exemples du dépôt restent importables
    private fun read(name: String) = java.io.File("../exemples/$name").readText()

    @Test
    fun exemple8v8() {
        val r = MatrixImport.parseTable(MatrixImport.parseCsv(read("matrice-8v8.csv")))
        assertEquals(8, r.size)
        assertEquals(Player("Antoine", "Space Marines"), r.ourPlayers[0])
        assertTrue(r.warnings.isEmpty())
    }

    @Test
    fun exemple6v6Symboles() {
        val r = MatrixImport.parseTable(MatrixImport.parseCsv(read("liste-6v6-symboles.csv")))
        assertEquals(6, r.size)
        assertEquals("liste", r.layout)
        assertEquals(Scale.SYMBOLS, r.scale)
    }
}
