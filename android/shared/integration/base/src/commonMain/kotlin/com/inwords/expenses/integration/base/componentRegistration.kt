package com.inwords.expenses.integration.base

import com.inwords.expenses.core.locator.ComponentsMap
import com.inwords.expenses.core.locator.registerComponent
import com.inwords.expenses.core.network.NetworkComponent
import com.inwords.expenses.core.utils.SuspendLazy
import com.inwords.expenses.feature.events.api.EventHooks
import com.inwords.expenses.feature.events.api.EventsComponent
import com.inwords.expenses.feature.events.api.EventsComponentFactory
import com.inwords.expenses.feature.expenses.api.ExpensesComponent
import com.inwords.expenses.feature.menu.api.MenuComponent
import com.inwords.expenses.feature.settings.api.SettingsComponent
import com.inwords.expenses.feature.share.api.ShareComponent
import com.inwords.expenses.feature.sync.api.SyncComponent
import com.inwords.expenses.feature.sync.api.SyncComponentFactoryCommonDeps
import com.inwords.expenses.integration.databases.api.DatabasesComponent

internal data class RegistrationContext(
    val settingsComponent: Lazy<SettingsComponent>,
    val dbComponent: Lazy<DatabasesComponent>,
    val networkComponent: Lazy<NetworkComponent>,
    val shareComponent: Lazy<ShareComponent>,
) {
    val networkClientLazy get() = SuspendLazy { networkComponent.value.getHttpClient() }

    val settingsRepositoryLazy get() = settingsComponent.value.settingsRepositoryLazy
}

internal fun registerCommonComponents(
    platformDeps: PlatformRegistrationDeps,
) {
    val settingsComponent = buildSettingsComponent(platformDeps)
    val dbComponent = buildDatabaseComponent(platformDeps)
    val networkComponent = buildNetworkComponent(platformDeps)
    val shareComponent = buildShareComponent(platformDeps)

    with(
        RegistrationContext(
            settingsComponent = settingsComponent,
            dbComponent = dbComponent,
            networkComponent = networkComponent,
            shareComponent = shareComponent,
        )
    ) {
        lateinit var syncComponent: Lazy<SyncComponent>

        val eventsComponent = buildEventsComponent(
            platformDeps = platformDeps,
            syncComponentProvider = { syncComponent },
        )

        val expensesComponent = buildExpensesComponent(
            eventsComponent = eventsComponent,
        )

        val menuComponent = buildMenuComponent(
            eventsComponent = eventsComponent,
        )

        syncComponent = buildSyncComponent(
            platformDeps = platformDeps,
            eventsComponent = eventsComponent,
            expensesComponent = expensesComponent,
        )

        ComponentsMap.registerComponent<SettingsComponent>(settingsComponent)
        ComponentsMap.registerComponent<DatabasesComponent>(dbComponent)
        ComponentsMap.registerComponent<NetworkComponent>(networkComponent)
        ComponentsMap.registerComponent<EventsComponent>(eventsComponent)
        ComponentsMap.registerComponent<ExpensesComponent>(expensesComponent)
        ComponentsMap.registerComponent<MenuComponent>(menuComponent)
        ComponentsMap.registerComponent<SyncComponent>(syncComponent)
        ComponentsMap.registerComponent<ShareComponent>(shareComponent)
    }
}

private fun buildSettingsComponent(
    platformDeps: PlatformRegistrationDeps,
): Lazy<SettingsComponent> = lazy {
    platformDeps.createSettingsComponent()
}

private fun buildDatabaseComponent(
    platformDeps: PlatformRegistrationDeps,
): Lazy<DatabasesComponent> = lazy {
    platformDeps.createDatabaseComponent()
}

private fun buildNetworkComponent(
    platformDeps: PlatformRegistrationDeps,
): Lazy<NetworkComponent> = lazy {
    platformDeps.createNetworkComponent()
}

