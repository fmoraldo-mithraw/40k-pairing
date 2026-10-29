package com.pairing40k.app.data

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lecteur .xlsx minimal (export Excel / Google Sheets) : un classeur est un zip
 * de XML. On lit le premier onglet (ou celui demandé) en texte, sans dépendance.
 */
object Xlsx {

    class Workbook(val sheetNames: List<String>, private val sheets: List<List<List<String>>>) {
        fun sheet(index: Int): List<List<String>> = sheets[index]
    }

    fun read(input: InputStream): Workbook {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory && (e.name.endsWith(".xml") || e.name.endsWith(".rels"))) files[e.name.removePrefix("/")] = zip.readBytes()
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { parse(it) }?.let { doc ->
            doc.getElementsByTagName("si").let { list ->
                List(list.length) { i -> textOf(list.item(i) as Element) }
            }
        } ?: emptyList()

        val workbook = files["xl/workbook.xml"]?.let { parse(it) }
            ?: throw MatrixImport.ImportException("Fichier Excel invalide (workbook.xml absent)")
        val rels = files["xl/_rels/workbook.xml.rels"]?.let { parse(it) }
        val targets = HashMap<String, String>()
        rels?.getElementsByTagName("Relationship")?.let { list ->
            for (i in 0 until list.length) {
                val r = list.item(i) as Element
                targets[r.getAttribute("Id")] = r.getAttribute("Target")
            }
        }
        val names = ArrayList<String>()
        val sheets = ArrayList<List<List<String>>>()
        val sheetNodes = workbook.getElementsByTagName("sheet")
        for (i in 0 until sheetNodes.length) {
            val el = sheetNodes.item(i) as Element
            val rid = el.getAttribute("r:id")
            val target = targets[rid]?.let { t -> if (t.startsWith("/")) t.removePrefix("/") else "xl/$t" }
                ?: "xl/worksheets/sheet${i + 1}.xml"
            val data = files[target] ?: continue
            names += el.getAttribute("name")
            sheets += readSheet(data, shared)
        }
        if (sheets.isEmpty()) throw MatrixImport.ImportException("Aucun onglet lisible dans le classeur")
        return Workbook(names, sheets)
    }

    private fun readSheet(data: ByteArray, shared: List<String>): List<List<String>> {
        val doc = parse(data)
        val out = ArrayList<MutableList<String>>()
        val rows = doc.getElementsByTagName("row")
        for (i in 0 until rows.length) {
            val row = rows.item(i) as Element
            val rIndex = row.getAttribute("r").toIntOrNull()?.minus(1) ?: out.size
            while (out.size <= rIndex) out.add(ArrayList())
            val line = out[rIndex]
            val cells = row.getElementsByTagName("c")
            for (k in 0 until cells.length) {
                val c = cells.item(k) as Element
                val col = colIndex(c.getAttribute("r")).takeIf { it >= 0 } ?: line.size
                val type = c.getAttribute("t")
                val v = c.getElementsByTagName("v").item(0)?.textContent
                val text = when (type) {
                    "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    "inlineStr" -> textOf(c)
                    "b" -> if (v == "1") "VRAI" else "FAUX"
                    else -> v?.let(::cleanNumber) ?: ""
                }
                while (line.size <= col) line.add("")
                line[col] = text
            }
        }
        return out
    }

    /** « 12.000000000001 » → « 12 » (artefacts flottants d'Excel) */
    private fun cleanNumber(v: String): String {
        val d = v.toDoubleOrNull() ?: return v
        val r = Math.round(d * 1e6) / 1e6
        return if (r == Math.rint(r)) r.toLong().toString() else r.toString()
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        var any = false
        for (ch in ref) {
            if (ch in 'A'..'Z') { n = n * 26 + (ch - 'A' + 1); any = true } else break
        }
        return if (any) n - 1 else -1
    }

    private fun textOf(el: Element): String {
        val ts = el.getElementsByTagName("t")
        val sb = StringBuilder()
        for (i in 0 until ts.length) sb.append(ts.item(i).textContent)
        return sb.toString()
    }

    private fun parse(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        setExpandEntityReferences(false)
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement

    fun isXlsx(bytes: ByteArray) = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
}
