package com.inwords.expenses.core.ktor_client_cronet

import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.utils.dropCompressionHeaders
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.Attributes
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resumeWithException

internal class CronetRequestCallback(
    private val config: CronetConfig,
    private val data: HttpRequestData,
    private val requestTime: GMTDate,
    private val callContext: CoroutineContext,
    requestDispatcher: CoroutineDispatcher,
    private val requestExecutor: Executor,
    private val continuation: CancellableContinuation<HttpResponseData>,
) : UrlRequest.Callback() {

    private enum class State {
        CREATED,
        AWAITING_HEADERS,
        READING,
        CANCELLING,
        TERMINAL,
    }

    private val output = ByteChannel(autoFlush = true)
    private val pending = Channel<ByteBuffer>(capacity = 1)
    private val responseBody = CancellableByteReadChannel(output, ::abort)

    private val state = AtomicReference(State.CREATED)
    private lateinit var request: UrlRequest

    init {
        CoroutineScope(callContext + requestDispatcher).launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                for (buffer in pending) {
                    output.writeFully(buffer)
                    buffer.clear()
                    readNext(buffer)
                }
                output.close()
            } catch (cause: Throwable) {
                output.cancel(cause)
                abort(cause)
            }
        }
    }

    fun start(request: UrlRequest) {
        this.request = request

        callContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                abort(cause)
            }
        }
        requestExecutor.execute {
            if (state.compareAndSet(State.CREATED, State.AWAITING_HEADERS)) {
                request.start()
            }
        }
    }

    override fun onRedirectReceived(
        request: UrlRequest,
        info: UrlResponseInfo,
        newLocationUrl: String,
    ) {
        if (config.followRedirects) {
            if (state.get() == State.AWAITING_HEADERS) {
                request.followRedirect()
            }
            return
        }

        if (!state.compareAndSet(State.AWAITING_HEADERS, State.TERMINAL)) return

        pending.close()
        continuation.resume(
            info.toHttpResponseData(
                method = data.method,
                attributes = data.attributes,
                requestTime = requestTime,
                callContext = callContext,
                responseBody = ByteReadChannel.Empty,
            ),
            onCancellation = { _, _, _ -> },
        )
        request.cancel()
    }

    override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
        if (!state.compareAndSet(State.AWAITING_HEADERS, State.READING)) return

        continuation.resume(
            info.toHttpResponseData(
                method = data.method,
                attributes = data.attributes,
                requestTime = requestTime,
                callContext = callContext,
                responseBody = responseBody,
            ),
            onCancellation = { cause, _, _ -> responseBody.cancel(cause) },
        )

        val responseBuffer = ByteBuffer.allocateDirect(info.responseBufferSize(config.responseBufferSize))
        readNext(responseBuffer)
    }

    override fun onReadCompleted(
        request: UrlRequest,
        info: UrlResponseInfo,
        byteBuffer: ByteBuffer,
    ) {
        if (state.get() != State.READING) return

        byteBuffer.flip()
        if (pending.trySend(byteBuffer).isFailure) {
            abort(IllegalStateException("Cronet response buffer handoff failed"))
        }
    }

    override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
        state.getAndSet(State.TERMINAL)
        pending.close()
    }

    override fun onFailed(
        request: UrlRequest,
        info: UrlResponseInfo?,
        error: CronetException,
    ) {
        val previousState = state.getAndSet(State.TERMINAL)
        if (previousState == State.CANCELLING) return

        cancelResponseBody(error)
        if (previousState == State.AWAITING_HEADERS) {
            continuation.resumeWithException(error)
        }
    }

    override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
        state.getAndSet(State.TERMINAL)
    }

    private fun readNext(buffer: ByteBuffer) {
        if (state.get() == State.READING) {
            request.read(buffer)
        }
    }

    private fun abort(cause: Throwable?) {
        val error = cause ?: CancellationException("Response body was cancelled")
        val previousState = state.getAndUpdate { currentState ->
            when (currentState) {
                State.CREATED -> State.TERMINAL
                State.AWAITING_HEADERS,
                State.READING -> State.CANCELLING

                State.CANCELLING,
                State.TERMINAL -> currentState
            }
        }
        if (previousState == State.CANCELLING || previousState == State.TERMINAL) return

        cancelResponseBody(error)
        if (previousState == State.CREATED) return

        requestExecutor.execute {
            if (state.get() == State.CANCELLING) {
                request.cancel()
            }
        }
    }

    private fun cancelResponseBody(cause: Throwable) {
        pending.close(cause)
        output.cancel(cause)
    }

}

private fun UrlResponseInfo.responseBufferSize(capacity: Int): Int {
    headerValues(HttpHeaders.ContentEncoding)
        .any { it.isNotBlank() && !it.equals("identity", ignoreCase = true) }
        .takeIf { it }
        ?.let { return capacity }

    val contentLength = headerValues(HttpHeaders.ContentLength)
        .firstNotNullOfOrNull(String::toLongOrNull)
        ?.takeIf { it > 0 }
        ?: return capacity

    return if (contentLength < capacity) contentLength.toInt() else capacity
}

private fun UrlResponseInfo.headerValues(name: String): List<String> {
    return allHeaders.entries
        .firstOrNull { (headerName) -> headerName.equals(name, ignoreCase = true) }
        ?.value
        .orEmpty()
}

private class CancellableByteReadChannel(
    private val delegate: ByteReadChannel,
    private val onCancel: (Throwable?) -> Unit,
) : ByteReadChannel by delegate {

    override fun cancel(cause: Throwable?) {
        onCancel(cause)
        delegate.cancel(cause)
    }
}

@OptIn(InternalAPI::class)
private fun UrlResponseInfo.toHttpResponseData(
    method: HttpMethod,
    attributes: Attributes,
    requestTime: GMTDate,
    callContext: CoroutineContext,
    responseBody: ByteReadChannel,
): HttpResponseData {
    return HttpResponseData(
        statusCode = HttpStatusCode.fromValue(httpStatusCode),
        requestTime = requestTime,
        headers = responseHeaders(method, attributes),
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

@OptIn(InternalAPI::class)
private fun UrlResponseInfo.responseHeaders(method: HttpMethod, attributes: Attributes): Headers {
    return Headers.build {
        allHeaders.forEach { (key, values) -> appendAll(key, values) }
        dropCompressionHeaders(method, attributes)
    }
}
