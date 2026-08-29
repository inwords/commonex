package com.inwords.expenses.core.ktor_client_cronet

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UploadDataProvider
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URL
import java.net.URLConnection
import java.nio.ByteBuffer
import java.util.AbstractMap.SimpleImmutableEntry
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class CronetEngineTest {

    private lateinit var fixture: CronetFixture

    @BeforeEach
    fun setUp() {
        fixture = CronetFixture()
    }

    @AfterEach
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun `cancellation during setup does not start request`() = runTest(timeout = 10.seconds) {
        lateinit var response: Deferred<*>
        fixture.cronet.onBuild = { response.cancel() }
        response = async(start = CoroutineStart.LAZY) {
            fixture.client.request("https://example.test/resource")
        }

        response.start()
        response.join()
        withContext(fixture.callbackDispatcher) {}

        assertTrue(response.isCancelled)
        assertEquals(0, fixture.cronet.request.startCalls.get())
    }

    @Test
    fun `not followed redirect completes once as redirect response`() = runTest(timeout = 10.seconds) {
        fixture.cronet.request.emitCanceledOnCancel = true
        val response = async { fixture.client.request("https://example.test/resource") }
        fixture.cronet.request.started.await()

        fixture.cronet.dispatchCallback {
            onRedirectReceived(
                fixture.cronet.request,
                FakeUrlResponseInfo(statusCode = 302),
                "https://example.test/redirected",
            )
        }

        assertEquals(HttpStatusCode.Found, response.await().status)
        fixture.cronet.awaitCallbacks()
        assertTrue(fixture.cronet.callbackFailures.isEmpty())
    }

    @Test
    fun `call cancellation invokes cancel on request executor`() = runTest(timeout = 10.seconds) {
        val cancellerDispatcher = Executors.newSingleThreadExecutor {
            Thread(it, "request-canceller")
        }.asCoroutineDispatcher()

        cancellerDispatcher.use { cancellerDispatcher ->
            val response = async(Dispatchers.Default) {
                fixture.client.request("https://example.test/resource")
            }
            fixture.cronet.request.started.await()

            withContext(cancellerDispatcher) {
                response.cancel()
            }
            response.cancelAndJoin()
            fixture.cronet.request.cancelled.await()

            assertEquals("cronet-callback", fixture.cronet.request.cancelThread.get()?.name)
        }
    }

    @Test
    fun `cancellation before response headers prevents native read`() = runTest(timeout = 10.seconds) {
        val response = async {
            fixture.client.request("https://example.test/resource")
        }
        fixture.cronet.request.started.await()

        response.cancelAndJoin()
        fixture.cronet.request.cancelled.await()
        fixture.cronet.emitResponseStarted()

        assertEquals(0, fixture.cronet.request.readCalls.get())
    }

    @Test
    fun `body headers preserve multipart boundary and content length`() = runTest(timeout = 10.seconds) {
        val contentType = ContentType.MultiPart.FormData.withParameter("boundary", "test-boundary")
        val content = TestByteArrayContent(
            contentType = contentType,
            bytes = byteArrayOf(1, 2),
        )

        val response = async {
            fixture.client.request("https://example.test/resource") {
                method = HttpMethod.Post
                setBody(content)
            }
        }
        fixture.cronet.request.started.await()

        assertEquals(
            listOf(contentType.toString()),
            fixture.cronet.headerValues(HttpHeaders.ContentType),
        )
        assertEquals(listOf("2"), fixture.cronet.headerValues(HttpHeaders.ContentLength))

        response.cancelAndJoin()
    }

    @Test
    fun `upload without content type receives one octet stream fallback`() = runTest(timeout = 10.seconds) {
        val response = async {
            fixture.client.request("https://example.test/resource") {
                method = HttpMethod.Post
                setBody(TestByteArrayContent(contentType = null, bytes = byteArrayOf(1)))
            }
        }
        fixture.cronet.request.started.await()

        assertEquals(
            listOf(ContentType.Application.OctetStream.toString()),
            fixture.cronet.headerValues(HttpHeaders.ContentType),
        )

        response.cancelAndJoin()
    }

    @Test
    fun `write channel upload uses configured engine dispatcher`() = runTest(timeout = 10.seconds) {
        val writeThread = CompletableDeferred<Thread>()
        val response = async {
            fixture.client.request("https://example.test/resource") {
                method = HttpMethod.Post
                setBody(TestWriteChannelContent(writeThread))
            }
        }
        fixture.cronet.request.started.await()

        assertSame(fixture.callbackThread.get(), writeThread.await())

        response.cancelAndJoin()
    }

    @Test
    fun `headers are returned before response succeeds`() = runTest(timeout = 10.seconds) {
        val responseStarted = CompletableDeferred<HttpStatusCode>()
        val releaseResponse = CompletableDeferred<Unit>()
        val response = async {
            fixture.client.prepareRequest("https://example.test/resource").execute { httpResponse ->
                responseStarted.complete(httpResponse.status)
                releaseResponse.await()
            }
        }
        fixture.cronet.request.started.await()

        fixture.cronet.emitResponseStarted()

        assertEquals(HttpStatusCode.OK, responseStarted.await())

        releaseResponse.complete(Unit)
        response.cancelAndJoin()
    }

    @Test
    fun `content length below configured cap right sizes native response buffer`() = runTest(timeout = 10.seconds) {
        fixture.config.responseBufferSize = 256 * 1024
        val responseStarted = CompletableDeferred<ByteReadChannel>()
        val response = async {
            fixture.client.prepareRequest("https://example.test/resource").execute { httpResponse ->
                val channel = httpResponse.bodyAsChannel()
                responseStarted.complete(channel)
                channel.toByteArray()
            }
        }
        fixture.cronet.request.started.await()
        fixture.cronet.emitResponseStarted(
            FakeUrlResponseInfo(
                headers = mapOf(HttpHeaders.ContentLength to listOf((32 * 1024).toString())),
            )
        )
        val channel = responseStarted.await()
        val nativeBuffer = checkNotNull(fixture.cronet.request.takeReadBuffer(1.seconds))

        assertEquals(32 * 1024, nativeBuffer.capacity())

        fixture.cronet.request.completeRead()
        channel.cancel(CancellationException("test cleanup"))
        response.cancelAndJoin()
    }

    @Test
    fun `content length above configured cap keeps native response buffer at cap`() = runTest(timeout = 10.seconds) {
        val configuredBufferSize = 256 * 1024
        fixture.config.responseBufferSize = configuredBufferSize
        val responseStarted = CompletableDeferred<ByteReadChannel>()
        val response = async {
            fixture.client.prepareRequest("https://example.test/resource").execute { httpResponse ->
                val channel = httpResponse.bodyAsChannel()
                responseStarted.complete(channel)
                channel.toByteArray()
            }
        }
        fixture.cronet.request.started.await()
        fixture.cronet.emitResponseStarted(
            FakeUrlResponseInfo(
                headers = mapOf(HttpHeaders.ContentLength to listOf((1024 * 1024).toString())),
            )
        )
        val channel = responseStarted.await()
        val nativeBuffer = checkNotNull(fixture.cronet.request.takeReadBuffer(1.seconds))

        assertEquals(configuredBufferSize, nativeBuffer.capacity())

        fixture.cronet.request.completeRead()
        channel.cancel(CancellationException("test cleanup"))
        response.cancelAndJoin()
    }

    @Test
    fun `compressed content length does not shrink native response buffer`() = runTest(timeout = 10.seconds) {
        val configuredBufferSize = 64 * 1024
        assertEquals(configuredBufferSize, fixture.config.responseBufferSize)
        val responseStarted = CompletableDeferred<ByteReadChannel>()
        val response = async {
            fixture.client.prepareRequest("https://example.test/resource").execute { httpResponse ->
                val channel = httpResponse.bodyAsChannel()
                responseStarted.complete(channel)
                channel.toByteArray()
            }
        }
        fixture.cronet.request.started.await()
        fixture.cronet.emitResponseStarted(
            FakeUrlResponseInfo(
                headers = mapOf(
                    HttpHeaders.ContentLength to listOf((4 * 1024).toString()),
                    HttpHeaders.ContentEncoding to listOf("gzip"),
                ),
            )
        )
        val channel = responseStarted.await()
        val nativeBuffer = checkNotNull(fixture.cronet.request.takeReadBuffer(1.seconds))

        assertEquals(configuredBufferSize, nativeBuffer.capacity())

        fixture.cronet.request.completeRead()
        channel.cancel(CancellationException("test cleanup"))
        response.cancelAndJoin()
    }

    @Test
    fun `response chunks are readable in order before success`() = runTest(timeout = 10.seconds) {
        val responseStarted = CompletableDeferred<Unit>()
        val firstChunk = CompletableDeferred<String>()
        val response = async {
            fixture.client.prepareRequest("https://example.test/resource").execute { httpResponse ->
                val channel = httpResponse.bodyAsChannel()
                responseStarted.complete(Unit)
                firstChunk.complete(channel.readByteArray(5).decodeToString())
                firstChunk.await() + channel.toByteArray().decodeToString()
            }
        }
        fixture.cronet.request.started.await()
        fixture.cronet.emitResponseStarted()
        responseStarted.await()
        fixture.cronet.emitChunk("first".encodeToByteArray())
        assertEquals("first", firstChunk.await())

        fixture.cronet.emitChunk("second".encodeToByteArray())
        fixture.cronet.emitSucceeded()
        assertEquals("firstsecond", response.await())
    }

    @Test
    fun `slow reader stops native reads when response buffer is full`() = runTest(timeout = 10.seconds) {
        fixture.config.responseBufferSize = 512 * 1024
        val responseStarted = CompletableDeferred<ByteReadChannel>()
        val releaseResponse = CompletableDeferred<Unit>()
        val response = async {
            fixture.client.prepareRequest("https://example.test/resource").execute { httpResponse ->
                responseStarted.complete(httpResponse.bodyAsChannel())
                releaseResponse.await()
            }
        }
        fixture.cronet.request.started.await()
        fixture.cronet.emitResponseStarted()
        val channel = responseStarted.await()

        var emittedChunks = 0
        while (emittedChunks < 8 && fixture.cronet.tryEmitChunk(ByteArray(512 * 1024), 300.milliseconds)) {
            emittedChunks++
        }

        assertTrue(emittedChunks < 8)
        assertEquals(512 * 1024, channel.readByteArray(512 * 1024).size)
        assertTrue(fixture.cronet.tryEmitChunk(ByteArray(512 * 1024), 1.seconds))
        channel.cancel(CancellationException("test cleanup"))
        releaseResponse.complete(Unit)
        response.cancelAndJoin()
    }

    @Test
    fun `failure after headers fails response body with original cause`() = runTest(timeout = 10.seconds) {
        val failure = FakeCronetException("stream failed")

        val actual = supervisorScope {
            val responseStarted = CompletableDeferred<Unit>()
            val response = async {
                fixture.client.prepareRequest("https://example.test/resource").execute { httpResponse ->
                    responseStarted.complete(Unit)
                    httpResponse.bodyAsChannel().toByteArray()
                }
            }
            fixture.cronet.request.started.await()
            fixture.cronet.emitResponseStarted()
            responseStarted.await()

            fixture.cronet.emitFailed(failure)
            runCatching { response.await() }.exceptionOrNull()
        }

        assertTrue(generateSequence(actual, Throwable::cause).any { it === failure })
    }

    @Test
    fun `canceling response body cancels native request once`() = runTest(timeout = 10.seconds) {
        val responseStarted = CompletableDeferred<ByteReadChannel>()
        val response = async {
            fixture.client.prepareRequest("https://example.test/resource").execute { httpResponse ->
                val channel = httpResponse.bodyAsChannel()
                responseStarted.complete(channel)
                channel.toByteArray()
            }
        }
        fixture.cronet.request.started.await()
        fixture.cronet.emitResponseStarted()
        val channel = responseStarted.await()

        channel.cancel(CancellationException("consumer stopped"))
        fixture.cronet.request.cancelled.await()

        assertEquals(1, fixture.cronet.request.cancelCalls.get())
        assertEquals("cronet-callback", fixture.cronet.request.cancelThread.get()?.name)
        response.cancelAndJoin()
    }
}

