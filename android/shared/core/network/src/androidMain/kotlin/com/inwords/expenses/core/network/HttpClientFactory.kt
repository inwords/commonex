package com.inwords.expenses.core.network

import android.content.Context
import com.inwords.expenses.core.ktor_client_cronet.Cronet
import com.inwords.expenses.core.utils.IO
import io.ktor.client.HttpClient
import kotlinx.coroutines.withContext
import org.chromium.net.ConnectionMigrationOptions
import org.chromium.net.CronetEngine
import org.chromium.net.DnsOptions
import org.chromium.net.QuicOptions
import java.io.File

internal actual class HttpClientFactory(
    private val context: Context,
    private val enableLogging: Boolean
) {

    actual suspend fun createHttpClient(): HttpClient {
        return withContext(IO) {
            val cronet = createCronet()
            createKtor(cronet)
        }
    }

    private fun createCronet(): CronetEngine {
        val storagePath = context.cacheDir.absolutePath + "/cronet"
        val storageFile = File(storagePath)
        if (!storageFile.exists()) {
            storageFile.mkdirs()
        }

        return CronetEngine.Builder(context)
            .setStoragePath(storagePath)
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
            .setUserAgent("Android/Expenses/1.0") // TODO choose good user agent
            .build()
    }

    private fun createKtor(cronetEngine: CronetEngine): HttpClient {
        return createKtor(Cronet(cronetEngine), enableLogging = enableLogging) {
            engine {
                this.pipelining = true
                this.followRedirects = false
            }
        }
    }

}