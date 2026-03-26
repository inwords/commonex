package com.inwords.expenses.feature.expenses.ui.add

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.inwords.expenses.core.navigation.BottomSheetSceneStrategy.Companion.bottomSheet
import com.inwords.expenses.core.navigation.Destination
import com.inwords.expenses.core.navigation.NavModule
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.feature.expenses.api.ExpensesComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AddExpensePaneDestination(
    @SerialName("replenishment")
    val replenishment: Replenishment? = null,
) : Destination {

    @Serializable
    data class Replenishment(
        @SerialName("fromPersonId")
        val fromPersonId: Long,
        @SerialName("toPersonId")
        val toPersonId: Long,
        @SerialName("currencyCode")
        val currencyCode: String,
        @SerialName("amount")
        val amount: String,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun ExpensesComponent.getAddExpensePaneNavModule(
    navigationController: NavigationController,
): NavModule {
    return NavModule(AddExpensePaneDestination.serializer()) {
        entry<AddExpensePaneDestination>(metadata = bottomSheet()) { key ->
            val viewModel = viewModel<AddExpenseViewModel>(factory = viewModelFactory {
                initializer {
                    AddExpenseViewModel(
                        navigationController = navigationController,
                        getCurrentEventStateUseCase = getCurrentEventStateUseCaseLazy.value,
                        addEqualSplitExpenseUseCase = addEqualSplitExpenseUseCaseLazy.value,
                        addCustomSplitExpenseUseCase = addCustomSplitExpenseUseCaseLazy.value,
                        settingsRepository = settingsRepositoryLazy.value,
                        replenishment = key.replenishment,
                    )
                }
            })
            AddExpensePane(
                state = viewModel.state.collectAsStateWithLifecycle().value,
                onCurrencyClicked = viewModel::onCurrencyClicked,
                onExchangeRateChanged = viewModel::onExchangeRateChanged,
                onExpenseTypeClicked = viewModel::onExpenseTypeClicked,
                onPersonClicked = viewModel::onPersonClicked,
                onSubjectPersonClicked = viewModel::onSubjectPersonClicked,
                onEqualSplitChange = viewModel::onEqualSplitChange,
                onWholeAmountChanged = viewModel::onWholeAmountChanged,
                onSplitAmountChanged = viewModel::onSplitAmountChanged,
                onDescriptionChanged = viewModel::onDescriptionChanged,
                onConfirmClicked = viewModel::onConfirmClicked,
            )
        }
    }
}