private class CronetFixture {

    val callbackThread = AtomicReference<Thread>()
    val callbackDispatcher = Executors.newSingleThreadExecutor {
        Thread(it, "cronet-callback").also(callbackThread::set)
    }.asCoroutineDispatcher()
    val cronet = FakeCronetEngine()
    val config = CronetConfig(cronet).apply {
        dispatcher = callbackDispatcher
        followRedirects = false
    }
    val engine = CronetEngine(config)
    val client = HttpClient(engine) {
        followRedirects = false
    }

    fun close() {
        client.close()
        callbackDispatcher.close()
    }
}

private class TestByteArrayContent(
    override val contentType: ContentType?,
    private val bytes: ByteArray,
) : OutgoingContent.ByteArrayContent() {

    override val contentLength: Long = bytes.size.toLong()

    override fun bytes(): ByteArray = bytes
}

private class TestWriteChannelContent(
    private val writeThread: CompletableDeferred<Thread>,
) : OutgoingContent.WriteChannelContent() {

    override val contentLength: Long = 1

    override suspend fun writeTo(channel: ByteWriteChannel) {
        writeThread.complete(Thread.currentThread())
        channel.writeFully(byteArrayOf(1))
    }
}

private class FakeCronetEngine : CronetEngine() {

    lateinit var callback: UrlRequest.Callback
    lateinit var callbackExecutor: Executor

