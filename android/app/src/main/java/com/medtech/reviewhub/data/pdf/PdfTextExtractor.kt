package com.medtech.reviewhub.data.pdf

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * BEST-EFFORT PDF text extractor — pure Kotlin, no Android dependencies.
 *
 * What it does:
 *  1. Scans the raw PDF bytes for `stream ... endstream` content blocks.
 *  2. Decodes FlateDecode streams with java.util.zip.Inflater (zlib and raw).
 *  3. Extracts text-showing operators: `(...) Tj`, `[(..) -120 (..)] TJ`, `('`.
 *  4. Handles common string escapes (\n \t \( \) \\ and octal \ddd).
 *
 * What it does NOT do (honest limitations):
 *  - It does not parse the xref table or page tree, so page attribution is
 *    approximate (streams are listed in file order).
 *  - It cannot OCR: scanned PDFs contain no text operators, so extraction
 *    returns empty and callers show:
 *    "No extractable text in this PDF (likely scanned)."
 *  - It ignores Type1/TrueType font encodings beyond ASCII/Latin-1, so
 *    unusual glyphs may come out wrong.
 *  - Encrypted PDFs are not supported (returns empty).
 *
 * This is used only for in-app search; page rendering uses android.graphics.pdf
 * .PdfRenderer which is always accurate.
 */
object PdfTextExtractor {

    const val NO_TEXT_MESSAGE = "No extractable text in this PDF (likely scanned)."

    data class Extraction(val text: String, val streamCount: Int, val inflatedCount: Int) {
        val hasText: Boolean get() = text.isNotBlank()
    }

    fun extract(pdfBytes: ByteArray): Extraction {
        val streams = findStreams(pdfBytes)
        var inflated = 0
        val pieces = mutableListOf<String>()
        for (s in streams) {
            val data = if (s.flated) {
                inflate(s.bytes)?.also { inflated++ } ?: continue
            } else s.bytes
            val text = extractTextOperators(data.toString(Charsets.ISO_8859_1))
            if (text.isNotBlank()) pieces += text
        }
        return Extraction(
            text = pieces.joinToString("\n").trim(),
            streamCount = streams.size,
            inflatedCount = inflated
        )
    }

