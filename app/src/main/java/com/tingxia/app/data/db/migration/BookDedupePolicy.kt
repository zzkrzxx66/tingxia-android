package com.tingxia.app.data.db.migration

/**
 * Choose which book row to keep when multiple books share the same rootUri.
 * Used by MIGRATION_2_3 and unit-tested without Android/Room.
 */
object BookDedupePolicy {
    data class Candidate(
        val id: Long,
        val lastPlayedAt: Long,
        val listenedDurationMs: Long,
        val currentPositionMs: Long,
    )

    /**
     * Prefer the copy the user is most likely still listening to:
     * 1) largest lastPlayedAt
     * 2) then largest listenedDurationMs
     * 3) then largest currentPositionMs
     * 4) then largest id (newest import) as final tie-breaker
     */
    fun selectKeeper(candidates: List<Candidate>): Candidate {
        require(candidates.isNotEmpty()) { "candidates empty" }
        return candidates.maxWith(
            compareBy<Candidate> { it.lastPlayedAt }
                .thenBy { it.listenedDurationMs }
                .thenBy { it.currentPositionMs }
                .thenBy { it.id },
        )
    }
}
