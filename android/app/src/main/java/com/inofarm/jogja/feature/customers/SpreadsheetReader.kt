package com.inofarm.jogja.feature.customers

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigDecimal
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/** One spreadsheet row; [number] is the row number the user sees in Excel (1-based). */
data class SheetRow(val number: Int, val cells: List<String>)

data class Sheet(val headers: List<String>, val rows: List<SheetRow>)

/**
 * Reads the first sheet of an .xlsx (no dependency: zip + SAX) or a .csv (comma or semicolon) for the buyer import
 * (Inofarm 20 Nov 2026). Only cell text is read; formulas, styles and other sheets are ignored.
 */
object SpreadsheetReader {
    private const val MAX_ENTRY_BYTES = 20_000_000L

    fun read(name: String, input: InputStream, maxBytes: Long = 2_000_000, maxRows: Int = 1000): Sheet {
        val bytes = input.readBounded(maxBytes)
        val rows = if (name.lowercase().endsWith(".csv")) csv(String(bytes, Charsets.UTF_8).removePrefix(Char(0xFEFF).toString())) else xlsx(bytes)
        val filled = rows.filter { row -> row.cells.any { it.isNotBlank() } }
        require(filled.isNotEmpty()) { "File tidak berisi baris." }
        val header = filled.first()
        val body = filled.drop(1)
        require(body.size <= maxRows) { "File berisi ${body.size} baris; maksimal $maxRows per impor." }
        val width = filled.maxOf { it.cells.size }
        fun pad(cells: List<String>) = cells + List(width - cells.size) { "" }
        return Sheet(pad(header.cells).map { it.trim() }, body.map { SheetRow(it.number, pad(it.cells).map(String::trim)) })
    }

    /** Index of the name column and of the WhatsApp column, guessed from header words. */
    fun guessColumns(headers: List<String>): Pair<Int?, Int?> {
        val lower = headers.map { it.lowercase() }
        val name = lower.indexOfFirst { h -> listOf("nama", "pembeli", "customer", "name").any { h.contains(it) } }.takeIf { it >= 0 }
        val phone = lower.indexOfFirst { h -> listOf("whatsapp", "wa", "hp", "telepon", "telp", "phone", "no").any { word -> Regex("\\b$word\\b").containsMatchIn(h) } }
            .takeIf { it >= 0 && it != name }
        return name to phone
    }

    private fun InputStream.readBounded(max: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            total += n
            require(total <= max) { "File melebihi ${max / 1_000_000} MB." }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun csv(text: String): List<SheetRow> {
        val lines = text.split(Regex("\r\n|\n|\r"))
        val delimiter = if (lines.firstOrNull { it.isNotBlank() }?.let { it.count { c -> c == ';' } > it.count { c -> c == ',' } } == true) ';' else ','
        return lines.mapIndexed { index, line ->
            val cells = mutableListOf<String>()
            val cell = StringBuilder()
            var quoted = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cell.append('"'); i++ }
                    c == '"' -> quoted = !quoted
                    c == delimiter && !quoted -> { cells += cell.toString(); cell.clear() }
                    else -> cell.append(c)
                }
                i++
            }
            cells += cell.toString()
            SheetRow(index + 1, cells)
        }
    }

    private fun xlsx(bytes: ByteArray): List<SheetRow> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name in setOf("xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/sharedStrings.xml") || entry.name.startsWith("xl/worksheets/")) {
                    entries[entry.name] = zip.readBounded(MAX_ENTRY_BYTES)
                }
            }
        }
        require(entries.containsKey("xl/workbook.xml")) { "File bukan Excel .xlsx yang dikenali. Simpan ulang sebagai .xlsx atau .csv." }
        var firstSheetRel: String? = null
        parse(entries.getValue("xl/workbook.xml"), object : DefaultHandler() {
            override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes) {
                if (qName.endsWith("sheet") && firstSheetRel == null) firstSheetRel = attrs.getValue("r:id")
            }
        })
        var target: String? = null
        entries["xl/_rels/workbook.xml.rels"]?.let { rels ->
            parse(rels, object : DefaultHandler() {
                override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes) {
                    if (qName.endsWith("Relationship") && attrs.getValue("Id") == firstSheetRel) target = attrs.getValue("Target")
                }
            })
        }
        val sheetPath = target?.let { if (it.startsWith("/")) it.removePrefix("/") else "xl/$it" }
            ?: entries.keys.filter { it.startsWith("xl/worksheets/sheet") }.minOrNull()
        val sheet = entries[sheetPath] ?: error("Lembar pertama tidak ditemukan di file Excel.")
        val shared = mutableListOf<String>()
        entries["xl/sharedStrings.xml"]?.let { strings ->
            parse(strings, object : DefaultHandler() {
                val text = StringBuilder(); var inText = false
                override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes) {
                    if (qName == "si") text.clear(); if (qName == "t") inText = true
                }
                override fun characters(ch: CharArray, start: Int, length: Int) { if (inText) text.append(ch, start, length) }
                override fun endElement(uri: String?, local: String?, qName: String) {
                    if (qName == "t") inText = false; if (qName == "si") shared += text.toString()
                }
            })
        }
        val rows = mutableListOf<SheetRow>()
        parse(sheet, object : DefaultHandler() {
            var row = 0; val cells = sortedMapOf<Int, String>(); var column = 0; var type: String? = null
            val value = StringBuilder(); var inValue = false
            override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes) {
                when (qName) {
                    "row" -> { row = attrs.getValue("r")?.toIntOrNull() ?: (row + 1); cells.clear(); column = 0 }
                    "c" -> {
                        column = attrs.getValue("r")?.let(::columnIndex) ?: (column + 1)
                        type = attrs.getValue("t"); value.clear()
                    }
                    "v", "t" -> inValue = true
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) { if (inValue) value.append(ch, start, length) }
            override fun endElement(uri: String?, local: String?, qName: String) {
                when (qName) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = value.toString()
                        cells[column] = when (type) {
                            "s" -> shared.getOrElse(raw.trim().toIntOrNull() ?: -1) { "" }
                            "inlineStr", "str" -> raw
                            else -> plainNumber(raw)
                        }
                    }
                    "row" -> if (cells.isNotEmpty()) rows += SheetRow(row, (0..cells.lastKey()).map { cells[it] ?: "" })
                }
            }
        })
        return rows
    }

    /** Excel stores phone numbers as numbers, sometimes as 8.1233334444E10; show them as plain digits. */
    private fun plainNumber(raw: String): String = runCatching { BigDecimal(raw.trim()).stripTrailingZeros().toPlainString() }.getOrDefault(raw)

    /** "B4" -> 1 (zero-based column). */
    private fun columnIndex(reference: String): Int =
        reference.takeWhile { it.isLetter() }.uppercase().fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1

    private fun parse(bytes: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance()
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        factory.newSAXParser().parse(ByteArrayInputStream(bytes), handler)
    }
}
