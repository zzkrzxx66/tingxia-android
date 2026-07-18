package com.tingxia.app.data.importer

import com.tingxia.app.data.model.Chapter
import java.util.PriorityQueue
import java.util.Locale

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

        val remainingOldIds = existing.mapTo(linkedSetOf()) { it.id }
        val remainingNewKeys = scanned.mapTo(linkedSetOf(), ::newKey)
        val strong = linkedMapOf<Long, ScannedChapter>()
        val weak = linkedMapOf<Long, ScannedChapter>()
        val ambiguous = mutableListOf<AmbiguousPair>()
        val stats = MatchStats(existing, scanned)
        val allScores = existing.flatMap { old ->
            scanned.mapNotNull { neu -> score(old, neu, stats) }
        }
        val byNew = allScores.groupBy { newKey(it.scanned) }
        val queue = PriorityQueue<Score>(
            compareByDescending<Score> { it.score }
                .thenBy { it.oldChapter.index }
                .thenBy { it.scanned.index }
                .thenBy { it.oldChapter.id },
        ).apply { addAll(allScores) }

        // Greedy best-score matching with a precomputed candidate queue.
        while (remainingOldIds.isNotEmpty() && remainingNewKeys.isNotEmpty()) {
            var pick: Score? = null
            while (queue.isNotEmpty() && pick == null) {
                val candidate = queue.remove()
                if (candidate.oldChapter.id in remainingOldIds && newKey(candidate.scanned) in remainingNewKeys) {
                    pick = candidate
                }
            }
            pick ?: break
            if (pick.score < 45) break

            // Check ambiguity among remaining for this scanned item.
            val pickedKey = newKey(pick.scanned)
            val candidatesForNew = byNew[pickedKey].orEmpty()
                .filter { it.oldChapter.id in remainingOldIds }
                .sortedWith(compareByDescending<Score> { it.score }.thenBy { it.oldChapter.index })

            val top = candidatesForNew.first()
            val second = candidatesForNew.getOrNull(1)
            val gap = top.score - (second?.score ?: 0)

            if (top.score >= 65 && gap >= 15) {
                strong[top.oldChapter.id] = top.scanned
                remainingOldIds.remove(top.oldChapter.id)
                remainingNewKeys.remove(pickedKey)
            } else if (top.score in 45..64 && gap >= 15) {
                weak[top.oldChapter.id] = top.scanned
                remainingOldIds.remove(top.oldChapter.id)
                remainingNewKeys.remove(pickedKey)
            } else if (top.score >= 45) {
                ambiguous += AmbiguousPair(pick.scanned, candidatesForNew.take(3))
                remainingNewKeys.remove(pickedKey)
            } else {
                break
            }
        }

        return MatchResult(
            matches = strong,
            weakMatches = weak,
            ambiguous = ambiguous,
            added = scanned.filter { newKey(it) in remainingNewKeys },
            removed = existing.filter { it.id in remainingOldIds },
        )
    }

    fun score(
        old: Chapter,
        neu: ScannedChapter,
        allOld: List<Chapter>,
        allNew: List<ScannedChapter>,
    ): Score? = score(old, neu, MatchStats(allOld, allNew))

    private fun score(old: Chapter, neu: ScannedChapter, stats: MatchStats): Score? {
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
            val unique = stats.newSizeDurationCount[neu.fileSize to newDurSec] == 1 &&
                stats.oldSizeDurationCount[old.fileSize to oldDurSec] == 1
            if (unique) return Score(old, neu, 70, "size+duration unique")
        }
        if (nameOk && sizeOk) {
            val unique = stats.newNameSizeCount[neu.fileName.lowercase(Locale.ROOT) to neu.fileSize] == 1
            if (unique) return Score(old, neu, 65, "name+size unique")
        }
        if (nameOk) {
            val oldUnique = stats.oldNameCount[old.fileName.lowercase(Locale.ROOT)] == 1
            val newUnique = stats.newNameCount[neu.fileName.lowercase(Locale.ROOT)] == 1
            if (oldUnique && newUnique) return Score(old, neu, 45, "name unique weak")
        }
        return null
    }

    private fun newKey(chapter: ScannedChapter): String = "${chapter.uri}\u0000${chapter.relativePath}"

    private class MatchStats(old: List<Chapter>, new: List<ScannedChapter>) {
        val oldNameCount = old.groupingBy { it.fileName.lowercase(Locale.ROOT) }.eachCount()
        val newNameCount = new.groupingBy { it.fileName.lowercase(Locale.ROOT) }.eachCount()
        val oldSizeDurationCount = old.groupingBy {
            it.fileSize to ChapterIdentity.durationSec(it.durationMs)
        }.eachCount()
        val newSizeDurationCount = new.groupingBy {
            it.fileSize to ChapterIdentity.durationSec(it.durationMs)
        }.eachCount()
        val newNameSizeCount = new.groupingBy { it.fileName.lowercase(Locale.ROOT) to it.fileSize }.eachCount()
    }
}
