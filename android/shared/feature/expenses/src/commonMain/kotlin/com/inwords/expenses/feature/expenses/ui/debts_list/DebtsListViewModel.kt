package com.inwords.expenses.feature.expenses.ui.debts_list

import androidx.lifecycle.ViewModel
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.core.utils.asImmutableListAdapter
import com.inwords.expenses.core.utils.asImmutableMap
import com.inwords.expenses.core.utils.flatMapLatestNoBuffer
import com.inwords.expenses.core.utils.stateInWhileSubscribed
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.expenses.domain.ExpensesInteractor
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneDestination
import com.inwords.expenses.feature.expenses.ui.common.DebtShortUiModel
import com.inwords.expenses.feature.expenses.ui.converter.toUiModel
import com.inwords.expenses.feature.expenses.ui.debts_list.DebtsListPaneUiModel.PersonUiModel
import com.inwords.expenses.feature.expenses.ui.utils.toRoundedString
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

internal class DebtsListViewModel(
    private val navigationController: NavigationController,
    getCurrentEventStateUseCase: GetCurrentEventStateUseCase,
    expensesInteractor: ExpensesInteractor,
    viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + IO),
) : ViewModel(viewModelScope = viewModelScope) {

    val state: StateFlow<SimpleScreenState<DebtsListPaneUiModel>> = getCurrentEventStateUseCase.currentEvent
        .filterNotNull() // TODO mvp
        .flatMapLatestNoBuffer { expensesInteractor.getExpensesDetails(it) }
        .map { expensesDetails ->
            val creditors = hashMapOf<PersonUiModel, ImmutableList<DebtShortUiModel>>()
            expensesDetails.debtCalculator.barterAccumulatedDebts.forEach { (debtor, barterAccumulatedDebts) ->
                creditors[debtor.toUiModel()] = barterAccumulatedDebts.map { (creditor, barterAccumulatedDebt) ->
                    DebtShortUiModel(
                        personId = creditor.id,
                        personName = creditor.name,
                        currencyCode = barterAccumulatedDebt.currency.code,
                        currencyName = barterAccumulatedDebt.currency.name,
                        amount = barterAccumulatedDebt.barterAmount.toRoundedString()
                    )
                }.asImmutableListAdapter()
            }

            SimpleScreenState.Success(
                data = DebtsListPaneUiModel(
                    eventName = expensesDetails.event.event.name,
                    creditors = creditors.asImmutableMap()
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
