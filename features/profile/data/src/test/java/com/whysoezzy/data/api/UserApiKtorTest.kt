package com.whysoezzy.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserApiKtorTest {
    @Test
    fun `delete current user profile sends DELETE to profile and accepts empty no-content response`() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val client = HttpClient(engine) {
            defaultRequest { url("http://test.local/") }
        }

        UserApiKtor(client).deleteCurrentUserProfile()

        assertEquals(HttpMethod.Delete, method)
        assertEquals("/profile", path)
    }
}
