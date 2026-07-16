package com.tingxia.app.data.policy

/**
 * SAF persistable-permission release / reauth matching rules.
 * Pure so unit tests cover production code, not a copy of it.
 */
object SafPermissionPolicy {
    data class BookRef(val id: Long, val rootUri: String)

    /** Release a root URI only when no remaining book still uses it. */
    fun shouldReleaseRoot(rootUri: String, remainingBooks: List<BookRef>): Boolean =
        remainingBooks.none { it.rootUri == rootUri }

    /**
     * After a failed import/reauth that newly took permission: release only if no book uses it.
     */
    fun shouldReleaseAfterFailedTake(
        rootUri: String,
        permissionWasNewlyTaken: Boolean,
        booksUsingRoot: Int,
    ): Boolean = permissionWasNewlyTaken && booksUsingRoot == 0

    /**
     * Reauth tree is acceptable if either:
     * - it is the same root URI as before, or
     * - at least [minOverlapRatio] of old chapter file names appear in the new scan.
     */
    fun isAcceptableReauthTree(
        oldRootUri: String,
        newRootUri: String,
        oldFileNames: Collection<String>,
        newFileNames: Collection<String>,
        minOverlapRatio: Float = 0.3f,
    ): Boolean {
        if (oldRootUri == newRootUri) return true
        if (oldFileNames.isEmpty()) return newFileNames.isNotEmpty()
        val oldSet = oldFileNames.map { it.lowercase() }.toSet()
        val newSet = newFileNames.map { it.lowercase() }.toSet()
        val overlap = oldSet.intersect(newSet).size
        return overlap.toFloat() / oldSet.size >= minOverlapRatio
    }
}
