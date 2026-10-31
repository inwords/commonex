package com.inwords.expenses.core.network

import android.content.Context
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.chromium.net.CronetEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class HttpClientFactoryTest {

    @Test
    fun `Android client sends the configured User-Agent`() = runTest {
        val userAgent = "CommonEx/123 (Android/r)"
        val factory = spyk(
            HttpClientFactory(
                context = mockk<Context>(),
                userAgent = userAgent,
                enableLogging = false,
            ),
            recordPrivateCalls = true,
        )
        every { factory["createCronet"]() } returns mockk<CronetEngine>()

        factory.createHttpClient().use { client ->
            client.plugin(HttpSend).intercept { request ->
                assertEquals(listOf(userAgent), request.headers.getAll(HttpHeaders.UserAgent))
                throw RequestCaptured()
            }

            assertFailsWith<RequestCaptured> {
                client.get("https://commonex.test/api/user/banner")
            }
        }
    }

    private class RequestCaptured : RuntimeException()
}
