package com.inwords.expenses.feature.banner.api

import com.inwords.expenses.core.network.HostConfig
import com.inwords.expenses.core.utils.Component
import com.inwords.expenses.core.utils.SuspendLazy
import com.inwords.expenses.feature.banner.data.network.BannerRemoteStoreImpl
import com.inwords.expenses.feature.banner.domain.BannerRepository
import com.inwords.expenses.feature.banner.domain.DismissBannerUseCase
import com.inwords.expenses.feature.banner.domain.GetBannerUseCase
import com.inwords.expenses.feature.settings.api.SettingsRepository
import io.ktor.client.HttpClient

class BannerComponent(private val deps: Deps) : Component {

    interface Deps {

        val client: SuspendLazy<HttpClient>
        val hostConfig: HostConfig
        val settingsRepositoryLazy: Lazy<SettingsRepository>
    }

    private val bannerRepositoryLazy: Lazy<BannerRepository> = lazy {
        BannerRemoteStoreImpl(
            client = deps.client,
            hostConfig = deps.hostConfig,
        )
    }

    val getBannerUseCaseLazy: Lazy<GetBannerUseCase> = lazy {
        GetBannerUseCase(
            bannerRepository = bannerRepositoryLazy.value,
            settingsRepositoryLazy = deps.settingsRepositoryLazy,
        )
    }

    val dismissBannerUseCaseLazy: Lazy<DismissBannerUseCase> = lazy {
        DismissBannerUseCase(settingsRepositoryLazy = deps.settingsRepositoryLazy)
    }
}
