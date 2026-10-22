package com.rhys.relation

import com.rhys.relation.model.FriendRequestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendRequestStatusTest {

    @Test
    fun `known statuses map from raw values`() {
        assertEquals(FriendRequestStatus.PENDING, FriendRequestStatus.fromRaw("pending"))
        assertEquals(FriendRequestStatus.ACCEPTED, FriendRequestStatus.fromRaw("ACCEPTED"))
        assertEquals(FriendRequestStatus.REJECTED, FriendRequestStatus.fromRaw("rejected"))
    }

    @Test
    fun `unknown or blank status is not actionable`() {
        assertEquals(FriendRequestStatus.UNKNOWN, FriendRequestStatus.fromRaw("cancelled"))
        assertEquals(FriendRequestStatus.UNKNOWN, FriendRequestStatus.fromRaw(null))
        assertFalse(FriendRequestStatus.UNKNOWN.isPending)
    }

    @Test
    fun `only pending status is actionable`() {
        assertTrue(FriendRequestStatus.PENDING.isPending)
        assertFalse(FriendRequestStatus.ACCEPTED.isPending)
        assertFalse(FriendRequestStatus.REJECTED.isPending)
    }
}
