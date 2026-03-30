package com.inwords.expenses.feature.expenses.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.observability.Observability
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.core.utils.UNCONFINED
import com.inwords.expenses.core.utils.asImmutableListAdapter
import com.inwords.expenses.core.utils.debounceAfterInitial
import com.inwords.expenses.core.utils.flatMapLatestNoBuffer
import com.inwords.expenses.core.utils.shareInWhileSubscribed
import com.inwords.expenses.core.utils.stateInWhileSubscribed
import com.inwords.expenses.feature.events.api.EventDeletionStateManager
import com.inwords.expenses.feature.events.api.EventDeletionStateManager.EventDeletionState
import com.inwords.expenses.feature.events.domain.DeleteEventUseCase
import com.inwords.expenses.feature.events.domain.EventsSyncStateHolder
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.GetEventsUseCase
import com.inwords.expenses.feature.events.domain.JoinEventUseCase
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.ui.choose_person.ChoosePersonPaneDestination
import com.inwords.expenses.feature.events.ui.create.CreateEventPaneDestination
import com.inwords.expenses.feature.events.ui.dialog.delete.DeleteEventDialogDestination
import com.inwords.expenses.feature.events.ui.join.JoinEventPaneDestination
import com.inwords.expenses.feature.events.ui.local.LocalEventsUiModel
import com.inwords.expenses.feature.events.ui.local.LocalEventsUiModel.LocalEventUiModel
import com.inwords.expenses.feature.expenses.domain.GetExpensesDetailsUseCase
import com.inwords.expenses.feature.expenses.domain.RequestExpensesRefreshUseCase
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneDestination
import com.inwords.expenses.feature.expenses.ui.common.DebtShortUiModel
import com.inwords.expenses.feature.expenses.ui.debts_list.DebtsListPaneDestination
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.Expenses.DayChipUiModel
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.Expenses.ExpenseUiModel
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.LocalEvents
import com.inwords.expenses.feature.expenses.ui.list.bottom_sheet.item.ExpenseItemPaneDestination
import com.inwords.expenses.feature.expenses.ui.utils.toRoundedString
import com.inwords.expenses.feature.menu.ui.MenuDialogDestination
import com.inwords.expenses.feature.settings.api.SettingsRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlin.time.Duration.Companion.milliseconds

