package com.inwords.expenses.integration.base

import com.inwords.expenses.core.network.NetworkComponent
import com.inwords.expenses.feature.events.api.EventsComponent
import com.inwords.expenses.feature.events.api.EventsComponentFactory
import com.inwords.expenses.feature.settings.api.SettingsComponent
import com.inwords.expenses.feature.share.api.ShareComponent
import com.inwords.expenses.feature.sync.api.SyncComponent
import com.inwords.expenses.integration.databases.api.DatabasesComponent

internal interface PlatformRegistrationDeps {

    fun createSettingsComponent(): SettingsComponent

    fun createDatabaseComponent(): DatabasesComponent

    fun createNetworkComponent(): NetworkComponent

    fun createShareComponent(): ShareComponent

    fun createEventsComponent(deps: EventsComponentFactory.Deps): EventsComponent

    fun createSyncComponent(syncDeps: SyncDepsValues): SyncComponent
}
