package com.inwords.expenses.core.network

import com.inwords.expenses.core.storage.utils.iosDocumentsDirectoryPath
import io.ktor.http.URLProtocol

actual class NetworkComponentFactory actual constructor(private val deps: Deps) {

    actual interface Deps : NetworkComponentFactoryCommonDeps

    actual fun create(): NetworkComponent {
        return NetworkComponent(
            hostConfig = HostConfig(URLProtocol.HTTPS, deps.apiHost),
            httpClientFactory = HttpClientFactory(
                userAgent = buildUserAgent(
                    versionCode = deps.versionCode,
                    platform = "iOS",
                    production = true // FIXME: ios is not always production
                )
            ),
            idempotencyKeyGenerator = IdempotencyKeyGenerator(
                idempotencyClientIdProvider = IdempotencyClientIdStore(
                    directory = iosDocumentsDirectoryPath(),
                )
            ),
        )
    }
}