private fun buildShareComponent(
    platformDeps: PlatformRegistrationDeps,
): Lazy<ShareComponent> = lazy {
    platformDeps.createShareComponent()
}

context(context: RegistrationContext)
private fun buildEventsComponent(
    platformDeps: PlatformRegistrationDeps,
    syncComponentProvider: () -> Lazy<SyncComponent>,
): Lazy<EventsComponent> = lazy {
    platformDeps.createEventsComponent(
        deps = object : EventsComponentFactory.Deps {
            override val eventsDao get() = context.dbComponent.value.eventsDao
            override val personsDao get() = context.dbComponent.value.personsDao
            override val currenciesDao get() = context.dbComponent.value.currenciesDao

            override val transactionHelper get() = context.dbComponent.value.transactionHelper

            override val client get() = context.networkClientLazy
            override val hostConfig get() = context.networkComponent.value.hostConfig

            override val settingsRepositoryLazy get() = context.settingsRepositoryLazy

            override val hooks
                get() = object : EventHooks {
                    override suspend fun onBeforeEventDeletion(eventId: Long) {
                        syncComponentProvider().value.eventsSyncManager.cancelEventSync(eventId)
                    }
                }
        }
    )
}

context(context: RegistrationContext)
private fun buildExpensesComponent(
    eventsComponent: Lazy<EventsComponent>,
): Lazy<ExpensesComponent> = lazy {
    ExpensesComponent(
        deps = object : ExpensesComponent.Deps {
            override val expensesDao get() = context.dbComponent.value.expensesDao

            override val client get() = context.networkClientLazy
            override val hostConfig get() = context.networkComponent.value.hostConfig

            override val transactionHelper get() = context.dbComponent.value.transactionHelper

            override val eventsLocalStore get() = eventsComponent.value.eventsLocalStore.value
            override val currenciesLocalStore get() = eventsComponent.value.currenciesLocalStore.value

            override val getCurrentEventStateUseCaseLazy get() = eventsComponent.value.getCurrentEventStateUseCaseLazy
            override val getEventsUseCaseLazy get() = eventsComponent.value.getEventsUseCaseLazy
            override val joinEventUseCaseLazy get() = eventsComponent.value.joinEventUseCaseLazy
            override val deleteEventUseCaseLazy get() = eventsComponent.value.deleteEventUseCaseLazy

            override val eventDeletionStateManagerLazy get() = eventsComponent.value.eventDeletionStateManagerLazy
            override val eventsSyncStateHolderLazy get() = eventsComponent.value.eventsSyncStateHolderLazy

            override val settingsRepositoryLazy get() = context.settingsRepositoryLazy
        }
    )
}

context(context: RegistrationContext)
private fun buildMenuComponent(
    eventsComponent: Lazy<EventsComponent>,
): Lazy<MenuComponent> = lazy {
    MenuComponent(
        deps = object : MenuComponent.Deps {
            override val getCurrentEventStateUseCaseLazy get() = eventsComponent.value.getCurrentEventStateUseCaseLazy
            override val leaveEventUseCaseLazy get() = eventsComponent.value.leaveEventUseCaseLazy
            override val createShareTokenUseCaseLazy get() = eventsComponent.value.createShareTokenUseCaseLazy

            override val shareManagerLazy get() = context.shareComponent.value.shareManagerLazy
        }
    )
}

private fun buildSyncComponent(
    platformDeps: PlatformRegistrationDeps,
    eventsComponent: Lazy<EventsComponent>,
    expensesComponent: Lazy<ExpensesComponent>,
): Lazy<SyncComponent> = lazy {
    platformDeps.createSyncComponent(
        commonDeps = object : SyncComponentFactoryCommonDeps {
            override val getCurrentEventStateUseCaseLazy get() = eventsComponent.value.getCurrentEventStateUseCaseLazy
            override val expensesInteractorLazy get() = expensesComponent.value.expensesInteractorLazy
            override val eventsSyncStateHolderLazy get() = eventsComponent.value.eventsSyncStateHolderLazy
        }
    )
}
