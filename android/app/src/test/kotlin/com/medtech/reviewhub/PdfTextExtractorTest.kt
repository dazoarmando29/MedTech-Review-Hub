package com.medtech.reviewhub

import com.medtech.reviewhub.data.pdf.PdfTextExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/**
 * Best-effort PDF text extractor tests on a tiny hand-built PDF.
 * (A full binary PDF fixture is not bundled; we build a minimal PDF byte
 * stream in-memory, which is feasible because the extractor parses raw
 * content streams rather than requiring a renderer.)
 */
class PdfTextExtractorTest {

    /** Builds a minimal PDF with one uncompressed and one Flate-compressed stream. */
    private fun buildSamplePdf(): ByteArray = buildPdf("Hello MedTech", "Compressed PDF text")

    private fun buildPdf(uncompressedText: String, compressedText: String): ByteArray {
        val uncompressed = "BT /F1 12 Tf 100 700 Td ($uncompressedText) Tj ET"
        val compressed = deflate("BT /F1 12 Tf ($compressedText) Tj ET".toByteArray(Charsets.ISO_8859_1))

        val body = StringBuilder()
        body.append("%PDF-1.4\n")
        val offsets = mutableMapOf<String, Int>()
        offsets["1"] = body.length
        body.append("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n")
        offsets["2"] = body.length
        body.append("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n")
        offsets["3"] = body.length
        body.append("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents [4 0 R 6 0 R] >> endobj\n")
        offsets["4"] = body.length
        body.append("4 0 obj << /Length ${uncompressed.length} >> stream\n")
        body.append(uncompressed)
        body.append("\nendstream endobj\n")
        offsets["5"] = body.length
        body.append("5 0 obj << /Length ${compressed.size} /Filter /FlateDecode >> stream\n")
        body.append(compressed.toString(Charsets.ISO_8859_1))
        body.append("\nendstream endobj\n")
        offsets["6"] = body.length
        body.append("6 0 obj << /Length 0 >> stream\nendstream endobj\n")
        val xrefPos = body.length
        body.append("trailer << /Size 7 /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        return body.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            out.write(buf, 0, deflater.deflate(buf))
        }
        deflater.end()
        return out.toByteArray()
    }

    @Test
    fun `extracts text from uncompressed content stream`() {
        val pdf = buildSamplePdf()
        val extraction = PdfTextExtractor.extract(pdf)
        assertTrue(extraction.streamCount >= 2)
        assertTrue("expected Hello MedTech in: ${extraction.text}", extraction.text.contains("Hello MedTech"))
    }

    @Test
    fun `inflates FlateDecode streams`() {
        val pdf = buildSamplePdf()
        val extraction = PdfTextExtractor.extract(pdf)
        assertTrue("inflated streams expected, got ${extraction.inflatedCount}", extraction.inflatedCount >= 1)
        assertTrue(extraction.text.contains("Compressed PDF text"))
    }

    @Test
    fun `search returns context and respects case-insensitivity`() {
        val pdf = buildSamplePdf()
        val results = PdfTextExtractor.search(pdf, "medtech")
        assertTrue(results.isNotEmpty())
        assertTrue(results.first().contains("MedTech"))
    }

    @Test
    fun `scanned-like PDF with no text operators yields empty extraction`() {
        // Streams containing only image-like bytes and no text-showing operators
        // (like a scanned page image).
        val pdf = buildPdf("", "")
        val extraction = PdfTextExtractor.extract(pdf)
        assertEquals("", extraction.text)
        assertTrue(PdfTextExtractor.NO_TEXT_MESSAGE.contains("scanned"))
    }

    @Test
    fun `handles TJ arrays with kerning`() {
        val content = "BT /F1 12 Tf [(Hel) -300 (lo World)] TJ ET"
        val text = PdfTextExtractor.extractTextOperators(content)
        // large negative kerning becomes a space
        assertTrue(text.contains("Hel"))
        assertTrue(text.contains("lo World"))
    }

    @Test
    fun `handles escaped parentheses in strings`() {
        val content = "BT (Escaped \\(paren\\) here) Tj ET"
        val text = PdfTextExtractor.extractTextOperators(content)
        assertTrue(text.contains("Escaped (paren) here"))
    }
}
