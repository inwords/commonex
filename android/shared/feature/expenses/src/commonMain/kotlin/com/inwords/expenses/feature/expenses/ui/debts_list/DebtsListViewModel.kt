package com.inwords.expenses.feature.expenses.ui.debts_list

import androidx.lifecycle.ViewModel
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.core.utils.asImmutableListAdapter
import com.inwords.expenses.core.utils.flatMapLatestNoBuffer
import com.inwords.expenses.core.utils.stateInWhileSubscribed
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.expenses.domain.GetExpensesDetailsUseCase
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneDestination
import com.inwords.expenses.feature.expenses.ui.common.DebtShortUiModel
import com.inwords.expenses.feature.expenses.ui.converter.toCreditorDebtShortUiModel
import com.inwords.expenses.feature.expenses.ui.converter.toUiModel
import com.inwords.expenses.feature.expenses.ui.debts_list.DebtsListPaneUiModel.DebtorUiModel
import com.inwords.expenses.feature.expenses.ui.debts_list.DebtsListPaneUiModel.PersonUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

internal class DebtsListViewModel(
    private val navigationController: NavigationController,
    getCurrentEventStateUseCase: GetCurrentEventStateUseCase,
    getExpensesDetailsUseCase: GetExpensesDetailsUseCase,
    viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + IO),
) : ViewModel(viewModelScope = viewModelScope) {

    val state: StateFlow<SimpleScreenState<DebtsListPaneUiModel>> = getCurrentEventStateUseCase.currentEvent
        .filterNotNull() // TODO mvp
        .flatMapLatestNoBuffer { getExpensesDetailsUseCase.getExpensesDetails(it) }
        .map { expensesDetails ->
            SimpleScreenState.Success(
                data = DebtsListPaneUiModel(
                    eventName = expensesDetails.event.event.name,
                    primaryCurrencyCode = expensesDetails.event.primaryCurrency.code,
                    debtors = expensesDetails.debtCalculator.barterAccumulatedDebtSummaries
                        .groupBy { it.debtor }
                        .map { (debtor, debts) ->
                            DebtorUiModel(
                                person = debtor.toUiModel(),
                                creditors = debts.map { it.toCreditorDebtShortUiModel() }.asImmutableListAdapter(),
                            )
                        }.asImmutableListAdapter(),
                )
            )
        }
        .stateInWhileSubscribed(viewModelScope, initialValue = SimpleScreenState.Loading)

    fun onReplenishmentClick(debtor: PersonUiModel, creditor: DebtShortUiModel) {
        navigationController.navigateTo(
            AddExpensePaneDestination(
                replenishment = AddExpensePaneDestination.Replenishment(
                    fromPersonId = debtor.personId,
                    toPersonId = creditor.personId,
                    currencyCode = creditor.currencyCode,
                    amount = creditor.amount
                )
            )
        )
    }

    fun onNavIconClicked() {
        navigationController.popBackStack()
    }

}
