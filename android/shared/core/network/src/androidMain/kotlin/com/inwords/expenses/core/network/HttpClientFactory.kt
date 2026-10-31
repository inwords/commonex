package com.inwords.expenses.core.network

import android.content.Context
import androidx.annotation.OptIn
import com.inwords.expenses.core.ktor_client_cronet.Cronet
import com.inwords.expenses.core.utils.IO
import io.ktor.client.HttpClient
import io.ktor.client.plugins.UserAgent
import kotlinx.coroutines.withContext
import org.chromium.net.ConnectionMigrationOptions
import org.chromium.net.CronetEngine
import org.chromium.net.DnsOptions
import org.chromium.net.QuicOptions
import java.io.File

internal actual class HttpClientFactory(
    private val context: Context,
    private val userAgent: String,
    private val enableLogging: Boolean
) {

    actual suspend fun createHttpClient(): HttpClient {
        return withContext(IO) {
            val cronet = createCronet()
            createKtor(cronet)
        }
    }

    @OptIn(
        ConnectionMigrationOptions.Experimental::class,
        DnsOptions.Experimental::class,
        QuicOptions.Experimental::class,
    )
    private fun createCronet(): CronetEngine {
        val storagePath = context.cacheDir.absolutePath + "/cronet"
        val storageFile = File(storagePath)
        if (!storageFile.exists()) {
            storageFile.mkdirs()
        }

        return CronetEngine.Builder(context)
            .setStoragePath(storagePath)
            .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, 10 * 1024 * 1024L)
            .enableHttp2(true)
            .enableQuic(true)
            .enableBrotli(true)
            .setConnectionMigrationOptions(
                ConnectionMigrationOptions.builder()
                    .enableDefaultNetworkMigration(true)
                    .enablePathDegradationMigration(true)
                    .build()
            )
            .setDnsOptions(
                DnsOptions.builder()
                    .persistHostCache(true)
                    .build()
            )
            .setQuicOptions(
                QuicOptions.builder()
                    .enableTlsZeroRtt(true)
                    .setInMemoryServerConfigsCacheSize(5)
                    .delayJobsWithAvailableSpdySession(true)
            )
            .setUserAgent(userAgent)
            .build()
    }

    private fun createKtor(cronetEngine: CronetEngine): HttpClient {
        return createKtor(Cronet(cronetEngine), enableLogging = enableLogging) {
            install(UserAgent) {
                agent = this@HttpClientFactory.userAgent
            }
            engine {
                this.pipelining = true
                this.followRedirects = false
            }
        }
    }

}
