package com.inwords.expenses.feature.events.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.core.utils.UI
import com.inwords.expenses.core.utils.asImmutableListAdapter
import com.inwords.expenses.core.utils.stateInWhileSubscribed
import com.inwords.expenses.feature.events.domain.EventCreationStateHolder
import com.inwords.expenses.feature.events.domain.GetCurrenciesUseCase
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.ui.add_persons.AddPersonsPaneDestination
import com.inwords.expenses.feature.events.ui.create.CreateEventPaneUiModel.CurrencyInfoUiModel
import com.inwords.expenses.feature.events.ui.create.CreateEventViewModel.CreateEventPaneModel.CurrencyInfoModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

internal class CreateEventViewModel(
    private val navigationController: NavigationController,
    private val eventCreationStateHolder: EventCreationStateHolder,
    getCurrenciesUseCase: GetCurrenciesUseCase,
    viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + IO),
) : ViewModel(viewModelScope = viewModelScope) {

    private data class CreateEventPaneModel(
        val eventName: String,
        val currencies: List<CurrencyInfoModel>,
    ) {

        data class CurrencyInfoModel(
            val currency: Currency,
            val selected: Boolean,
        )
    }

    private val initialValue = CreateEventPaneModel("", emptyList())

    private val inputEventName = MutableStateFlow(initialValue.eventName)
    private val selectedCurrencyCode = MutableStateFlow<String?>(null)

    private val _state: StateFlow<CreateEventPaneModel> = combine(
        getCurrenciesUseCase.getCurrencies(),
        inputEventName,
        selectedCurrencyCode
    ) { currencies, inputEventName, selectedCurrencyCode ->
        val currencyCodeToSelect = selectedCurrencyCode ?: "RUB"
        CreateEventPaneModel(
            eventName = inputEventName,
            currencies = currencies.map { currency ->
                currency.toUiModel(
                    selected = currency.code == currencyCodeToSelect
                )
            }
        )
    }.stateInWhileSubscribed(
        scope = viewModelScope + UI,
        initialValue = initialValue
    )

    val state: StateFlow<CreateEventPaneUiModel> = _state
        .map { state -> state.toUiModel() }
        .stateInWhileSubscribed(
            scope = viewModelScope + UI,
            initialValue = initialValue.toUiModel()
        )

    private var confirmJob: Job? = null

    fun onEventNameChanged(eventName: String) {
        inputEventName.value = eventName
    }

    fun onCurrencyClicked(currency: CurrencyInfoUiModel) {
        selectedCurrencyCode.value = currency.currencyCode
    }

    fun onConfirmClicked() {
        // TODO mvp
        confirmJob?.cancel()
        confirmJob = viewModelScope.launch {
            val state = _state.value
            eventCreationStateHolder.draftEventName(
                eventName = state.eventName.takeIf { it.isNotBlank() } ?: return@launch
            )
            eventCreationStateHolder.draftEventPrimaryCurrency(
                currency = state.currencies.first { it.selected }.currency
            )
            navigationController.navigateTo(
                destination = AddPersonsPaneDestination,
            )
        }
    }

    fun onNavIconClicked() {
        navigationController.popBackStack()
    }

    private fun Currency.toUiModel(selected: Boolean): CurrencyInfoModel {
        return CurrencyInfoModel(
            currency = this,
            selected = selected
        )
    }

    private fun CurrencyInfoModel.toUiModel(): CurrencyInfoUiModel {
        return CurrencyInfoUiModel(
            currencyName = currency.name,
            currencyCode = currency.code,
            selected = selected
        )
    }

    private fun CreateEventPaneModel.toUiModel(): CreateEventPaneUiModel {
        return CreateEventPaneUiModel(
            eventName = eventName,
            currencies = currencies.map { currency ->
                currency.toUiModel()
            }.asImmutableListAdapter()
        )
    }

}