internal class ExpensesViewModel(
    private val navigationController: NavigationController,
    private val getCurrentEventStateUseCase: GetCurrentEventStateUseCase,
    private val eventDeletionStateManager: EventDeletionStateManager,
    private val getEventsUseCase: GetEventsUseCase,
    private val joinEventUseCase: JoinEventUseCase,
    private val deleteEventUseCase: DeleteEventUseCase,
    private val getExpensesDetailsUseCase: GetExpensesDetailsUseCase,
    private val requestExpensesRefreshUseCase: RequestExpensesRefreshUseCase,
    eventsSyncStateHolder: EventsSyncStateHolder,
    settingsRepository: SettingsRepository,
    private val timelineUiModelFactory: ExpensesTimelineUiModelFactory = ExpensesTimelineUiModelFactory(),
    unconfinedDispatcher: CoroutineDispatcher = UNCONFINED,
    viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + IO),
) : ViewModel(viewModelScope = viewModelScope) {

    private var refreshJob: Job? = null
    private var joinEventJob: Job? = null
    private var recentlyRemovedEventJob: Job? = null

    private val pullToRefreshStateManager = PullToRefreshStateManager(eventsSyncStateHolder)
    private val recentlyRemovedEventName = MutableStateFlow<String?>(null)
    private val selectedDayKey = MutableStateFlow<SelectedDayKey?>(null)

    private val localEventsState = flow<SimpleScreenState<ExpensesPaneUiModel>> {
        var previousEvents = emptyList<Event>()
        combine(
            getEventsUseCase.getEvents(),
            eventDeletionStateManager.eventsDeletionState,
            recentlyRemovedEventName,
        ) { events, eventsDeletionState, recentlyRemovedEventName ->
            val result = if (events.isEmpty()) {
                SimpleScreenState.Empty
            } else {
                handleEventRemovalDetection(previousEvents, events)
                SimpleScreenState.Success(
                    data = LocalEvents(
                        localEvents = LocalEventsUiModel(
                            events = events.map { event ->
                                LocalEventUiModel(
                                    eventId = event.id,
                                    eventName = event.name,
                                    isSynced = event.serverId != null,
                                    deletionState = eventsDeletionState[event.id] ?: EventDeletionState.None
                                )
                            }.asImmutableListAdapter(),
                            recentlyRemovedEventName = recentlyRemovedEventName,
                        )
                    )
                )
            }
            previousEvents = events
            result
        }.let { emitAll(it) }
    }

    private val expensesDetailsFlow = getCurrentEventStateUseCase.currentEvent
        .flatMapLatestNoBuffer { currentEvent ->
            if (currentEvent == null) {
                flowOf(null)
            } else {
                getExpensesDetailsUseCase.getExpensesDetails(currentEvent)
                    .debounceAfterInitial(500.milliseconds)
            }
        }

    private val isRefreshingFlow = getCurrentEventStateUseCase.currentEvent
        .flatMapLatestNoBuffer { currentEvent ->
            if (currentEvent == null) {
                flowOf(false)
            } else {
                pullToRefreshStateManager.isEventRefreshing(currentEvent.event.id)
            }
        }
        .shareInWhileSubscribed(scope = viewModelScope + unconfinedDispatcher, replay = 1)

    val state: StateFlow<SimpleScreenState<ExpensesPaneUiModel>> = combine(
        expensesDetailsFlow,
        settingsRepository.getCurrentPersonId(),
    ) { expensesDetails, currentPersonId ->
        expensesDetails to currentPersonId
    }.flatMapLatestNoBuffer { (expensesDetails, currentPersonId) ->
        val currentPerson = expensesDetails?.event?.persons?.firstOrNull { it.id == currentPersonId }
        if (expensesDetails == null || currentPerson == null) {
            // local events branch
            return@flatMapLatestNoBuffer localEventsState
        }

        val debts = expensesDetails.debtCalculator.getBarterAccumulatedDebtForPerson(currentPerson)
            .map { (person, barterAccumulatedDebt) ->
                DebtShortUiModel(
                    personId = person.id,
                    personName = person.name,
                    currencyCode = barterAccumulatedDebt.currency.code,
                    currencyName = barterAccumulatedDebt.currency.name,
                    amount = barterAccumulatedDebt.barterAmount.toRoundedString()
                )
            }
            .sortedBy { it.amount }
            .toPersistentList()

        val timelineData = timelineUiModelFactory.create(
            expensesDetails = expensesDetails,
            currentPersonId = currentPerson.id,
            debts = debts,
        )

        flowOf(SimpleScreenState.Success(timelineData))
    }
        .flowOn(viewModelScope.coroutineContext[CoroutineDispatcher] ?: IO)
        .combine(selectedDayKey) { state, selected ->
            if (state !is SimpleScreenState.Success) return@combine state

            when (val data = state.data) {
                is ExpensesPaneUiModel.Expenses -> {
                    val preferredDayKey = selected?.takeIf { it.eventId == data.eventId }?.dayKey
                    val updatedDayChips = data.dayChips.withSelectedDay(preferredDayKey)
                    if (updatedDayChips == data.dayChips) {
                        state
                    } else {
                        state.copy(data = data.copy(dayChips = updatedDayChips))
                    }
                }

                is LocalEvents -> state
            }
        }
        .combine(isRefreshingFlow) { state, isRefreshing ->
            if (state !is SimpleScreenState.Success) return@combine state

            when (val data = state.data) {
                is ExpensesPaneUiModel.Expenses -> if (data.isRefreshing == isRefreshing) {
                    state
                } else {
                    state.copy(data = data.copy(isRefreshing = isRefreshing))
                }

                is LocalEvents -> state
            }
        }
        .stateInWhileSubscribed(
            scope = viewModelScope + unconfinedDispatcher,
            initialValue = SimpleScreenState.Loading,
            replayExpirationMillis = 1500L,
        )

    fun onMenuClick() {
        navigationController.navigateTo(MenuDialogDestination)
    }

    fun onAddExpenseClick() {
        navigationController.navigateTo(AddExpensePaneDestination())
    }

    fun onExpenseClick(expense: ExpenseUiModel) {
        val data = (state.value as? SimpleScreenState.Success)?.data ?: return
        val eventId = (data as? ExpensesPaneUiModel.Expenses)?.eventId ?: return

        navigationController.navigateTo(
            ExpenseItemPaneDestination(
                expenseId = expense.expenseId,
                eventId = eventId,
            )
        )
    }

    fun onDebtsDetailsClick() {
        navigationController.navigateTo(DebtsListPaneDestination)
    }

    fun onReplenishmentClick(creditor: DebtShortUiModel) {
        val state = (state.value as? SimpleScreenState.Success)?.data ?: return
        val currentPersonId = when (state) {
            is ExpensesPaneUiModel.Expenses -> state.currentPersonId
            is LocalEvents -> return
        }

        navigationController.navigateTo(
            AddExpensePaneDestination(
                replenishment = AddExpensePaneDestination.Replenishment(
                    fromPersonId = currentPersonId,
                    toPersonId = creditor.personId,
                    currencyCode = creditor.currencyCode,
                    amount = creditor.amount
                )
            )
        )
    }

    fun onCreateEventClick() {
        navigationController.navigateTo(CreateEventPaneDestination)
    }

    fun onJoinEventClick() {
        navigationController.navigateTo(JoinEventPaneDestination())
    }

    fun onRefresh() {
        val event = getCurrentEventStateUseCase.currentEvent.value?.event ?: return

        pullToRefreshStateManager.onUserTriggeredRefresh(viewModelScope, event.id)
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            requestExpensesRefreshUseCase.requestRefresh(event)
        }
    }

    fun onDayChipClick(dayKey: String) {
        val data = (state.value as? SimpleScreenState.Success)?.data as? ExpensesPaneUiModel.Expenses ?: return
        updateSelectedDayState(data, dayKey)
    }

    fun onVisibleDayChanged(dayKey: String) {
        val data = (state.value as? SimpleScreenState.Success)?.data as? ExpensesPaneUiModel.Expenses ?: return
        updateSelectedDayState(data, dayKey)
    }

    fun onJoinLocalEventClick(event: LocalEventUiModel) {
        joinEventJob?.cancel()
        joinEventJob = viewModelScope.launch {
            val joined = joinEventUseCase.joinLocalEvent(event.eventId)
            if (joined) {
                navigationController.navigateTo(
                    destination = ChoosePersonPaneDestination
                )
            } else {
                Observability.captureMessage("ExpensesViewModel failed to join a local event because it no longer exists")
            }
        }
    }

    fun onDeleteEventClick(event: LocalEventUiModel) {
        navigationController.navigateTo(
            DeleteEventDialogDestination(
                eventId = event.eventId,
                eventName = event.eventName
            )
        )
    }

    fun onDeleteOnlyLocalEventClick(event: LocalEventUiModel) {
        viewModelScope.launch {
            deleteEventUseCase.deleteLocalEvent(event.eventId)
        }
    }

    fun onKeepLocalEventClick(event: LocalEventUiModel) {
        eventDeletionStateManager.clearEventDeletionState(event.eventId)
    }

    private fun handleEventRemovalDetection(
        previousEvents: List<Event>,
        newEvents: List<Event>
    ) {
        if (previousEvents.size - 1 == newEvents.size) {
            for (i in previousEvents.indices) {
                if (i >= newEvents.size || previousEvents[i].id != newEvents[i].id) {
                    recentlyRemovedEventName.value = null
                    recentlyRemovedEventJob?.cancel()

                    val removedEvent = previousEvents[i]

                    recentlyRemovedEventJob = viewModelScope.launch {
                        recentlyRemovedEventName.value = removedEvent.name
                        delay(3000)
                        recentlyRemovedEventName.value = null
                    }
                    break
                }
            }
        }
    }

    private fun updateSelectedDayState(data: ExpensesPaneUiModel.Expenses, dayKey: String) {
        if (data.dayChips.none { it.dayKey == dayKey }) return

        selectedDayKey.value = SelectedDayKey(data.eventId, dayKey)
    }

    private data class SelectedDayKey(
        val eventId: Long,
        val dayKey: String,
    )

}

private fun ImmutableList<DayChipUiModel>.withSelectedDay(dayKey: String?): ImmutableList<DayChipUiModel> {
    val list = this
    if (isEmpty()) return list

    val resolvedDayKey = dayKey?.takeIf { list.any { it.dayKey == dayKey } } ?: list.first().dayKey
    var changed = false
    val updated = list.map { chip ->
        val isSelected = chip.dayKey == resolvedDayKey
        if (chip.isSelected == isSelected) {
            chip
        } else {
            changed = true
            chip.copy(isSelected = isSelected)
        }
    }

    return if (changed) {
        updated.asImmutableListAdapter()
    } else {
        list
    }
}
