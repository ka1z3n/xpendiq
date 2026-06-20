package com.kaizenll.xpendiq.util

/**
 * Minimal RFC-4180 CSV codec. Handles the cases our backup needs: fields containing commas,
 * double quotes (escaped as ""), and newlines (SMS bodies are multi-line) by quoting them.
 */
object Csv {

    /** Encode one record. Fields are quoted only when they contain a delimiter, quote, or newline. */
    fun encodeRow(fields: List<String>): String =
        fields.joinToString(",") { encodeField(it) }

    private fun encodeField(s: String): String {
        val needsQuote = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuote) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    /**
     * Parse a whole CSV document into rows of fields. Quoted fields may span newlines and contain
     * escaped quotes. CR/LF and lone LF both terminate a record; a trailing newline yields no
     * empty record.
     */
    fun parse(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val field = StringBuilder()
        var row = ArrayList<String>()
        var inQuotes = false
        var i = 0
        val n = text.length
        var sawAny = false
        while (i < n) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text[i + 1] == '"') { field.append('"'); i += 2; continue }
                    inQuotes = false; i++
                } else {
                    field.append(c); i++
                }
                continue
            }
            when (c) {
                '"' -> { inQuotes = true; sawAny = true; i++ }
                ',' -> { row.add(field.toString()); field.setLength(0); sawAny = true; i++ }
                '\r' -> i++ // swallow; the following \n (or end) closes the record
                '\n' -> {
                    row.add(field.toString()); field.setLength(0)
                    rows.add(row); row = ArrayList(); sawAny = false; i++
                }
                else -> { field.append(c); sawAny = true; i++ }
            }
        }
        if (sawAny || field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }
}
