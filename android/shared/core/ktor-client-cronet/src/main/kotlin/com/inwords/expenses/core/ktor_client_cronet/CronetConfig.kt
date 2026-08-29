package com.inwords.expenses.core.ktor_client_cronet

import io.ktor.client.engine.HttpClientEngineConfig
import org.chromium.net.CronetEngine

class CronetConfig(val preconfigured: CronetEngine) : HttpClientEngineConfig() {

    var followRedirects: Boolean = false
    var responseBufferSize: Int = 64 * 1024
}
