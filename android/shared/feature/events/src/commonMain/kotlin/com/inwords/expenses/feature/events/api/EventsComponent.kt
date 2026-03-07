package com.inwords.expenses.feature.events.api

import com.inwords.expenses.core.navigation.Destination
import com.inwords.expenses.core.navigation.NavModule
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.core.utils.Component
import com.inwords.expenses.feature.events.data.db.store.CurrenciesLocalStoreImpl
import com.inwords.expenses.feature.events.data.db.store.EventsLocalStoreImpl
import com.inwords.expenses.feature.events.data.db.store.PersonsLocalStoreImpl
import com.inwords.expenses.feature.events.data.network.store.CurrenciesRemoteStoreImpl
import com.inwords.expenses.feature.events.data.network.store.EventsRemoteStoreImpl
import com.inwords.expenses.feature.events.domain.AddParticipantsToCurrentEventUseCase
import com.inwords.expenses.feature.events.domain.CreateEventFromParametersUseCase
import com.inwords.expenses.feature.events.domain.CreateEventUseCase
import com.inwords.expenses.feature.events.domain.CreateShareTokenUseCase
import com.inwords.expenses.feature.events.domain.DeleteEventUseCase
import com.inwords.expenses.feature.events.domain.EventCreationStateHolder
import com.inwords.expenses.feature.events.domain.EventDeletionStateManagerImpl
import com.inwords.expenses.feature.events.domain.EventsSyncStateHolder
import com.inwords.expenses.feature.events.domain.GetCurrenciesUseCase
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.GetEventsUseCase
import com.inwords.expenses.feature.events.domain.JoinEventUseCase
import com.inwords.expenses.feature.events.domain.LeaveEventUseCase
import com.inwords.expenses.feature.events.domain.store.local.CurrenciesLocalStore
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.events.domain.store.local.PersonsLocalStore
import com.inwords.expenses.feature.events.domain.store.remote.CurrenciesRemoteStore
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore
import com.inwords.expenses.feature.events.domain.task.CurrenciesPullTask
import com.inwords.expenses.feature.events.domain.task.EventPersonsPushTask
import com.inwords.expenses.feature.events.domain.task.EventPullPersonsTask
import com.inwords.expenses.feature.events.domain.task.EventPushTask
import com.inwords.expenses.feature.events.ui.add_participants.getAddParticipantsToEventPaneNavModule
import com.inwords.expenses.feature.events.ui.add_persons.getAddPersonsPaneNavModule
import com.inwords.expenses.feature.events.ui.choose_person.getChoosePersonPaneNavModule
import com.inwords.expenses.feature.events.ui.create.getCreateEventPaneNavModule
import com.inwords.expenses.feature.events.ui.dialog.delete.getDeleteEventDialogNavModule
import com.inwords.expenses.feature.events.ui.join.getJoinEventPaneNavModule
import com.inwords.expenses.feature.settings.api.SettingsRepository

