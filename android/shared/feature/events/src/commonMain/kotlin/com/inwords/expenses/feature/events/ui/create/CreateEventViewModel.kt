package com.inwords.expenses.feature.events.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inwords.expenses.core.navigation.Destination
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.core.utils.UI
import com.inwords.expenses.core.utils.asImmutableListAdapter
import com.inwords.expenses.core.utils.stateInWhileSubscribed
import com.inwords.expenses.feature.events.domain.CreateEventFromParametersUseCase
import com.inwords.expenses.feature.events.domain.GetCurrenciesUseCase
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.ui.create.CreateEventPaneUiModel.CurrencyInfoUiModel
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

internal class CreateEventViewModel(
    private val navigationController: NavigationController,
    getCurrenciesUseCase: GetCurrenciesUseCase,
    private val createEventFromParametersUseCase: CreateEventFromParametersUseCase,
    private val expensesScreenDestination: Destination,
    viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + IO),
) : ViewModel(viewModelScope = viewModelScope) {

    private data class CreateEventPaneModel(
        val eventName: String,
        val ownerName: String,
        val persons: PersistentList<String>,
        val currencies: List<Currency>,
        val selectedCurrency: Currency?,
    )

    private val eventName = MutableStateFlow("")
    private val ownerName = MutableStateFlow("")
    private val persons = MutableStateFlow(persistentListOf<String>())
    private val selectedCurrencyCode = MutableStateFlow<String?>(null)
    private val isCreating = MutableStateFlow(false)

    private var createJob: Job? = null

    private val initialValue = CreateEventPaneModel(
        eventName = eventName.value,
        ownerName = ownerName.value,
        persons = persons.value,
        currencies = emptyList(),
        selectedCurrency = null,
    )

    private val _state = combine(
        getCurrenciesUseCase.getCurrencies(),
        eventName,
        ownerName,
        persons,
        selectedCurrencyCode,
    ) { currencies, eventName, ownerName, persons, selectedCurrencyCode ->
        CreateEventPaneModel(
            eventName = eventName,
            ownerName = ownerName,
            persons = persons,
            currencies = currencies,
            selectedCurrency = currencies.findByCurrencyCode(selectedCurrencyCode),
        )
    }.stateInWhileSubscribed(
        scope = viewModelScope + UI,
        initialValue = initialValue,
    )

    val state: StateFlow<CreateEventPaneUiModel> = combine(
        _state,
        isCreating,
    ) { model, isCreating ->
        model.toUiModel(isCreating = isCreating)
    }.stateInWhileSubscribed(
        scope = viewModelScope + UI,
        initialValue = initialValue.toUiModel(isCreating = false),
    )

    fun onEventNameChanged(eventName: String) {
        if (createJob?.isActive == true) return

        this.eventName.value = eventName
    }

    fun onOwnerNameChanged(ownerName: String) {
        if (createJob?.isActive == true) return

        this.ownerName.value = ownerName
    }

    fun onParticipantNameChanged(index: Int, participantName: String) {
        if (createJob?.isActive == true) return

        persons.update { it.replacingAt(index, participantName) }
    }

    fun onAddParticipantClicked() {
        if (createJob?.isActive == true) return

        persons.update { it.adding("") }
    }

    fun onCurrencyClicked(currency: CurrencyInfoUiModel) {
        if (createJob?.isActive == true) return

        selectedCurrencyCode.value = currency.currencyCode
    }

    fun onConfirmClicked() {
        if (createJob?.isActive == true) return

        val state = _state.value

        val name = state.eventName
        val owner = state.ownerName
        val otherPersons = state.persons
        val selectedCurrencyId = state.selectedCurrency?.id ?: return
        if (name.isBlank() || owner.isBlank()) return

        isCreating.value = true
        createJob = viewModelScope.launch {
            createEventFromParametersUseCase.createEvent(
                name = name,
                owner = owner,
                primaryCurrencyId = selectedCurrencyId,
                otherPersons = otherPersons,
            )
            navigationController.popBackStack(
                toDestination = expensesScreenDestination,
                inclusive = false,
            )
        }.apply {
            invokeOnCompletion {
                isCreating.value = false
            }
        }
    }

    fun onNavIconClicked() {
        navigationController.popBackStack()
    }

    private fun List<Currency>.findByCurrencyCode(currencyCode: String?): Currency? {
        return firstOrNull { it.code == currencyCode }
            ?: firstOrNull { it.code == "RUB" }
            ?: firstOrNull()
    }

    private fun CreateEventPaneModel.toUiModel(isCreating: Boolean): CreateEventPaneUiModel {
        return CreateEventPaneUiModel(
            eventName = eventName,
            ownerName = ownerName,
            persons = persons,
            currencies = currencies.map { currency ->
                CurrencyInfoUiModel(
                    currencyName = currency.name,
                    currencyCode = currency.code,
                    selected = currency.id == selectedCurrency?.id,
                )
            }.asImmutableListAdapter(),
            isCreating = isCreating,
        )
    }

}
