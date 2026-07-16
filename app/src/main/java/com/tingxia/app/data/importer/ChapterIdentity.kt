package com.tingxia.app.data.importer

import java.security.MessageDigest
import java.util.Locale

object ChapterIdentity {
    fun normalizeRelativePath(path: String): String {
        return path
            .replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != "." }
            .joinToString("/")
            .lowercase(Locale.ROOT)
    }

    fun durationSec(durationMs: Long): Long = (durationMs.coerceAtLeast(0L) / 1000L)

    fun stableKey(relativePath: String, fileSize: Long, durationMs: Long): String {
        val payload = buildString {
            append(normalizeRelativePath(relativePath))
            append('|')
            append(fileSize.coerceAtLeast(0L))
            append('|')
            append(durationSec(durationMs))
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }
}
