package com.tingxia.app.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure policy tests documenting SAF permission release rules
 * (implementation lives in BookRepository).
 */
class SafPermissionPolicyTest {

    data class BookRef(val id: Long, val rootUri: String)

    private fun shouldRelease(rootUri: String, remaining: List<BookRef>): Boolean =
        remaining.none { it.rootUri == rootUri }

    @Test
    fun releaseWhenLastBookRemoved() {
        val remaining = listOf(BookRef(2, "content://tree/b"))
        assertTrue(shouldRelease("content://tree/a", remaining))
    }

    @Test
    fun keepWhenSharedRootRemains() {
        val remaining = listOf(
            BookRef(2, "content://tree/a"),
            BookRef(3, "content://tree/b"),
        )
        assertFalse(shouldRelease("content://tree/a", remaining))
    }

    @Test
    fun importFailure_releaseOnlyIfNotShared() {
        val alreadyUsedByOther = listOf(BookRef(9, "content://tree/x"))
        assertFalse(shouldRelease("content://tree/x", alreadyUsedByOther))
        assertTrue(shouldRelease("content://tree/x", emptyList()))
    }
}
