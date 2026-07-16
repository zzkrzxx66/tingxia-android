package com.tingxia.app.data.importer

import com.tingxia.app.data.model.Chapter

/**
 * Pure chapter matching for rescan. JVM-testable; no Android APIs.
 */
object ChapterMatcher {
    data class Score(
        val oldChapter: Chapter,
        val scanned: ScannedChapter,
        val score: Int,
        val reason: String,
    )

    data class MatchResult(
        val matches: Map<Long, ScannedChapter>, // oldChapterId -> scanned
        val weakMatches: Map<Long, ScannedChapter>,
        val ambiguous: List<AmbiguousPair>,
        val added: List<ScannedChapter>,
        val removed: List<Chapter>,
    )

    data class AmbiguousPair(
        val scanned: ScannedChapter,
        val candidates: List<Score>,
    )

    fun match(existing: List<Chapter>, scanned: List<ScannedChapter>): MatchResult {
        if (existing.isEmpty()) {
            return MatchResult(
                matches = emptyMap(),
                weakMatches = emptyMap(),
                ambiguous = emptyList(),
                added = scanned,
                removed = emptyList(),
            )
        }
        if (scanned.isEmpty()) {
            return MatchResult(
                matches = emptyMap(),
                weakMatches = emptyMap(),
                ambiguous = emptyList(),
                added = emptyList(),
                removed = existing,
            )
        }

        val remainingOld = existing.toMutableList()
        val remainingNew = scanned.toMutableList()
        val strong = linkedMapOf<Long, ScannedChapter>()
        val weak = linkedMapOf<Long, ScannedChapter>()
        val ambiguous = mutableListOf<AmbiguousPair>()

        // Greedy best-score matching, highest score first, unique old/new.
        while (remainingOld.isNotEmpty() && remainingNew.isNotEmpty()) {
            var best: Score? = null
            for (old in remainingOld) {
                for (neu in remainingNew) {
                    val s = score(old, neu, existing, scanned)
                    if (s != null && (best == null || s.score > best.score)) {
                        best = s
                    }
                }
            }
            val pick = best ?: break
            if (pick.score < 45) break

            // Check ambiguity among remaining for this scanned item.
            val candidatesForNew = remainingOld.mapNotNull { old ->
                score(old, pick.scanned, existing, scanned)
            }.sortedByDescending { it.score }

            val top = candidatesForNew.first()
            val second = candidatesForNew.getOrNull(1)
            val gap = top.score - (second?.score ?: 0)

            if (top.score >= 65 && gap >= 15) {
                strong[top.oldChapter.id] = top.scanned
                remainingOld.removeAll { it.id == top.oldChapter.id }
                remainingNew.removeAll { it.uri == top.scanned.uri && it.relativePath == top.scanned.relativePath }
            } else if (top.score in 45..64 && gap >= 15) {
                weak[top.oldChapter.id] = top.scanned
                remainingOld.removeAll { it.id == top.oldChapter.id }
                remainingNew.removeAll { it.uri == top.scanned.uri && it.relativePath == top.scanned.relativePath }
            } else if (top.score >= 45) {
                ambiguous += AmbiguousPair(pick.scanned, candidatesForNew.take(3))
                remainingNew.removeAll { it.uri == pick.scanned.uri && it.relativePath == pick.scanned.relativePath }
            } else {
                break
            }
        }

        return MatchResult(
            matches = strong,
            weakMatches = weak,
            ambiguous = ambiguous,
            added = remainingNew.toList(),
            removed = remainingOld.toList(),
        )
    }

    fun score(
        old: Chapter,
        neu: ScannedChapter,
        allOld: List<Chapter>,
        allNew: List<ScannedChapter>,
    ): Score? {
        if (old.uri == neu.uri) {
            return Score(old, neu, 100, "uri")
        }
        if (!old.documentId.isNullOrBlank() && old.documentId == neu.documentId) {
            return Score(old, neu, 95, "documentId")
        }
        val oldPath = ChapterIdentity.normalizeRelativePath(old.relativePath.ifBlank { old.fileName })
        val newPath = ChapterIdentity.normalizeRelativePath(neu.relativePath)
        if (oldPath.isNotEmpty() && oldPath == newPath) {
            return Score(old, neu, 90, "relativePath")
        }
        if (!old.stableKey.isNullOrBlank() && old.stableKey == neu.stableKey) {
            return Score(old, neu, 85, "stableKey")
        }

        val oldDurSec = ChapterIdentity.durationSec(old.durationMs)
        val newDurSec = ChapterIdentity.durationSec(neu.durationMs)
        val sizeOk = old.fileSize > 0L && neu.fileSize > 0L && old.fileSize == neu.fileSize
        val nameOk = old.fileName.equals(neu.fileName, ignoreCase = true)
        val durOk = oldDurSec > 0L && newDurSec > 0L && oldDurSec == newDurSec

        if (nameOk && sizeOk && durOk) {
            return Score(old, neu, 80, "name+size+duration")
        }
        if (sizeOk && durOk) {
            val unique = allNew.count {
                it.fileSize == neu.fileSize && ChapterIdentity.durationSec(it.durationMs) == newDurSec
            } == 1 && allOld.count {
                it.fileSize == old.fileSize && ChapterIdentity.durationSec(it.durationMs) == oldDurSec
            } == 1
            if (unique) return Score(old, neu, 70, "size+duration unique")
        }
        if (nameOk && sizeOk) {
            val unique = allNew.count {
                it.fileName.equals(neu.fileName, ignoreCase = true) && it.fileSize == neu.fileSize
            } == 1
            if (unique) return Score(old, neu, 65, "name+size unique")
        }
        if (nameOk) {
            val oldUnique = allOld.count { it.fileName.equals(old.fileName, ignoreCase = true) } == 1
            val newUnique = allNew.count { it.fileName.equals(neu.fileName, ignoreCase = true) } == 1
            if (oldUnique && newUnique) return Score(old, neu, 45, "name unique weak")
        }
        return null
    }
}
