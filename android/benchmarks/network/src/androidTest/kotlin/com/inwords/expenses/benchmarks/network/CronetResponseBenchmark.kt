package com.inwords.expenses.benchmarks.network

import android.content.Context
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inwords.expenses.core.ktor_client_cronet.Cronet
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.runBlocking
import org.chromium.net.CronetEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class CronetResponseBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var server: LoopbackResponseServer
    private lateinit var cronetEngine: CronetEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        server = LoopbackResponseServer()
        cronetEngine = CronetEngine.Builder(context).build()
    }

    @After
    fun tearDown() {
        cronetEngine.shutdown()
        server.close()
    }

    @Test
    fun buffered32KiB() = measureBuffered(32 * 1024)

    @Test
    fun streaming32KiB() = measure(Cronet(cronetEngine), 32 * 1024)

    @Test
    fun buffered256KiB() = measureBuffered(256 * 1024)

    @Test
    fun streaming256KiB() = measure(Cronet(cronetEngine), 256 * 1024)

    @Test
    fun buffered1MiB() = measureBuffered(1024 * 1024)

    @Test
    fun streaming1MiB() = measure(Cronet(cronetEngine), 1024 * 1024)

    @Test
    fun streaming256KiBResponse64KiBBuffer() = measureStreaming(256 * 1024, 64 * 1024)

    @Test
    fun streaming256KiBResponse100KiBBuffer() = measureStreaming(256 * 1024, 100 * 1024)

    @Test
    fun streaming256KiBResponse128KiBBuffer() = measureStreaming(256 * 1024, 128 * 1024)

    @Test
    fun streaming256KiBResponse256KiBBuffer() = measureStreaming(256 * 1024, 256 * 1024)

    @Test
    fun streaming1MiBResponse64KiBBuffer() = measureStreaming(1024 * 1024, 64 * 1024)

    @Test
    fun streaming1MiBResponse100KiBBuffer() = measureStreaming(1024 * 1024, 100 * 1024)

    @Test
    fun streaming1MiBResponse128KiBBuffer() = measureStreaming(1024 * 1024, 128 * 1024)

    @Test
    fun streaming1MiBResponse256KiBBuffer() = measureStreaming(1024 * 1024, 256 * 1024)

    private fun measureBuffered(responseSize: Int) {
        measure(LegacyBufferedCronet(cronetEngine), responseSize) {
            engine {
                responseBufferSize = 100 * 1024
            }
        }
    }

    private fun measureStreaming(responseSize: Int, responseBufferSize: Int) {
        measure(Cronet(cronetEngine), responseSize) {
            engine {
                this.responseBufferSize = responseBufferSize
            }
        }
    }

    private fun <T : io.ktor.client.engine.HttpClientEngineConfig> measure(
        engineFactory: HttpClientEngineFactory<T>,
        responseSize: Int,
        configure: HttpClientConfig<T>.() -> Unit = {},
    ) {
        val client = HttpClient(engineFactory) {
            followRedirects = false
            configure()
        }
        var consumedBytes = 0L

        client.use { client ->
            benchmarkRule.measureRepeated {
                consumedBytes = runBlocking {
                    client.prepareGet(server.url(responseSize)).execute { response ->
                        response.bodyAsChannel().toByteArray().size.toLong()
                    }
                }
            }
        }

        assertEquals(responseSize.toLong(), consumedBytes)
    }
}