    /** Search helper: returns matches (with 60-char context) for [query], case-insensitive. */
    fun search(pdfBytes: ByteArray, query: String, contextChars: Int = 60): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val text = extract(pdfBytes).text
        if (text.isEmpty()) return emptyList()
        val results = mutableListOf<String>()
        var idx = 0
        val lower = text.lowercase()
        val lowerQ = q.lowercase()
        while (results.size < 50) {
            val found = lower.indexOf(lowerQ, idx)
            if (found < 0) break
            val start = (found - contextChars).coerceAtLeast(0)
            val end = (found + q.length + contextChars).coerceAtMost(text.length)
            results += (if (start > 0) "…" else "") + text.substring(start, end).replace('\n', ' ') + (if (end < text.length) "…" else "")
            idx = found + q.length
        }
        return results
    }

    // ------------------------------------------------------------------
    // Stream discovery
    // ------------------------------------------------------------------

    private data class StreamBlock(val bytes: ByteArray, val flated: Boolean)

    private fun findStreams(bytes: ByteArray): List<StreamBlock> {
        val out = mutableListOf<StreamBlock>()
        var i = 0
        val n = bytes.size
        while (i < n - 6) {
            // Find "stream" keyword followed by EOL.
            if (matches(bytes, i, "stream")) {
                var headerEnd = i + "stream".length
                if (headerEnd < n && bytes[headerEnd] == '\r'.code.toByte()) headerEnd++
                if (headerEnd < n && bytes[headerEnd] == '\n'.code.toByte()) headerEnd++
                val dictStart = findDictStart(bytes, i)
                val dict = if (dictStart >= 0) bytes.copyOfRange(dictStart, i).toString(Charsets.ISO_8859_1) else ""
                val flated = dict.contains("FlateDecode")
                // Find "endstream"
                var end = indexOf(bytes, headerEnd, "endstream")
                if (end < 0) end = n
                var dataEnd = end
                // Strip trailing EOL before endstream
                while (dataEnd > headerEnd && (bytes[dataEnd - 1] == '\n'.code.toByte() || bytes[dataEnd - 1] == '\r'.code.toByte())) dataEnd--
                out += StreamBlock(bytes.copyOfRange(headerEnd, dataEnd), flated)
                i = end + "endstream".length
            } else {
                i++
            }
        }
        return out
    }

    private fun matches(bytes: ByteArray, at: Int, token: String): Boolean {
        if (at + token.length > bytes.size) return false
        for (k in token.indices) if (bytes[at + k] != token[k].code.toByte()) return false
        return true
    }

    private fun indexOf(bytes: ByteArray, from: Int, token: String): Int {
        var i = from
        while (i <= bytes.size - token.length) {
            if (matches(bytes, i, token)) return i
            i++
        }
        return -1
    }

    /**
     * Finds the dictionary that precedes the `stream` keyword. Standard PDFs
     * write `N 0 obj << ...dict... >> stream`, so we anchor on the nearest
     * `obj` keyword before `stream` and take the first `<<` after it. This
     * correctly handles nested dictionaries (e.g. /Resources) inside the dict.
     */
    private fun findDictStart(bytes: ByteArray, streamKeywordAt: Int): Int {
        // Search for "obj" backwards within a bounded window.
        val windowStart = (streamKeywordAt - 1024).coerceAtLeast(0)
        var objAt = -1
        var i = streamKeywordAt - 1
        while (i >= windowStart) {
            if (matches(bytes, i, "obj")) { objAt = i; break }
            i--
        }
        if (objAt < 0) return -1
        val open = indexOf(bytes, objAt, "<<")
        return if (open in 0 until streamKeywordAt) open else -1
    }

    // ------------------------------------------------------------------
    // FlateDecode
    // ------------------------------------------------------------------

    fun inflate(data: ByteArray): ByteArray? {
        // Try zlib-wrapped first, then raw deflate.
        for (nowrap in listOf(false, true)) {
            val result = runCatching { inflateOnce(data, nowrap) }.getOrNull()
            if (result != null && result.isNotEmpty()) return result
        }
        return null
    }

    private fun inflateOnce(data: ByteArray, nowrap: Boolean): ByteArray? {
        val inflater = Inflater(nowrap)
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count == 0 && inflater.needsInput()) break
                if (count == 0 && inflater.needsDictionary()) return null
                out.write(buf, 0, count)
            }
            if (out.size() == 0) null else out.toByteArray()
        } catch (e: Exception) {
            null
        } finally {
            inflater.end()
        }
    }

    // ------------------------------------------------------------------
    // Text-showing operators
    // ------------------------------------------------------------------

    fun extractTextOperators(content: String): String {
        val sb = StringBuilder()
        var i = 0
        val n = content.length
        while (i < n) {
            val c = content[i]
            when {
                c == '(' -> {
                    val (text, end) = readLiteralString(content, i)
                    // Decide whether this string is used with Tj / ' / TJ
                    val after = content.substring(end, minOf(end + 24, n)).trimStart()
                    if (after.startsWith("Tj") || after.startsWith("'") || after.startsWith("TJ") || after.startsWith("\"")) {
                        sb.append(text)
                    }
                    i = end
                }
                c == '<' && i + 1 < n && content[i + 1] != '<' -> {
                    val end = content.indexOf('>', i + 1).let { if (it < 0) n else it + 1 }
                    val hex = content.substring(i + 1, end - 1)
                    val after = content.substring(end, minOf(end + 24, n)).trimStart()
                    if (after.startsWith("Tj") || after.startsWith("'") || after.startsWith("TJ")) {
                        sb.append(hexToString(hex))
                    }
                    i = end
                }
                c == '[' -> {
                    // TJ array: [(Hel) -200 (lo)] TJ — collect strings, large kerning => space
                    val end = content.indexOf(']', i + 1).let { if (it < 0) n else it + 1 }
                    val inner = content.substring(i + 1, end - 1)
                    val after = content.substring(end, minOf(end + 12, n)).trimStart()
                    if (after.startsWith("TJ") || after.startsWith("'")) {
                        sb.append(extractTjArray(inner))
                    }
                    i = end
                }
                c == 'T' && i + 1 < n && (content[i + 1] == '*' || content[i + 1] == 'd') -> {
                    // T* / Td / TD / Tm line positioning → newline hint
                    if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
                    i += 2
                }
                else -> i++
            }
        }
        return sb.toString().replace(Regex("\n{3,}"), "\n\n")
    }

    private fun extractTjArray(inner: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < inner.length) {
            when (inner[i]) {
                '(' -> {
                    val (text, end) = readLiteralString(inner, i)
                    sb.append(text)
                    i = end
                }
                '<' -> {
                    val end = inner.indexOf('>', i + 1).let { if (it < 0) inner.length else it + 1 }
                    sb.append(hexToString(inner.substring(i + 1, end - 1)))
                    i = end
                }
                '-' -> {
                    val end = i + 1
                    var j = end
                    while (j < inner.length && (inner[j].isDigit() || inner[j] == '.')) j++
                    val num = inner.substring(end, j).toDoubleOrNull() ?: 0.0
                    if (num < -100 && sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
                    i = j
                }
                else -> i++
            }
        }
        return sb.toString()
    }

    private fun readLiteralString(s: String, startAt: Int): Pair<String, Int> {
        // s[startAt] == '('
        val sb = StringBuilder()
        var i = startAt + 1
        var depth = 1
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length -> {
                    val next = s[i + 1]
                    when (next) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        '(' -> sb.append('(')
                        ')' -> sb.append(')')
                        '\\' -> sb.append('\\')
                        in '0'..'7' -> {
                            var oct = next.digitToInt()
                            var k = i + 2
                            while (k < s.length && k < i + 4 && s[k] in '0'..'7') {
                                oct = oct * 8 + s[k].digitToInt()
                                k++
                            }
                            sb.append(oct.toChar())
                            i = k - 1
                        }
                        '\n' -> { /* line continuation */ }
                        else -> sb.append(next)
                    }
                    i += 2
                }
                c == '(' -> { depth++; sb.append(c); i++ }
                c == ')' -> {
                    depth--
                    if (depth == 0) return sb.toString() to (i + 1)
                    sb.append(c); i++
                }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString() to s.length
    }

    private fun hexToString(hex: String): String {
        val clean = hex.filter { it.isLetterOrDigit() }
        val out = StringBuilder()
        var i = 0
        while (i + 1 < clean.length) {
            val byte = clean.substring(i, i + 2).toIntOrNull(16) ?: break
            out.append(byte.toChar())
            i += 2
        }
        return out.toString()
    }
}
