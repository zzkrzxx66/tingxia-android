package com.tingxia.app.data.repo

import com.tingxia.app.data.policy.SafPermissionPolicy
import com.tingxia.app.data.policy.SafPermissionPolicy.BookRef
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises real [SafPermissionPolicy] production code. */
class SafPermissionPolicyTest {

    @Test
    fun releaseWhenLastBookRemoved() {
        val remaining = listOf(BookRef(2, "content://tree/b"))
        assertTrue(SafPermissionPolicy.shouldReleaseRoot("content://tree/a", remaining))
    }

    @Test
    fun keepWhenSharedRootRemains() {
        val remaining = listOf(
            BookRef(2, "content://tree/a"),
            BookRef(3, "content://tree/b"),
        )
        assertFalse(SafPermissionPolicy.shouldReleaseRoot("content://tree/a", remaining))
    }

    @Test
    fun importFailure_releaseOnlyIfNotShared() {
        assertFalse(
            SafPermissionPolicy.shouldReleaseAfterFailedTake(
                rootUri = "content://tree/x",
                permissionWasNewlyTaken = true,
                booksUsingRoot = 1,
            ),
        )
        assertTrue(
            SafPermissionPolicy.shouldReleaseAfterFailedTake(
                rootUri = "content://tree/x",
                permissionWasNewlyTaken = true,
                booksUsingRoot = 0,
            ),
        )
        assertFalse(
            SafPermissionPolicy.shouldReleaseAfterFailedTake(
                rootUri = "content://tree/x",
                permissionWasNewlyTaken = false,
                booksUsingRoot = 0,
            ),
        )
    }

    @Test
    fun reauth_sameRootAlwaysOk() {
        assertTrue(
            SafPermissionPolicy.isAcceptableReauthTree(
                oldRootUri = "content://tree/a",
                newRootUri = "content://tree/a",
                oldFileNames = listOf("01.mp3", "02.mp3"),
                newFileNames = emptyList(),
            ),
        )
    }

    @Test
    fun reauth_rejectsUnrelatedTree() {
        assertFalse(
            SafPermissionPolicy.isAcceptableReauthTree(
                oldRootUri = "content://tree/a",
                newRootUri = "content://tree/b",
                oldFileNames = listOf("01.mp3", "02.mp3", "03.mp3"),
                newFileNames = listOf("other.mp3"),
            ),
        )
    }

    @Test
    fun reauth_acceptsEnoughOverlap() {
        assertTrue(
            SafPermissionPolicy.isAcceptableReauthTree(
                oldRootUri = "content://tree/a",
                newRootUri = "content://tree/b",
                oldFileNames = listOf("01.mp3", "02.mp3", "03.mp3"),
                newFileNames = listOf("01.mp3", "02.mp3", "x.mp3"),
            ),
        )
    }
}
