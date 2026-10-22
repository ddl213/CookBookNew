package com.example.network.repository

import com.example.network.AuthException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response

class RelationApiCallTest {

    @Test
    fun `successful response completes without throwing`() {
        val response = Response.success(Unit)
        response.requireSuccessful()
    }

    @Test
    fun `404 response maps FRIEND_NOT_FOUND error body`() {
        val response = Response.error<Unit>(
            404,
            errorBody("""{"error":{"code":"FRIEND_NOT_FOUND","message":"This user is not in your friend list"}}""")
        )

        try {
            response.requireSuccessful()
            fail("expected AuthException")
        } catch (e: AuthException) {
            assertEquals("FRIEND_NOT_FOUND", e.code)
            assertEquals(404, e.httpStatus)
            assertEquals("对方不在你的好友列表中", e.message)
        }
    }

    @Test
    fun `401 response is marked unauthorized`() {
        val response = Response.error<Unit>(
            401,
            errorBody("""{"error":{"code":"UNAUTHORIZED","message":"Invalid token"}}""")
        )

        try {
            response.requireSuccessful(unauthorizedMessage = "登录已失效，请重新登录")
            fail("expected AuthException")
        } catch (e: AuthException) {
            assertTrue(e.isUnauthorized)
            assertEquals(401, e.httpStatus)
        }
    }

    private fun errorBody(json: String) = json.toResponseBody("application/json".toMediaType())
}
