package com.inwords.expenses.benchmarks.network

import com.inwords.expenses.core.ktor_client_cronet.CronetConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.utils.dropCompressionHeaders
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.Attributes
import io.ktor.util.date.GMTDate
import io.ktor.util.flattenForEach
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resumeWithException

internal class LegacyBufferedCronet(
    private val preconfigured: org.chromium.net.CronetEngine,
) : HttpClientEngineFactory<CronetConfig> {
    override fun create(block: CronetConfig.() -> Unit): HttpClientEngine {
        return LegacyBufferedCronetEngine(CronetConfig(preconfigured).apply(block))
    }
}

private class LegacyBufferedCronetEngine(
    override val config: CronetConfig,
) : HttpClientEngineBase("ktor-cronet-buffered-benchmark-baseline") {

    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = emptySet()

    private val executor by lazy { dispatcher.asExecutor() }

    @InternalAPI
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val requestTime = GMTDate()
        val responseCache = ByteArrayOutputStream()
        val receiveChannel = Channels.newChannel(responseCache)

        return suspendCancellableCoroutine { continuation ->
            val callback = object : UrlRequest.Callback() {
                override fun onRedirectReceived(
                    request: UrlRequest,
                    info: UrlResponseInfo,
                    newLocationUrl: String,
                ) {
                    if (config.followRedirects) {
                        request.followRedirect()
                    } else {
                        request.cancel()
                        continuation.resume(
                            info.toLegacyResponseData(
                                method = data.method,
                                attributes = data.attributes,
                                requestTime = requestTime,
                                callContext = callContext,
                                responseBody = ByteReadChannel.Empty,
                            ),
                            onCancellation = { _, _, _ -> },
                        )
                    }
                }

                override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                    request.read(ByteBuffer.allocateDirect(config.responseBufferSize))
                }

                override fun onReadCompleted(
                    request: UrlRequest,
                    info: UrlResponseInfo,
                    byteBuffer: ByteBuffer,
                ) {
                    byteBuffer.flip()
                    receiveChannel.write(byteBuffer)
                    byteBuffer.clear()
                    request.read(byteBuffer)
                }

                override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                    continuation.resume(
                        info.toLegacyResponseData(
                            method = data.method,
                            attributes = data.attributes,
                            requestTime = requestTime,
                            callContext = callContext,
                            responseBody = ByteReadChannel(responseCache.toByteArray()),
                        ),
                        onCancellation = { _, _, _ -> },
                    )
                }

                override fun onFailed(
                    request: UrlRequest,
                    info: UrlResponseInfo?,
                    error: CronetException,
                ) {
                    continuation.resumeWithException(error)
                }

                override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                    continuation.resumeWithException(CancellationException("Request was cancelled"))
                }
            }
            val request = config.preconfigured.newUrlRequestBuilder(
                data.url.toString(),
                callback,
                executor,
            ).apply {
                setHttpMethod(data.method.value)
                data.headers.flattenForEach(::addHeader)
            }.build()

            continuation.invokeOnCancellation { request.cancel() }
            request.start()
        }
    }
}

@OptIn(InternalAPI::class)
private fun UrlResponseInfo.toLegacyResponseData(
    method: HttpMethod,
    attributes: Attributes,
    requestTime: GMTDate,
    callContext: CoroutineContext,
    responseBody: ByteReadChannel,
): HttpResponseData {
    return HttpResponseData(
        statusCode = HttpStatusCode.fromValue(httpStatusCode),
        requestTime = requestTime,
        headers = Headers.build {
            allHeaders.forEach { (key, values) -> appendAll(key, values) }
            dropCompressionHeaders(method, attributes)
        },
        version = when (negotiatedProtocol) {
            "h2" -> HttpProtocolVersion.HTTP_2_0
            "h3" -> HttpProtocolVersion.HTTP_3_0
            "quic/1+spdy/3" -> HttpProtocolVersion.SPDY_3
            else -> HttpProtocolVersion.HTTP_1_1
        },
        body = responseBody,
        callContext = callContext,
    )
}