    val callbackFailures = ConcurrentLinkedQueue<Throwable>()
    val headers = mutableListOf<Pair<String, String>>()
    val request = FakeUrlRequest(this)

    var onBuild: () -> Unit = {}

    override fun newUrlRequestBuilder(
        url: String,
        callback: UrlRequest.Callback,
        executor: Executor,
    ): UrlRequest.Builder {
        this.callback = callback
        callbackExecutor = executor
        return FakeUrlRequestBuilder(this)
    }

    fun dispatchCallback(block: UrlRequest.Callback.() -> Unit) {
        callbackExecutor.execute {
            runCatching { callback.block() }
                .onFailure(callbackFailures::add)
        }
    }

    suspend fun emitResponseStarted(info: UrlResponseInfo = FakeUrlResponseInfo()) {
        dispatchCallback { onResponseStarted(request, info) }
        awaitCallbacks()
    }

    suspend fun emitChunk(bytes: ByteArray) {
        assertTrue(tryEmitChunk(bytes, 5.seconds))
    }

    suspend fun tryEmitChunk(bytes: ByteArray, timeout: kotlin.time.Duration): Boolean {
        val buffer = request.takeReadBuffer(timeout) ?: return false
        require(bytes.size <= buffer.remaining())
        buffer.put(bytes)
        request.completeRead()
        dispatchCallback { onReadCompleted(request, FakeUrlResponseInfo(), buffer) }
        awaitCallbacks()
        return true
    }

