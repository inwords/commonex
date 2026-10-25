package com.inwords.expenses.feature.banner.data.network

import com.inwords.expenses.core.network.HostConfig
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.banner.domain.Banner
import com.inwords.expenses.feature.banner.domain.BannerResult
import com.inwords.expenses.feature.banner.domain.BannerType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

internal class BannerRemoteStoreImplTest {

    @Test
    fun `getBanner parses supported types and uses the banner endpoint`() = runTest {
        for ((wireType, type) in listOf(
            "general_warning" to BannerType.GeneralWarning,
            "update_required" to BannerType.UpdateRequired,
        )) {
            createClient { request ->
                assertEquals(HttpMethod.Get, request.method)
                assertEquals("/api/user/banner", request.url.encodedPath)
                respond(
                    content = """{"type":"$wireType","text":"Message"}""",
                    headers = jsonHeaders,
                )
            }.use { client ->
                assertEquals(
                    IoResult.Success(BannerResult(Banner(type, "Message"))),
                    createStore(client).getBanner(),
                )
            }
        }
    }

    @Test
    fun `getBanner returns an empty 204 response without decoding a body`() = runTest {
        createClient { respond("", HttpStatusCode.NoContent) }.use { client ->
            assertEquals(IoResult.Success(BannerResult(null)), createStore(client).getBanner())
        }
    }

    @Test
    fun `getBanner ignores blank text`() = runTest {
        createClient {
            respond("""{"type":"general_warning","text":" \n\t"}""", headers = jsonHeaders)
        }.use { client ->
            assertEquals(IoResult.Success(BannerResult(null)), createStore(client).getBanner())
        }
    }

    @Test
    fun `getBanner reports malformed JSON and unknown types as a failure`() = runTest {
        for (body in listOf("""{"type":"general_warning""", """{"type":"future_type","text":"Message"}""")) {
            createClient { respond(body, headers = jsonHeaders) }.use { client ->
                assertEquals(IoResult.Error.Failure, createStore(client).getBanner())
            }
        }
    }

    @Test
    fun `getBanner preserves HTTP failure classification`() = runTest {
        for ((status, expected) in listOf(
            HttpStatusCode.BadRequest to IoResult.Error.Failure,
            HttpStatusCode.NotFound to IoResult.Error.Failure,
            HttpStatusCode.ServiceUnavailable to IoResult.Error.Retry,
        )) {
            createClient { respond("Error", status) }.use { client ->
                assertEquals(expected, createStore(client).getBanner())
            }
        }
    }

    @Test
    fun `getBanner preserves transport failure classification`() = runTest {
        createClient { throw IOException("Offline") }.use { client ->
            assertEquals(IoResult.Error.Retry, createStore(client).getBanner())
        }
    }

    private fun createClient(handler: MockRequestHandler): HttpClient = HttpClient(MockEngine) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine { addHandler(handler) }
    }

    private fun createStore(client: HttpClient): BannerRemoteStoreImpl = BannerRemoteStoreImpl(
        client = { client },
        hostConfig = HostConfig(URLProtocol.HTTPS, "commonex.test"),
    )

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
}
