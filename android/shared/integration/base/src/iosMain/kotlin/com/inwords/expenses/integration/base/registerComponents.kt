package com.inwords.expenses.integration.base

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
import platform.Foundation.NSBundle

fun registerComponents() {
    val platformFactoryDeps = IosPlatformFactoryDeps

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
                return SyncComponentFactory(deps = IosSyncFactoryDeps(commonDeps = commonDeps)).create()
            }
        }
    )
}

private object IosPlatformFactoryDeps :
    SettingsComponentFactory.Deps,
    DatabasesComponentFactory.Deps,
    ShareComponentFactory.Deps,
    NetworkComponentFactory.Deps {

    override val versionCode = bundleBuildNumber()

    private fun bundleBuildNumber(): Int {
        val info = NSBundle.mainBundle.infoDictionary ?: return 0
        return (info["CFBundleVersion"] as? String)?.toIntOrNull() ?: 0
    }
}

private class IosSyncFactoryDeps(
    commonDeps: SyncComponentFactoryCommonDeps,
) : SyncComponentFactory.Deps, SyncComponentFactoryCommonDeps by commonDeps
