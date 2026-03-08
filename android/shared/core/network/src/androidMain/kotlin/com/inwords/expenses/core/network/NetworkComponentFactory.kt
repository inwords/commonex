package com.inwords.expenses.core.network

import android.content.Context

actual class NetworkComponentFactory actual constructor(
    private val deps: Deps
) {

    actual interface Deps : NetworkComponentFactoryCommonDeps {
        val context: Context
        val production: Boolean
    }

    actual fun create(): NetworkComponent {
        return NetworkComponent(
            HttpClientFactory(
                context = deps.context,
                userAgent = buildUserAgent(
                    versionCode = deps.versionCode,
                    platform = "Android",
                    production = deps.production,
                ),
                enableLogging = !deps.production
            )
        )
    }
}
