package com.inwords.expenses.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.UserAgent
import io.ktor.http.HttpHeaders
import platform.Foundation.NSURLRequestUseProtocolCachePolicy

internal actual class HttpClientFactory(
    private val userAgent: String,
) {

    actual suspend fun createHttpClient(): HttpClient {
        // FIXME: check if content-encoding is automatically handled by Darwin engine
        return createKtor(Darwin) {
            install(UserAgent) {
                agent = this@HttpClientFactory.userAgent
            }
            engine {
                this.pipelining = true
                configureRequest {
                    if (valueForHTTPHeaderField(HttpHeaders.IfNoneMatch) == null &&
                        valueForHTTPHeaderField(HttpHeaders.IfModifiedSince) == null
                    ) {
                        setCachePolicy(NSURLRequestUseProtocolCachePolicy)
                    }
                }
            }
        }
    }

}
