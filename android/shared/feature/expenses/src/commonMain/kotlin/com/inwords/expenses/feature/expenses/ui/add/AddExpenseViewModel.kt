package com.inwords.expenses.feature.expenses.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.DefaultStringProvider
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.core.utils.UI
import com.inwords.expenses.core.utils.asImmutableListAdapter
import com.inwords.expenses.core.utils.combine
import com.inwords.expenses.core.utils.flatMapLatestNoBuffer
import com.inwords.expenses.core.utils.stateInWhileSubscribed
import com.inwords.expenses.core.utils.toBigDecimalOrNull
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.AddCustomSplitExpenseUseCase
import com.inwords.expenses.feature.expenses.domain.AddEqualSplitExpenseUseCase
import com.inwords.expenses.feature.expenses.domain.EqualSplitCalculator
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.model.PersonWithAmount
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneDestination.Replenishment
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneUiModel.CurrencyInfoUiModel
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneUiModel.ExpenseSplitWithPersonUiModel
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneUiModel.PersonInfoUiModel
import com.inwords.expenses.feature.expenses.ui.add.AddExpenseViewModel.AddExpenseScreenModel.AmountModel
import com.inwords.expenses.feature.expenses.ui.add.AddExpenseViewModel.AddExpenseScreenModel.ExpenseSplitWithPersonModel
import com.inwords.expenses.feature.expenses.ui.add.AddExpenseViewModel.AddExpenseScreenModel.PersonInfoModel
import com.inwords.expenses.feature.expenses.ui.utils.toRoundedString
import com.inwords.expenses.feature.settings.api.SettingsRepository
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_no_description
import expenses.shared.feature.expenses.generated.resources.expenses_repayment_from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

