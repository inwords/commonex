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
import com.inwords.expenses.feature.sync.api.SyncComponentFactoryCommonDeps
import com.inwords.expenses.integration.databases.api.DatabasesComponent
import com.inwords.expenses.integration.databases.api.DatabasesComponentFactory

fun registerComponents(appContext: Context, versionCode: Int, production: Boolean) {
    val platformFactoryDeps = AndroidPlatformFactoryDeps(
        context = appContext,
        production = production,
        versionCode = versionCode,
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

            override fun createSyncComponent(commonDeps: SyncComponentFactoryCommonDeps): SyncComponent {
                return SyncComponentFactory(
                    deps = AndroidSyncFactoryDeps(context = appContext, commonDeps = commonDeps)
                ).create()
            }
        }
    )
}

private data class AndroidPlatformFactoryDeps(
    override val context: Context,
    override val production: Boolean,
    override val versionCode: Int,
) : SettingsComponentFactory.Deps, DatabasesComponentFactory.Deps, ShareComponentFactory.Deps, NetworkComponentFactory.Deps

private class AndroidSyncFactoryDeps(
    override val context: Context,
    commonDeps: SyncComponentFactoryCommonDeps,
) : SyncComponentFactory.Deps, SyncComponentFactoryCommonDeps by commonDeps
