package com.rhys.relation

import com.rhys.relation.model.UserProfileAction
import com.rhys.relation.model.UserProfileActionResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class UserProfileActionResolverTest {

    @Test
    fun `logged out user sees login required`() {
        assertEquals(
            UserProfileAction.LOGIN_REQUIRED,
            UserProfileActionResolver.resolve(isLoggedIn = false, isSelf = false, isFriend = false)
        )
        assertEquals(
            UserProfileAction.LOGIN_REQUIRED,
            UserProfileActionResolver.resolve(isLoggedIn = false, isSelf = true, isFriend = true)
        )
    }

    @Test
    fun `own profile has no relationship actions`() {
        assertEquals(
            UserProfileAction.NONE,
            UserProfileActionResolver.resolve(isLoggedIn = true, isSelf = true, isFriend = false)
        )
    }

    @Test
    fun `friend profile shows message and unfriend`() {
        assertEquals(
            UserProfileAction.MESSAGE_AND_UNFRIEND,
            UserProfileActionResolver.resolve(isLoggedIn = true, isSelf = false, isFriend = true)
        )
    }

    @Test
    fun `non friend profile shows add friend`() {
        assertEquals(
            UserProfileAction.ADD_FRIEND,
            UserProfileActionResolver.resolve(isLoggedIn = true, isSelf = false, isFriend = false)
        )
    }
}
