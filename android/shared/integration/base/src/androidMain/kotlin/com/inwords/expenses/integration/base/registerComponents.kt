package com.inwords.expenses.integration.base

import android.content.Context
import com.inwords.expenses.core.network.NetworkComponent
import com.inwords.expenses.core.network.NetworkComponentFactory
import com.inwords.expenses.feature.events.api.EventsComponent
import com.inwords.expenses.feature.events.api.EventsComponentFactory
import com.inwords.expenses.feature.settings.api.SettingsComponent
import com.inwords.expenses.feature.settings.api.SettingsComponentFactory
import com.inwords.expenses.feature.share.api.ShareComponent
import com.inwords.expenses.feature.share.api.ShareComponentFactory
import com.inwords.expenses.feature.sync.api.SyncComponent
import com.inwords.expenses.feature.sync.api.SyncComponentFactory
import com.inwords.expenses.integration.databases.api.DatabasesComponent
import com.inwords.expenses.integration.databases.api.DatabasesComponentFactory

fun registerComponents(appContext: Context, production: Boolean) {
    val platformFactoryDeps = AndroidPlatformFactoryDeps(
        appContext = appContext,
        production = production,
    )

    registerCommonComponents(
        platformDeps = object : PlatformRegistrationDeps {
            override fun createSettingsComponent(): SettingsComponent {
                return SettingsComponentFactory(deps = platformFactoryDeps).create()
            }

            override fun createDatabaseComponent(): DatabasesComponent {
                return DatabasesComponentFactory(deps = platformFactoryDeps).create()
            }

            override fun createNetworkComponent(): NetworkComponent {
                return NetworkComponentFactory(deps = platformFactoryDeps).create()
            }

            override fun createShareComponent(): ShareComponent {
                return ShareComponentFactory(deps = platformFactoryDeps).create()
            }

            override fun createEventsComponent(deps: EventsComponentFactory.Deps): EventsComponent {
                return EventsComponentFactory(deps = deps).create()
            }

            override fun createSyncComponent(syncDeps: SyncDepsValues): SyncComponent {
                return SyncComponentFactory(
                    deps = AndroidSyncFactoryDeps(
                        appContext = appContext,
                        syncDeps = syncDeps,
                    )
                ).create()
            }
        }
    )
}

private data class AndroidPlatformFactoryDeps(
    private val appContext: Context,
    override val production: Boolean,
) : SettingsComponentFactory.Deps, DatabasesComponentFactory.Deps, ShareComponentFactory.Deps, NetworkComponentFactory.Deps {
    override val context: Context get() = appContext
}

private data class AndroidSyncFactoryDeps(
    private val appContext: Context,
    private val syncDeps: SyncDepsValues,
) : SyncComponentFactory.Deps {
    override val context: Context get() = appContext

    override val getCurrentEventStateUseCaseLazy = syncDeps.getCurrentEventStateUseCaseLazy
    override val expensesInteractorLazy = syncDeps.expensesInteractorLazy
    override val eventsSyncStateHolderLazy = syncDeps.eventsSyncStateHolderLazy
}
