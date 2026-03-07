package com.inwords.expenses.core.network

actual class NetworkComponentFactory actual constructor(deps: Deps) {

    actual interface Deps

    actual fun create(): NetworkComponent {
        return NetworkComponent(HttpClientFactory())
    }
}
