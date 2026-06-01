package com.kaizenll.xpendiq.parser

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs the live SmsParser against samples/sms.csv if present. Reports counts; does not
 * hard-fail on coverage thresholds yet — the goal is to keep this visible while we iterate.
 */
class CorpusCoverageTest {

    private val parser = SmsParser()

    @Test fun `report parse coverage on real corpus`() {
        val csv = locateCorpus()
        assumeTrue("samples/sms.csv not found — skipping", csv != null)

        val rows = readCsv(csv!!)
        var bankLike = 0
        var parsedCount = 0
        var highConf = 0
        var lowConf = 0
        var dropped = 0
        val dropReasonCounts = mutableMapOf<SmsFilter.Decision, Int>()
        val byExtractor = mutableMapOf<String, Int>()

        for ((sender, body) in rows) {
            val decision = SmsFilter.classify(sender, body)
            if (decision == SmsFilter.Decision.DROP_NON_BANK) continue
            bankLike++
            if (decision != SmsFilter.Decision.PARSE) {
                dropped++
                dropReasonCounts[decision] = (dropReasonCounts[decision] ?: 0) + 1
                continue
            }
            val p = parser.parse(sender, body, System.currentTimeMillis())
            if (p != null) {
                parsedCount++
                if (p.confidence == ParseConfidence.HIGH) highConf++ else lowConf++
                byExtractor[p.extractor] = (byExtractor[p.extractor] ?: 0) + 1
            }
        }

        println("=== Corpus coverage ===")
        println("bank-shortcode messages: $bankLike")
        println("dropped by filters:      $dropped")
        for ((reason, count) in dropReasonCounts) println("  $reason: $count")
        println("parsed transactions:     $parsedCount  (high=$highConf, low=$lowConf)")
        println("unparsed (post-filter):  ${bankLike - dropped - parsedCount}")
        println()
        println("By extractor:")
        for ((name, count) in byExtractor.entries.sortedByDescending { it.value }) {
            println("  ${name.padEnd(28)} $count")
        }
    }

    private fun locateCorpus(): File? {
        val candidates = listOf(
            File("../samples/sms.csv"),
            File("samples/sms.csv"),
            File("../../samples/sms.csv"),
        )
        return candidates.firstOrNull { it.exists() }
    }

    private fun readCsv(file: File): List<Pair<String, String>> {
        val text = file.readText(Charsets.UTF_8)
        // Skip the first 4 header-ish lines (3 preamble + 1 column header).
        var start = 0
        var skipped = 0
        while (skipped < 4 && start < text.length) {
            val nl = text.indexOf('\n', start)
            if (nl < 0) return emptyList()
            start = nl + 1
            skipped++
        }

        val rows = mutableListOf<Pair<String, String>>()
        val sb = StringBuilder()
        var inQuote = false
        var i = start
        while (i < text.length) {
            val ch = text[i]
            if (ch == '"') {
                inQuote = !inQuote
                sb.append(ch)
            } else if (ch == '\n' && !inQuote) {
                parseRow(sb.toString())?.let { rows += it }
                sb.clear()
            } else if (ch == '\r') {
                // skip
            } else {
                sb.append(ch)
            }
            i++
        }
        if (sb.isNotEmpty()) parseRow(sb.toString())?.let { rows += it }
        return rows
    }

    private fun parseRow(row: String): Pair<String, String>? {
        // CSV columns: DateTime,Direction,Contact,Phone,Content,Type
        val cells = splitCsv(row)
        if (cells.size < 6) return null
        val contact = cells[2]
        val content = cells[4]
        if (contact.isEmpty() || content.isEmpty()) return null
        return contact to content
    }

    private fun splitCsv(row: String): List<String> {
        val out = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < row.length) {
            val c = row[i]
            when {
                c == '"' && inQuote && i + 1 < row.length && row[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> inQuote = !inQuote
                c == ',' && !inQuote -> { out += cell.toString(); cell.clear() }
                else -> cell.append(c)
            }
            i++
        }
        out += cell.toString()
        return out
    }
}