class EventsComponent internal constructor(
    private val deps: EventsComponentFactory.Deps
) : Component {

    private val transactionHelper: Lazy<TransactionHelper> = lazy { deps.transactionHelper }

    internal val settingsRepositoryLazy: Lazy<SettingsRepository> get() = deps.settingsRepositoryLazy

    val currenciesLocalStore: Lazy<CurrenciesLocalStore> = lazy {
        CurrenciesLocalStoreImpl(currenciesDaoLazy = lazy { deps.currenciesDao })
    }

    private val currenciesRemoteStore: Lazy<CurrenciesRemoteStore> = lazy {
        CurrenciesRemoteStoreImpl(
            client = deps.client,
            hostConfig = deps.hostConfig
        )
    }

    private val personsLocalStore: Lazy<PersonsLocalStore> = lazy {
        PersonsLocalStoreImpl(lazy { deps.personsDao })
    }

    val eventsLocalStore: Lazy<EventsLocalStore> = lazy {
        EventsLocalStoreImpl(
            transactionHelperLazy = transactionHelper,
            eventsDaoLazy = lazy { deps.eventsDao },
            personsLocalStoreLazy = personsLocalStore,
            currenciesLocalStoreLazy = currenciesLocalStore,
        )
    }

    private val eventsRemoteStore: Lazy<EventsRemoteStore> = lazy {
        EventsRemoteStoreImpl(
            client = deps.client,
            hostConfig = deps.hostConfig,
        )
    }

    val currenciesPullTask: Lazy<CurrenciesPullTask> = lazy {
        CurrenciesPullTask(
            transactionHelperLazy = transactionHelper,
            currenciesLocalStoreLazy = currenciesLocalStore,
            currenciesRemoteStoreLazy = currenciesRemoteStore
        )
    }

    val eventPushTask: Lazy<EventPushTask> = lazy {
        EventPushTask(
            transactionHelperLazy = transactionHelper,
            eventsLocalStoreLazy = eventsLocalStore,
            eventsRemoteStoreLazy = eventsRemoteStore,
            personsLocalStoreLazy = personsLocalStore,
        )
    }

    val eventPersonsPushTask: Lazy<EventPersonsPushTask> = lazy {
        EventPersonsPushTask(
            eventsLocalStoreLazy = eventsLocalStore,
            eventsRemoteStoreLazy = eventsRemoteStore,
        )
    }

    val eventPullPersonsTask: Lazy<EventPullPersonsTask> = lazy {
        EventPullPersonsTask(
            transactionHelperLazy = transactionHelper,
            eventsLocalStoreLazy = eventsLocalStore,
            eventsRemoteStoreLazy = eventsRemoteStore,
        )
    }

    val deleteEventUseCaseLazy: Lazy<DeleteEventUseCase> = lazy {
        DeleteEventUseCase(
            eventsLocalStoreLazy = eventsLocalStore,
            eventsRemoteStoreLazy = eventsRemoteStore,
            settingsRepositoryLazy = deps.settingsRepositoryLazy,
            hooksLazy = lazy { deps.hooks },
        )
    }

    val joinEventUseCaseLazy: Lazy<JoinEventUseCase> = lazy {
        JoinEventUseCase(
            transactionHelperLazy = transactionHelper,
            eventsLocalStoreLazy = eventsLocalStore,
            eventsRemoteStoreLazy = eventsRemoteStore,
            deleteEventUseCase = deleteEventUseCaseLazy,
            settingsRepositoryLazy = deps.settingsRepositoryLazy,
            currenciesLocalStoreLazy = currenciesLocalStore,
            currenciesPullTaskLazy = currenciesPullTask,
        )
    }

    val eventCreationStateHolder: Lazy<EventCreationStateHolder> = lazy {
        EventCreationStateHolder()
    }

    val getCurrentEventStateUseCaseLazy: Lazy<GetCurrentEventStateUseCase> = lazy {
        GetCurrentEventStateUseCase(
            eventsLocalStoreLazy = eventsLocalStore,
            settingsRepositoryLazy = deps.settingsRepositoryLazy,
        )
    }

    val createEventUseCaseLazy: Lazy<CreateEventUseCase> = lazy {
        CreateEventUseCase(
            createEventFromParametersUseCaseLazy = createEventFromParametersUseCaseLazy,
            eventCreationStateHolderLazy = eventCreationStateHolder,
        )
    }

    val createEventFromParametersUseCaseLazy: Lazy<CreateEventFromParametersUseCase> = lazy {
        CreateEventFromParametersUseCase(
            eventsLocalStoreLazy = eventsLocalStore,
            settingsRepositoryLazy = deps.settingsRepositoryLazy,
        )
    }

    val getCurrenciesUseCaseLazy: Lazy<GetCurrenciesUseCase> = lazy {
        GetCurrenciesUseCase(
            currenciesLocalStoreLazy = currenciesLocalStore,
        )
    }

    val getEventsUseCaseLazy: Lazy<GetEventsUseCase> = lazy {
        GetEventsUseCase(
            eventsLocalStoreLazy = eventsLocalStore,
        )
    }

    val leaveEventUseCaseLazy: Lazy<LeaveEventUseCase> = lazy {
        LeaveEventUseCase(
            settingsRepositoryLazy = deps.settingsRepositoryLazy,
        )
    }

    val addParticipantsToCurrentEventUseCaseLazy: Lazy<AddParticipantsToCurrentEventUseCase> = lazy {
        AddParticipantsToCurrentEventUseCase(
            eventsLocalStoreLazy = eventsLocalStore,
            settingsRepositoryLazy = deps.settingsRepositoryLazy,
        )
    }

    val eventDeletionStateManagerLazy: Lazy<EventDeletionStateManager> = lazy {
        EventDeletionStateManagerImpl()
    }

    val eventsSyncStateHolderLazy: Lazy<EventsSyncStateHolder> = lazy {
        EventsSyncStateHolder()
    }

    val createShareTokenUseCaseLazy: Lazy<CreateShareTokenUseCase> = lazy {
        CreateShareTokenUseCase(
            eventsRemoteStoreLazy = eventsRemoteStore,
        )
    }

    fun getNavModules(
        navigationController: NavigationController,
        expensesPaneDestination: Destination,
    ): List<NavModule> {
        return listOf(
            getJoinEventPaneNavModule(navigationController),
            getCreateEventPaneNavModule(navigationController),
            getDeleteEventDialogNavModule(navigationController),
            getAddPersonsPaneNavModule(navigationController, expensesPaneDestination),
            getChoosePersonPaneNavModule(navigationController, expensesPaneDestination),
            getAddParticipantsToEventPaneNavModule(navigationController),
        )
    }
}