    suspend fun emitSucceeded() {
        checkNotNull(request.takeReadBuffer(5.seconds))
        request.completeRead()
        dispatchCallback { onSucceeded(request, FakeUrlResponseInfo()) }
        awaitCallbacks()
    }

    suspend fun emitFailed(error: CronetException) {
        checkNotNull(request.takeReadBuffer(5.seconds))
        request.completeRead()
        dispatchCallback { onFailed(request, FakeUrlResponseInfo(), error) }
        awaitCallbacks()
    }

    suspend fun awaitCallbacks() {
        val drained = CompletableDeferred<Unit>()
        callbackExecutor.execute { drained.complete(Unit) }
        drained.await()
    }

    fun headerValues(name: String): List<String> {
        return headers
            .filter { (headerName) -> headerName.equals(name, ignoreCase = true) }
            .map(Pair<String, String>::second)
    }

    override fun getVersionString(): String = "fake"

    override fun shutdown() = Unit

    override fun startNetLogToFile(fileName: String, logAll: Boolean) = Unit

    override fun stopNetLog() = Unit

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getGlobalMetricsDeltas(): ByteArray = byteArrayOf()

    override fun openConnection(url: URL): URLConnection = error("Not used")

    override fun createURLStreamHandlerFactory() = error("Not used")

}

