package com.tingxia.app.data.importer

import com.tingxia.app.data.model.Chapter

/**
 * Builds a deterministic rescan plan from matcher output.
 */
object RescanPlanner {
    sealed interface ChapterChange {
        data class Unchanged(val chapter: Chapter, val scanned: ScannedChapter) : ChapterChange
        data class Renamed(val chapter: Chapter, val scanned: ScannedChapter, val score: Int, val reason: String) : ChapterChange
        data class Added(val scanned: ScannedChapter) : ChapterChange
        data class Removed(val chapter: Chapter) : ChapterChange
        data class WeakMatch(val chapter: Chapter, val scanned: ScannedChapter, val score: Int, val reason: String) : ChapterChange
        data class Ambiguous(
            val scanned: ScannedChapter,
            val candidates: List<ChapterMatcher.Score>,
        ) : ChapterChange
    }

    data class Plan(
        val bookId: Long,
        val changes: List<ChapterChange>,
        val autoMatches: Map<Long, ScannedChapter>,
        val weakMatches: Map<Long, ScannedChapter>,
        val added: List<ScannedChapter>,
        val removed: List<Chapter>,
        val ambiguous: List<ChapterMatcher.AmbiguousPair>,
        val finalChaptersPreview: List<ScannedChapter>,
    ) {
        val addedCount get() = added.size
        val removedCount get() = removed.size
        val renamedCount get() = changes.count { it is ChapterChange.Renamed || it is ChapterChange.WeakMatch }
        val ambiguousCount get() = ambiguous.size
        val unchangedCount get() = changes.count { it is ChapterChange.Unchanged }
        val hasUserDecisions get() = weakMatches.isNotEmpty() || ambiguous.isNotEmpty()
    }

    fun plan(
        bookId: Long,
        existing: List<Chapter>,
        scanned: List<ScannedChapter>,
        acceptedWeak: Map<Long, ScannedChapter> = emptyMap(),
        acceptedAmbiguous: Map<String, Long> = emptyMap(), // scanned.uri -> oldChapterId
        rejectedWeak: Set<Long> = emptySet(),
        rejectedAmbiguous: Set<String> = emptySet(),
    ): Plan {
        val base = ChapterMatcher.match(existing, scanned)
        // Apply user-accepted weak/ambiguous into strong set.
        val strong = base.matches.toMutableMap()
        val weak = base.weakMatches.toMutableMap()
        val removedIds = base.removed.map { it.id }.toMutableSet()
        val added = base.added.toMutableList()
        val ambiguousLeft = base.ambiguous.toMutableList()

        acceptedWeak.forEach { (oldId, sc) ->
            if (weak.containsKey(oldId) || base.weakMatches.containsKey(oldId)) {
                strong[oldId] = sc
                weak.remove(oldId)
                added.removeAll { it.uri == sc.uri }
                removedIds.remove(oldId)
            }
        }
        // also allow accepting currently weak by caller re-pass
        weak.putAll(base.weakMatches.filterKeys { !strong.containsKey(it) })
        acceptedWeak.keys.forEach { weak.remove(it) }
        rejectedWeak.forEach { oldId ->
            base.weakMatches[oldId]?.let { added += it }
            weak.remove(oldId)
            removedIds.add(oldId)
        }

        val iter = ambiguousLeft.iterator()
        val claimedOldIds = strong.keys.toMutableSet()
        while (iter.hasNext()) {
            val amb = iter.next()
            val chosenOldId = acceptedAmbiguous[amb.scanned.uri]
            if (chosenOldId != null) {
                require(amb.candidates.any { it.oldChapter.id == chosenOldId }) { "所选章节不在歧义候选中" }
                require(claimedOldIds.add(chosenOldId)) { "同一旧章节不能匹配多个新文件" }
                strong[chosenOldId] = amb.scanned
                removedIds.remove(chosenOldId)
                added.removeAll { it.uri == amb.scanned.uri }
                iter.remove()
            } else if (amb.scanned.uri in rejectedAmbiguous) {
                added += amb.scanned
                iter.remove()
            }
        }

        val removed = existing.filter { it.id in removedIds && it.id !in strong.keys && it.id !in weak.keys }
        val changes = mutableListOf<ChapterChange>()

        existing.forEach { old ->
            val sc = strong[old.id]
            if (sc != null) {
                val sameUri = old.uri == sc.uri
                val samePath = ChapterIdentity.normalizeRelativePath(old.relativePath.ifBlank { old.fileName }) ==
                    ChapterIdentity.normalizeRelativePath(sc.relativePath)
                if (sameUri && samePath) {
                    changes += ChapterChange.Unchanged(old, sc)
                } else {
                    val s = ChapterMatcher.score(old, sc, existing, scanned)
                    changes += ChapterChange.Renamed(old, sc, s?.score ?: 0, s?.reason ?: "matched")
                }
            } else if (weak.containsKey(old.id)) {
                val scw = weak.getValue(old.id)
                val s = ChapterMatcher.score(old, scw, existing, scanned)
                changes += ChapterChange.WeakMatch(old, scw, s?.score ?: 45, s?.reason ?: "weak")
            } else if (removed.any { it.id == old.id }) {
                changes += ChapterChange.Removed(old)
            }
        }
        added.forEach { changes += ChapterChange.Added(it) }
        ambiguousLeft.forEach { changes += ChapterChange.Ambiguous(it.scanned, it.candidates) }

        // Preview final order = scanned natural order (source of truth for files on disk).
        return Plan(
            bookId = bookId,
            changes = changes,
            autoMatches = strong.toMap(),
            weakMatches = weak.toMap(),
            added = added.toList(),
            removed = removed,
            ambiguous = ambiguousLeft.toList(),
            finalChaptersPreview = scanned,
        )
    }

    /**
     * Choose replacement chapter index when current chapter is removed.
     * Prefer next higher old index among survivors; else previous.
     */
    fun chooseReplacementChapterId(
        oldChapters: List<Chapter>,
        removedIds: Set<Long>,
        currentChapterId: Long?,
        survivingOldIdToNewIndex: Map<Long, Int>,
        addedInOrder: List<ScannedChapter>,
        newChapterIdsInFinalOrder: List<Long>,
    ): Long? {
        if (currentChapterId == null) return newChapterIdsInFinalOrder.firstOrNull()
        if (currentChapterId !in removedIds) {
            // still present
            return currentChapterId
        }
        val old = oldChapters.firstOrNull { it.id == currentChapterId } ?: return newChapterIdsInFinalOrder.firstOrNull()
        // find nearest surviving old chapter by index
        val survivors = oldChapters
            .filter { it.id !in removedIds }
            .sortedBy { it.index }
        val next = survivors.firstOrNull { it.index > old.index }
        val prev = survivors.lastOrNull { it.index < old.index }
        return next?.id ?: prev?.id ?: newChapterIdsInFinalOrder.firstOrNull()
    }
}
