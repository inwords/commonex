package com.inwords.expenses.core.network

actual class NetworkComponentFactory actual constructor(private val deps: Deps) {

    actual interface Deps : NetworkComponentFactoryCommonDeps

    actual fun create(): NetworkComponent {
        return NetworkComponent(
            HttpClientFactory(
                userAgent = buildUserAgent(
                    versionCode = deps.versionCode,
                    platform = "iOS",
                    production = true // FIXME: ios is not always production
                )
            )
        )
    }
}