private class FakeUrlRequestBuilder(
    private val engine: FakeCronetEngine,
) : UrlRequest.Builder() {

    override fun addHeader(header: String, value: String): UrlRequest.Builder = apply {
        engine.headers += header to value
    }

    override fun allowDirectExecutor(): UrlRequest.Builder = this

    override fun disableCache(): UrlRequest.Builder = this

    override fun setHttpMethod(method: String): UrlRequest.Builder = this

    override fun setPriority(priority: Int): UrlRequest.Builder = this

    override fun setUploadDataProvider(
        uploadDataProvider: UploadDataProvider,
        executor: Executor,
    ): UrlRequest.Builder = this

    override fun build(): UrlRequest {
        engine.onBuild()
        return engine.request
    }
}

private class FakeUrlRequest(
    private val engine: FakeCronetEngine,
) : UrlRequest() {

    val startCalls = AtomicInteger()
    val cancelCalls = AtomicInteger()
    val cancelThread = AtomicReference<Thread?>()
    val readCalls = AtomicInteger()
    val started = CompletableDeferred<Unit>()
    val cancelled = CompletableDeferred<Unit>()

    private val readOutstanding = AtomicBoolean()
    private val readBuffers = LinkedBlockingQueue<ByteBuffer>()

    var emitCanceledOnCancel: Boolean = false

    override fun start() {
        startCalls.incrementAndGet()
        started.complete(Unit)
    }

    override fun cancel() {
        cancelCalls.incrementAndGet()
        cancelThread.set(Thread.currentThread())
        cancelled.complete(Unit)
        if (emitCanceledOnCancel) {
            engine.dispatchCallback {
                onCanceled(this@FakeUrlRequest, null)
            }
        }
    }

    override fun followRedirect() = Unit

    override fun getStatus(listener: StatusListener) = Unit

    override fun read(buffer: ByteBuffer) {
        check(readOutstanding.compareAndSet(false, true))
        readCalls.incrementAndGet()
        readBuffers.add(buffer)
    }

    fun takeReadBuffer(timeout: kotlin.time.Duration): ByteBuffer? {
        return readBuffers.poll(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    }

    fun completeRead() {
        check(readOutstanding.compareAndSet(true, false))
    }

    override fun isDone(): Boolean = false
}

private class FakeCronetException(message: String) : CronetException(message, null)

private class FakeUrlResponseInfo(
    private val statusCode: Int = 200,
    private val headers: Map<String, List<String>> = emptyMap(),
) : UrlResponseInfo() {

    override fun getHttpStatusCode(): Int = statusCode

    override fun getReceivedByteCount(): Long = 0

    override fun getHttpStatusText(): String = ""

    override fun getNegotiatedProtocol(): String = "h2"

    override fun getProxyServer(): String = ""

    override fun getUrl(): String = "https://example.test/resource"

    override fun getAllHeadersAsList(): List<Map.Entry<String, String>> {
        return headers.flatMap { (name, values) ->
            values.map { value -> SimpleImmutableEntry(name, value) }
        }
    }

    override fun getUrlChain(): List<String> = listOf(url)

    override fun getAllHeaders(): Map<String, List<String>> = headers

    override fun wasCached(): Boolean = false
}