internal class AddExpenseViewModel(
    private val navigationController: NavigationController,
    getCurrentEventStateUseCase: GetCurrentEventStateUseCase,
    private val addEqualSplitExpenseUseCase: AddEqualSplitExpenseUseCase,
    private val addCustomSplitExpenseUseCase: AddCustomSplitExpenseUseCase,
    settingsRepository: SettingsRepository,
    private val replenishment: Replenishment?,
    private val stringProvider: StringProvider = DefaultStringProvider,
    viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + IO),
) : ViewModel(viewModelScope = viewModelScope) {

    private data class AddExpenseScreenModel(
        val event: Event,
        val description: String,
        val currencies: List<CurrencyInfoModel>,
        val expenseType: ExpenseType,
        val persons: List<PersonInfoModel>,
        val subjectPersons: List<PersonInfoModel>,
        val equalSplit: Boolean,
        val wholeAmount: AmountModel,
        val split: List<ExpenseSplitWithPersonModel>?,
        val canSave: Boolean,
    ) {

        data class CurrencyInfoModel(
            val currency: Currency,
            val selected: Boolean,
        )

        data class PersonInfoModel(
            val person: Person,
            val selected: Boolean,
        )

        data class ExpenseSplitWithPersonModel(
            val person: PersonInfoModel,
            val amount: AmountModel,
        )

        data class AmountModel(
            val amount: BigDecimal?,
            val amountRaw: String,
        )
    }

    private var confirmJob: Job? = null

    private val selectedExpenseType = MutableStateFlow(replenishment?.let { ExpenseType.Replenishment } ?: ExpenseType.Spending)
    private val selectedCurrencyCode = MutableStateFlow(replenishment?.currencyCode)
    private val selectedPersonId = MutableStateFlow(replenishment?.fromPersonId)
    private val selectedSubjectPersonsIds = MutableStateFlow(replenishment?.toPersonId?.let { setOf(it) })
    private val inputDescription = MutableStateFlow(if (replenishment == null) "" else null)
    private val inputEqualSplit = MutableStateFlow(replenishment == null)
    private val inputWholeAmount = MutableStateFlow(
        if (replenishment == null) {
            AmountModel(null, "")
        } else {
            AmountModel(
                amount = replenishment.amount.toBigDecimalOrNull(),
                amountRaw = replenishment.amount
            )
        }
    )
    private val inputSplit = MutableStateFlow<List<ExpenseSplitWithPersonModel>?>(null)

    private val _state: StateFlow<SimpleScreenState<AddExpenseScreenModel>> = combine(
        getCurrentEventStateUseCase.currentEvent,
        selectedExpenseType,
        selectedCurrencyCode,
        selectedPersonId.flatMapLatestNoBuffer {
            it?.let { flowOf(it) } ?: settingsRepository.getCurrentPersonId()
        },
        selectedSubjectPersonsIds,
        inputDescription,
        inputEqualSplit,
        inputWholeAmount,
        inputSplit,
    ) { eventDetails,
        selectedExpenseType,
        selectedCurrencyCode,
        selectedPersonId,
        selectedSubjectPersonsIds,
        inputDescription,
        inputEqualSplit,
        inputWholeAmount,
        inputSplit ->

        eventDetails ?: return@combine SimpleScreenState.Error // can't work without event
        selectedPersonId ?: return@combine SimpleScreenState.Error // it's current person if not selected, can't work without current person

        val selectedPerson = eventDetails.persons.firstOrNull { it.id == selectedPersonId }
            ?: return@combine SimpleScreenState.Error // selected person must be in event
        val persons = eventDetails.persons.map { person ->
            PersonInfoModel(
                person = person,
                selected = person == selectedPerson
            )
        }
        val subjectPersons = eventDetails.persons.map { person ->
            PersonInfoModel(
                person = person,
                selected = selectedSubjectPersonsIds?.contains(person.id) ?: true
            )
        }

        val selectedCurrency = eventDetails.currencies
            .firstOrNull { it.code == selectedCurrencyCode }
            ?: eventDetails.primaryCurrency

        val split = ensureSplitCalculated(
            equalSplit = inputEqualSplit,
            wholeAmount = inputWholeAmount,
            split = inputSplit ?: if (replenishment == null) {
                emptyList()
            } else {
                listOf(
                    ExpenseSplitWithPersonModel(
                        person = subjectPersons.first { it.selected },
                        amount = AmountModel(
                            amount = replenishment.amount.toBigDecimalOrNull(),
                            amountRaw = replenishment.amount
                        )
                    )
                )
            },
            subjectPersons = subjectPersons
        )

        val model = AddExpenseScreenModel(
            event = eventDetails.event,
            description = inputDescription ?: run {
                stringProvider.getString(Res.string.expenses_repayment_from, selectedPerson.name)
            },
            currencies = eventDetails.currencies.map { currency ->
                AddExpenseScreenModel.CurrencyInfoModel(
                    currency = currency,
                    selected = currency == selectedCurrency,
                )
            },
            expenseType = selectedExpenseType,
            persons = persons,
            subjectPersons = subjectPersons,
            equalSplit = inputEqualSplit,
            wholeAmount = inputWholeAmount,
            split = split,
            canSave = calculateCanSave(
                equalSplit = inputEqualSplit,
                wholeAmount = inputWholeAmount,
                split = split
            )
        )
        SimpleScreenState.Success(model)
    }.stateInWhileSubscribed(viewModelScope + UI, initialValue = SimpleScreenState.Loading)

    val state: StateFlow<SimpleScreenState<AddExpensePaneUiModel>> = _state
        .map { state ->
            when (state) {
                SimpleScreenState.Empty -> SimpleScreenState.Empty
                SimpleScreenState.Error -> SimpleScreenState.Error
                SimpleScreenState.Loading -> SimpleScreenState.Loading
                is SimpleScreenState.Success -> SimpleScreenState.Success(state.data.toUiModel())
            }
        }
        .stateInWhileSubscribed(viewModelScope + UI, initialValue = SimpleScreenState.Loading)

    fun onExpenseTypeClicked(type: ExpenseType) {
        selectedExpenseType.value = type
    }

    fun onCurrencyClicked(currency: CurrencyInfoUiModel) {
        selectedCurrencyCode.value = currency.currencyCode
    }

    fun onPersonClicked(person: PersonInfoUiModel) {
        selectedPersonId.value = person.personId
    }

    fun onSubjectPersonClicked(person: PersonInfoUiModel) {
        selectedSubjectPersonsIds.update { current ->
            val selectedSubjectPersonsIds = if (current == null) {
                // initialize set if it's null
                val state = (_state.value as? SimpleScreenState.Success)?.data ?: return@update current
                state.subjectPersons.mapTo(HashSet()) { it.person.id }
            } else {
                current
            }

            if (selectedSubjectPersonsIds.contains(person.personId)) {
                selectedSubjectPersonsIds - person.personId
            } else {
                selectedSubjectPersonsIds + person.personId
            }
        }
    }

    fun onEqualSplitChange(equalSplit: Boolean) {
        inputEqualSplit.value = equalSplit
    }

    private fun ensureSplitCalculated(
        equalSplit: Boolean,
        wholeAmount: AmountModel?,
        split: List<ExpenseSplitWithPersonModel>,
        subjectPersons: List<PersonInfoModel>,
    ): List<ExpenseSplitWithPersonModel> {
        return if (equalSplit) {
            split
        } else {
            val selectedSubjectPersons = subjectPersons.filter { it.selected }

            val newSplit = split.ifEmpty {
                val amount = wholeAmount?.amount?.let { amount ->
                    EqualSplitCalculator.calculateDraftAmount(
                        amount = amount,
                        selectedSubjectPersonsSize = selectedSubjectPersons.size
                    )
                }
                selectedSubjectPersons.map { personInfoModel ->
                    ExpenseSplitWithPersonModel(
                        person = personInfoModel,
                        amount = AmountModel(amount, amount?.toRoundedString(2).orEmpty())
                    )
                }
            }

            if (newSplit.map { it.person } == selectedSubjectPersons) {
                newSplit
            } else {
                selectedSubjectPersons.map { personInfoModel ->
                    newSplit.firstOrNull { it.person.person.id == personInfoModel.person.id } ?: ExpenseSplitWithPersonModel(
                        person = personInfoModel,
                        amount = AmountModel(null, ""),
                    )
                }
            }
        }
    }

    fun onWholeAmountChanged(amount: String) {
        val newAmount = amount.parseToAmountModel()

        inputWholeAmount.value = newAmount
    }

    fun onSplitAmountChanged(person: ExpenseSplitWithPersonUiModel, amount: String) {
        val newAmount = amount.parseToAmountModel()

        inputSplit.update { current ->
            val split = current ?: (_state.value as? SimpleScreenState.Success)?.data?.split ?: return@update current

            split.map { splitWithPerson ->
                if (splitWithPerson.person.person.id == person.person.personId) {
                    splitWithPerson.copy(amount = newAmount)
                } else {
                    splitWithPerson
                }
            }
        }
    }

    private fun String.parseToAmountModel(): AmountModel {
        val trimmedAmount = this.trim()
        return AmountModel(
            amount = trimmedAmount.toBigDecimalOrNull(),
            amountRaw = trimmedAmount
        )
    }

    fun onDescriptionChanged(description: String) {
        inputDescription.value = description
    }

    fun onConfirmClicked() {
        val state = (_state.value as? SimpleScreenState.Success)?.data ?: return

        if (confirmJob?.isActive == true) {
            return
        }
        confirmJob = viewModelScope.launch {
            val selectedCurrency = state.currencies.firstOrNull { it.selected }?.currency ?: return@launch
            val selectedPerson = state.persons.firstOrNull { it.selected }?.person ?: return@launch
            val description = state.description.trim().ifEmpty { stringProvider.getString(Res.string.expenses_no_description) }
            if (state.equalSplit) {
                addEqualSplitExpenseUseCase.addExpense(
                    event = state.event,
                    wholeAmount = state.wholeAmount.amount ?: return@launch,
                    expenseType = state.expenseType,
                    description = description,
                    selectedSubjectPersons = state.subjectPersons.filter { it.selected }.map { it.person },
                    selectedCurrency = selectedCurrency,
                    selectedPerson = selectedPerson,
                )
            } else {
                val personWithAmountSplit = state.split?.map {
                    PersonWithAmount(it.person.person, it.amount.amount ?: return@launch)
                } ?: return@launch
                addCustomSplitExpenseUseCase.addExpense(
                    event = state.event,
                    expenseType = state.expenseType,
                    description = description,
                    selectedCurrency = selectedCurrency,
                    selectedPerson = selectedPerson,
                    personWithAmountSplit = personWithAmountSplit
                )
            }

            navigationController.popBackStack()
        }
    }

    private fun AddExpenseScreenModel.toUiModel(): AddExpensePaneUiModel {
        return AddExpensePaneUiModel(
            description = this.description,
            currencies = this.currencies.map { currencyInfoModel ->
                CurrencyInfoUiModel(
                    currencyName = currencyInfoModel.currency.name,
                    currencyCode = currencyInfoModel.currency.code,
                    selected = currencyInfoModel.selected
                )
            }.asImmutableListAdapter(),
            expenseType = this.expenseType,
            persons = this.persons.map { personInfoModel ->
                PersonInfoUiModel(
                    personId = personInfoModel.person.id,
                    personName = personInfoModel.person.name,
                    selected = personInfoModel.selected
                )
            }.asImmutableListAdapter(),
            subjectPersons = this.subjectPersons.map { personInfoModel ->
                PersonInfoUiModel(
                    personId = personInfoModel.person.id,
                    personName = personInfoModel.person.name,
                    selected = personInfoModel.selected
                )
            }.asImmutableListAdapter(),
            equalSplit = this.equalSplit,
            wholeAmount = this.wholeAmount.amountRaw,
            split = this.split.orEmpty().map { expenseSplitWithPersonModel ->
                ExpenseSplitWithPersonUiModel(
                    person = PersonInfoUiModel(
                        personId = expenseSplitWithPersonModel.person.person.id,
                        personName = expenseSplitWithPersonModel.person.person.name,
                        selected = expenseSplitWithPersonModel.person.selected
                    ),
                    amount = expenseSplitWithPersonModel.amount.amountRaw,
                )
            }.asImmutableListAdapter(),
            canSave = this.canSave,
        )
    }

    private fun calculateCanSave(
        equalSplit: Boolean,
        wholeAmount: AmountModel,
        split: List<ExpenseSplitWithPersonModel>,
    ): Boolean {
        return if (equalSplit) {
            wholeAmount.amount != null
        } else {
            split.isNotEmpty() && split.all { it.amount.amount != null }
        }
    }

}
