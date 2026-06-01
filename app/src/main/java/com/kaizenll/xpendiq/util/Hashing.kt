package com.kaizenll.xpendiq.util

import java.security.MessageDigest

object Hashing {
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Stable dedup hash for a transactional SMS. Uses sender + trimmed body only —
     * NOT the parser's interpretation. This way, if the parser improves and produces
     * a different amount/currency from the same SMS later, the new ingest still hits
     * the existing row and dedupes, instead of inserting a second interpretation.
     */
    fun transactionHash(sender: String, body: String): String =
        sha256("$sender|${body.trim()}")
}
