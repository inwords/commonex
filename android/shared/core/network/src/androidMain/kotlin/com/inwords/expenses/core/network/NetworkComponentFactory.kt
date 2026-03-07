package com.inwords.expenses.core.network

import android.content.Context

actual class NetworkComponentFactory actual constructor(
    private val deps: Deps
) {

    actual interface Deps {
        val context: Context
        val production: Boolean
    }

    actual fun create(): NetworkComponent {
        return NetworkComponent(HttpClientFactory(deps.context, enableLogging = !deps.production))
    }
}
