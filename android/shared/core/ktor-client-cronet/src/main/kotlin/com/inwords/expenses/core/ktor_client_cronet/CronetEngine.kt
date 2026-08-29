package com.inwords.expenses.core.ktor_client_cronet

import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forEachHeader
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writer
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import org.chromium.net.UploadDataProvider
import org.chromium.net.apihelpers.UploadDataProviders

class CronetEngine internal constructor(
    override val config: CronetConfig,
) : HttpClientEngineBase("ktor-cronet") {

    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = emptySet()

    @InternalAPI
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val requestTime = GMTDate()
        val uploadDataProvider = data.body.toUploadDataProvider()
        val requestDispatcher = dispatcher.limitedParallelism(1)
        val requestExecutor = requestDispatcher.asExecutor()

        return suspendCancellableCoroutine { continuation ->
            val callback = CronetRequestCallback(
                config = config,
                data = data,
                requestTime = requestTime,
                callContext = callContext,
                requestDispatcher = requestDispatcher,
                requestExecutor = requestExecutor,
                continuation = continuation,
            )
            val request = config.preconfigured.newUrlRequestBuilder(
                /* url = */ data.url.toString(),
                /* callback = */ callback,
                /* executor = */ requestExecutor,
            ).apply {
                setHttpMethod(data.method.value)

                var hasContentType = false
                data.forEachHeader { key, value ->
                    if (key.equals(HttpHeaders.ContentType, ignoreCase = true)) {
                        hasContentType = true
                    }
                    addHeader(key, value)
                }

                if (uploadDataProvider != null && !hasContentType) {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                }

                uploadDataProvider?.let { setUploadDataProvider(it, requestExecutor) }
            }.build()

            callback.start(request)
        }
    }
}

private suspend fun OutgoingContent.toUploadDataProvider(): UploadDataProvider? {
    return when (val outgoingContent = this) {
        is OutgoingContent.NoContent -> null

        is OutgoingContent.ContentWrapper -> outgoingContent.delegate().toUploadDataProvider()

        is OutgoingContent.ByteArrayContent -> {
            UploadDataProviders.create(outgoingContent.bytes())
        }

        is OutgoingContent.ReadChannelContent -> {
            UploadDataProviders.create(outgoingContent.readFrom().toByteArray())
        }

        is OutgoingContent.WriteChannelContent -> coroutineScope {
            UploadDataProviders.create(
                writer {
                    outgoingContent.writeTo(channel)
                }.channel.toByteArray()
            )
        }

        is OutgoingContent.ProtocolUpgrade -> error("UnsupportedContentType $this")
    }
}
