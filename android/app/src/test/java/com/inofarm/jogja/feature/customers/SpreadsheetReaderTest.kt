package com.inofarm.jogja.feature.customers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SpreadsheetReaderTest {
    /** A minimal real .xlsx: workbook, relationships, shared strings and one sheet (A1:B3). */
    private fun xlsx(): ByteArray {
        val files = linkedMapOf(
            "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Pembeli" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>Nama Pembeli</t></si><si><t>No HP</t></si><si><t>Bu Sari</t></si><si><r><t>Pak </t></r><r><t>Joko</t></r></si></sst>""",
            "xl/worksheets/sheet1.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
                <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2"><v>81233334444</v></c></row>
                <row r="4"><c r="A4" t="s"><v>3</v></c><c r="B4" t="inlineStr"><is><t>0812-5555-6666</t></is></c></row>
                </sheetData></worksheet>""")
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> files.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() } }
        return out.toByteArray()
    }

    @Test fun reads_xlsx_shared_strings_first_sheet() {
        val sheet = SpreadsheetReader.read("pembeli.xlsx", ByteArrayInputStream(xlsx()))
        assertEquals(listOf("Nama Pembeli", "No HP"), sheet.headers)
        assertEquals(listOf("Bu Sari", "81233334444"), sheet.rows[0].cells)
        assertEquals(2, sheet.rows[0].number)
        assertEquals(listOf("Pak Joko", "0812-5555-6666"), sheet.rows[1].cells)
        assertEquals(4, sheet.rows[1].number)
    }

    @Test fun reads_csv_semicolon_and_quotes() {
        val csv = "Nama;WhatsApp\n\"Sari; Bu\";0812 3333 4444\n\nJoko;\n"
        val sheet = SpreadsheetReader.read("pembeli.csv", ByteArrayInputStream(csv.toByteArray()))
        assertEquals(listOf("Nama", "WhatsApp"), sheet.headers)
        assertEquals(listOf("Sari; Bu", "0812 3333 4444"), sheet.rows[0].cells)
        assertEquals(listOf("Joko", ""), sheet.rows[1].cells)
        assertEquals(4, sheet.rows[1].number)
    }

    @Test fun guesses_name_and_phone_columns() {
        assertEquals(0 to 1, SpreadsheetReader.guessColumns(listOf("Nama Pembeli", "No HP", "Alamat")))
        assertEquals(1 to 0, SpreadsheetReader.guessColumns(listOf("WhatsApp", "Customer")))
        assertEquals(null to null, SpreadsheetReader.guessColumns(listOf("Kolom A", "Kolom B")))
    }

    @Test fun rejects_file_over_limit() {
        assertThrows(IllegalArgumentException::class.java) {
            SpreadsheetReader.read("pembeli.csv", ByteArrayInputStream(ByteArray(2_000_001) { 'a'.code.toByte() }))
        }
    }
}
