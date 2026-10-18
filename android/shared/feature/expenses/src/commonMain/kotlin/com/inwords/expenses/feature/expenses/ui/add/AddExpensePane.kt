package com.inwords.expenses.feature.expenses.ui.add

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.inwords.expenses.core.ui.design.appbar.TopAppBarWithNavIconAndText
import com.inwords.expenses.core.ui.design.button.ButtonWithIconAndText
import com.inwords.expenses.core.ui.design.loading.DefaultProgressIndicator
import com.inwords.expenses.core.ui.design.selection.SingleChoiceBottomSheet
import com.inwords.expenses.core.ui.design.selection.SingleChoiceOption
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.core.utils.asImmutableMap
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneUiModel.CurrencyInfoUiModel
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneUiModel.ExpenseSplitWithPersonUiModel
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneUiModel.PersonInfoUiModel
import expenses.shared.core.ui_design.generated.resources.common_close
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.common_error
import expenses.shared.feature.expenses.generated.resources.expenses_add_choose_payer
import expenses.shared.feature.expenses.generated.resources.expenses_add_choose_recipients
import expenses.shared.feature.expenses.generated.resources.expenses_add_choose_sender
import expenses.shared.feature.expenses.generated.resources.expenses_add_context
import expenses.shared.feature.expenses.generated.resources.expenses_add_description
import expenses.shared.feature.expenses.generated.resources.expenses_add_description_hint
import expenses.shared.feature.expenses.generated.resources.expenses_add_from
import expenses.shared.feature.expenses.generated.resources.expenses_add_people
import expenses.shared.feature.expenses.generated.resources.expenses_add_repayment_description
import expenses.shared.feature.expenses.generated.resources.expenses_add_repayment_title
import expenses.shared.feature.expenses.generated.resources.expenses_add_save
import expenses.shared.feature.expenses.generated.resources.expenses_add_save_repayment
import expenses.shared.feature.expenses.generated.resources.expenses_add_split_custom
import expenses.shared.feature.expenses.generated.resources.expenses_add_split_equally
import expenses.shared.feature.expenses.generated.resources.expenses_add_split_title
import expenses.shared.feature.expenses.generated.resources.expenses_add_title
import expenses.shared.feature.expenses.generated.resources.expenses_add_to
import expenses.shared.feature.expenses.generated.resources.expenses_add_total_custom
import expenses.shared.feature.expenses.generated.resources.expenses_currency
import expenses.shared.feature.expenses.generated.resources.expenses_expense
import expenses.shared.feature.expenses.generated.resources.expenses_no_expenses
import expenses.shared.feature.expenses.generated.resources.expenses_paid_by
import expenses.shared.feature.expenses.generated.resources.expenses_repayment
import expenses.shared.feature.expenses.generated.resources.expenses_split
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import expenses.shared.core.ui_design.generated.resources.Res as CoreRes

@Composable
internal fun AddExpensePane(
    state: SimpleScreenState<AddExpensePaneUiModel>,
    onCurrencyClicked: (CurrencyInfoUiModel) -> Unit,
    onExchangeRateChanged: (String) -> Unit,
    onExpenseTypeClicked: (ExpenseType) -> Unit,
    onPersonClicked: (PersonInfoUiModel) -> Unit,
    onSubjectPersonClicked: (PersonInfoUiModel) -> Unit,
    onEqualSplitChange: (Boolean) -> Unit,
    onWholeAmountChanged: (String) -> Unit,
    onSplitAmountChanged: (ExpenseSplitWithPersonUiModel, String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onConfirmClicked: () -> Unit,
    onNavIconClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val showSplitPane = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) &&
        windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) &&
        LocalDensity.current.fontScale < 1.5f
    val successData = (state as? SimpleScreenState.Success)?.data
    val isRepayment = successData?.expenseType == ExpenseType.Replenishment
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBarWithNavIconAndText(
                onNavIconClicked = onNavIconClicked,
                title = stringResource(
                    if (isRepayment) Res.string.expenses_add_repayment_title else Res.string.expenses_add_title
                ),
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(CoreRes.string.common_close),
            )
        },
        bottomBar = {
            if (successData != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier.navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 960.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = if (showSplitPane) Alignment.CenterEnd else Alignment.Center,
                        ) {
                            ButtonWithIconAndText(
                                modifier = Modifier
                                    .widthIn(max = if (showSplitPane) 240.dp else 480.dp)
                                    .fillMaxWidth()
                                    .testTag("add_expense_save_button"),
                                onClick = onConfirmClicked,
                                text = stringResource(
                                    if (isRepayment) Res.string.expenses_add_save_repayment else Res.string.expenses_add_save
                                ),
                                imageVector = Icons.Outlined.Check,
                                enabled = successData.canSave,
                                minHeight = ButtonDefaults.MediumContainerHeight,
                            )
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (state) {
                is SimpleScreenState.Success -> AddExpensePaneSuccess(
                    state = state.data,
                    isRepayment = isRepayment,
                    showSplitPane = showSplitPane,
                    onCurrencyClicked = onCurrencyClicked,
                    onExchangeRateChanged = onExchangeRateChanged,
                    onExpenseTypeClicked = onExpenseTypeClicked,
                    onPersonClicked = onPersonClicked,
                    onSubjectPersonClicked = onSubjectPersonClicked,
                    onEqualSplitChange = onEqualSplitChange,
                    onWholeAmountChanged = onWholeAmountChanged,
                    onSplitAmountChanged = onSplitAmountChanged,
                    onDescriptionChanged = onDescriptionChanged,
                )

                SimpleScreenState.Loading -> AddExpensePaneLoading()

                SimpleScreenState.Error -> Text(
                    modifier = Modifier.padding(24.dp),
                    text = stringResource(Res.string.common_error)
                )

                SimpleScreenState.Empty -> Text(
                    modifier = Modifier.padding(24.dp),
                    text = stringResource(Res.string.expenses_no_expenses)
                )
            }
        }
    }
}

@Composable
private fun AddExpensePaneLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        DefaultProgressIndicator()
    }
}

@Composable
private fun AddExpensePaneSuccess(
    state: AddExpensePaneUiModel,
    isRepayment: Boolean,
    showSplitPane: Boolean,
    onCurrencyClicked: (CurrencyInfoUiModel) -> Unit,
    onExchangeRateChanged: (String) -> Unit,
    onExpenseTypeClicked: (ExpenseType) -> Unit,
    onPersonClicked: (PersonInfoUiModel) -> Unit,
    onSubjectPersonClicked: (PersonInfoUiModel) -> Unit,
    onEqualSplitChange: (Boolean) -> Unit,
    onWholeAmountChanged: (String) -> Unit,
    onSplitAmountChanged: (ExpenseSplitWithPersonUiModel, String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
) {
    val people = pluralStringResource(
        resource = Res.plurals.expenses_add_people,
        quantity = state.selectedSubjectPersonsCount,
        state.selectedSubjectPersonsCount
    )
    val splitTitle = stringResource(
        if (isRepayment) Res.string.expenses_add_choose_recipients else Res.string.expenses_add_split_title
    )

    val expenseTypeColors = ToggleButtonDefaults.tonalToggleButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    var isCurrencyPickerVisible by rememberSaveable { mutableStateOf(false) }
    var isPersonPickerVisible by rememberSaveable { mutableStateOf(false) }
    var isSplitPickerVisible by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .widthIn(max = if (showSplitPane) 960.dp else 480.dp)
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.eventName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(Res.string.expenses_add_context, state.primaryCurrencyCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                TonalToggleButton(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    checked = state.expenseType == ExpenseType.Spending,
                    onCheckedChange = { onExpenseTypeClicked(ExpenseType.Spending) },
                    colors = expenseTypeColors,
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                ) { Text(text = stringResource(Res.string.expenses_expense)) }
                TonalToggleButton(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    checked = state.expenseType == ExpenseType.Replenishment,
                    onCheckedChange = { onExpenseTypeClicked(ExpenseType.Replenishment) },
                    colors = expenseTypeColors,
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                ) { Text(text = stringResource(Res.string.expenses_repayment)) }
            }
            Column {
                ExpenseAmountInput(
                    modifier = Modifier.fillMaxWidth(),
                    amount = if (state.equalSplit) state.wholeAmount else state.totalAmount,
                    currencyCode = state.selectedCurrencyCode,
                    readOnly = !state.equalSplit,
                    isCurrencyPickerVisible = isCurrencyPickerVisible,
                    enabled = true,
                    onAmountChanged = onWholeAmountChanged,
                    onCurrencyClicked = { isCurrencyPickerVisible = true },
                )
                if (!state.equalSplit) {
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = stringResource(Res.string.expenses_add_total_custom),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val motionScheme = MaterialTheme.motionScheme
                AnimatedContent(
                    targetState = state.exchangeRate,
                    modifier = Modifier.fillMaxWidth(),
                    contentKey = { it != null },
                    transitionSpec = {
                        fadeIn(motionScheme.fastEffectsSpec())
                            .togetherWith(fadeOut(motionScheme.fastEffectsSpec()))
                            .using(SizeTransform { _, _ -> motionScheme.defaultSpatialSpec() })
                    },
                ) { exchangeRate ->
                    if (exchangeRate != null) {
                        ExpenseExchangeRateInput(
                            modifier = Modifier.padding(top = 24.dp),
                            exchangeRate = exchangeRate,
                            enabled = true,
                            onExchangeRateChanged = onExchangeRateChanged,
                        )
                    }
                }
            }
            val keyboardController = LocalSoftwareKeyboardController.current
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().testTag("add_expense_description_input"),
                value = state.description,
                onValueChange = onDescriptionChanged,
                label = {
                    Text(
                        text = stringResource(
                            if (isRepayment) Res.string.expenses_add_repayment_description else Res.string.expenses_add_description
                        )
                    )
                },
                placeholder = {
                    Text(text = stringResource(Res.string.expenses_add_description_hint))
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = true,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                singleLine = true,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column {
                    ExpenseSettingsRow(
                        modifier = Modifier.testTag("add_expense_payer_button"),
                        label = stringResource(
                            if (isRepayment) Res.string.expenses_add_from else Res.string.expenses_paid_by
                        ),
                        value = state.selectedPersonName,
                        imageVector = Icons.Outlined.Person,
                        enabled = true,
                        onClick = { isPersonPickerVisible = true },
                        supportingContent = null,
                    )
                    if (!showSplitPane) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ExpenseSettingsRow(
                            modifier = Modifier.testTag("add_expense_split_button"),
                            label = stringResource(
                                if (isRepayment) Res.string.expenses_add_to else Res.string.expenses_split
                            ),
                            value = if (isRepayment) {
                                if (state.selectedSubjectPersonsCount == 1) state.selectedSubjectPersonsNames else people
                            } else {
                                stringResource(
                                    resource = if (state.equalSplit) {
                                        Res.string.expenses_add_split_equally
                                    } else {
                                        Res.string.expenses_add_split_custom
                                    },
                                    people
                                )
                            },
                            imageVector = Icons.AutoMirrored.Outlined.List,
                            enabled = true,
                            onClick = { isSplitPickerVisible = true },
                            supportingContent = {
                                ExpenseEqualShareSummary(
                                    amount = state.equalShareAmount,
                                    currencyCode = state.selectedCurrencyCode,
                                )
                            },
                        )
                    }
                }
            }
        }
        if (showSplitPane) {
            Surface(
                modifier = Modifier.weight(0.82f)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                ExpenseSplitEditor(
                    modifier = Modifier.padding(24.dp),
                    title = splitTitle,
                    persons = state.subjectPersons,
                    selectedPersonsCount = state.selectedSubjectPersonsCount,
                    lastSplitPersonId = state.lastSplitPersonId,
                    splitByPerson = state.splitByPerson,
                    equalSplit = state.equalSplit,
                    totalAmount = state.totalAmount,
                    currencyCode = state.selectedCurrencyCode,
                    enabled = true,
                    onSubjectPersonClicked = onSubjectPersonClicked,
                    onEqualSplitChange = onEqualSplitChange,
                    onSplitAmountChanged = onSplitAmountChanged,
                    onDoneClicked = null,
                )
            }
        }
    }
    if (isCurrencyPickerVisible) {
        SingleChoiceBottomSheet(
            modifier = Modifier.testTag("add_expense_currency_sheet"),
            title = stringResource(Res.string.expenses_currency),
            options = remember(state.currencies) {
                state.currencies.map { currency ->
                    SingleChoiceOption(
                        key = currency.currencyCode,
                        text = currency.currencyCode,
                        supportingText = currency.currencyName,
                        selected = currency.selected,
                        payload = currency,
                        testTag = "add_expense_currency_option_${currency.currencyCode}",
                    )
                }
            },
            onOptionClicked = onCurrencyClicked,
            onDismissRequest = { isCurrencyPickerVisible = false },
        )
    }
    if (isPersonPickerVisible) {
        SingleChoiceBottomSheet(
            modifier = Modifier.testTag("add_expense_payer_sheet"),
            title = stringResource(
                if (isRepayment) Res.string.expenses_add_choose_sender else Res.string.expenses_add_choose_payer
            ),
            options = remember(state.persons) {
                state.persons.map { person ->
                    SingleChoiceOption(
                        key = person.personId.toString(),
                        text = person.personName,
                        supportingText = null,
                        selected = person.selected,
                        payload = person,
                        testTag = "add_expense_payer_${person.personId}",
                    )
                }
            },
            onOptionClicked = onPersonClicked,
            onDismissRequest = { isPersonPickerVisible = false },
        )
    }
    if (isSplitPickerVisible && !showSplitPane) {
        ExpenseSplitBottomSheet(
            state = state,
            title = splitTitle,
            currencyCode = state.selectedCurrencyCode,
            onSubjectPersonClicked = onSubjectPersonClicked,
            onEqualSplitChange = onEqualSplitChange,
            onSplitAmountChanged = onSplitAmountChanged,
            onDismissRequest = { isSplitPickerVisible = false },
        )
    }
}

@Preview(name = "Phone", widthDp = 360, heightDp = 800)
@Preview(name = "Large text", widthDp = 360, heightDp = 800, fontScale = 2f)
@Preview(name = "Expanded", widthDp = 960, heightDp = 640)
@Composable
private fun AddExpensePaneSuccessEqualSplitPreview() {
    CommonExTheme {
        AddExpensePane(
            onCurrencyClicked = {},
            onExchangeRateChanged = {},
            onExpenseTypeClicked = {},
            onPersonClicked = {},
            onSubjectPersonClicked = {},
            onEqualSplitChange = {},
            onDescriptionChanged = {},
            onWholeAmountChanged = {},
            onSplitAmountChanged = { _, _ -> },
            onConfirmClicked = {},
            onNavIconClicked = {},
            state = SimpleScreenState.Success(mockAddExpenseScreenUiModel().copy(equalSplit = true, wholeAmount = "60.00", equalShareAmount = "20.00")),
        )
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Composable
private fun AddExpensePaneSuccessPreview() {
    CommonExTheme {
        AddExpensePane(
            onCurrencyClicked = {},
            onExchangeRateChanged = {},
            onExpenseTypeClicked = {},
            onPersonClicked = {},
            onSubjectPersonClicked = {},
            onEqualSplitChange = {},
            onDescriptionChanged = {},
            onWholeAmountChanged = {},
            onSplitAmountChanged = { _, _ -> },
            onConfirmClicked = {},
            onNavIconClicked = {},
            state = SimpleScreenState.Success(mockAddExpenseScreenUiModel()),
        )
    }
}

internal fun mockAddExpenseScreenUiModel(): AddExpensePaneUiModel {
    val alex = PersonInfoUiModel(personId = 1, personName = "Alex", selected = true)
    val sam = PersonInfoUiModel(personId = 2, personName = "Sam", selected = true)
    val jo = PersonInfoUiModel(personId = 3, personName = "Jo", selected = true)
    val split = persistentListOf(
        ExpenseSplitWithPersonUiModel(person = alex, amount = "20.00"),
        ExpenseSplitWithPersonUiModel(person = sam, amount = "20.00"),
        ExpenseSplitWithPersonUiModel(person = jo, amount = "20.00"),
    )
    return AddExpensePaneUiModel(
        eventName = "Weekend in Lisbon",
        expenseType = ExpenseType.Spending,
        description = "Dinner",
        wholeAmount = "60.00",
        totalAmount = "60.00",
        primaryCurrencyCode = "EUR",
        currencies = persistentListOf(
            CurrencyInfoUiModel(currencyName = "Euro", currencyCode = "EUR", selected = true),
            CurrencyInfoUiModel(currencyName = "US Dollar", currencyCode = "USD", selected = false),
            CurrencyInfoUiModel(currencyName = "Russian Ruble", currencyCode = "RUB", selected = false),
        ),
        selectedCurrencyCode = "EUR",
        exchangeRate = null,
        persons = persistentListOf(alex, sam.copy(selected = false), jo.copy(selected = false)),
        selectedPersonName = alex.personName,
        subjectPersons = persistentListOf(alex, sam, jo),
        selectedSubjectPersonsCount = 3,
        selectedSubjectPersonsNames = "Alex, Sam, Jo",
        equalSplit = false,
        equalShareAmount = null,
        split = split,
        splitByPerson = split.associateBy { it.person.personId }.asImmutableMap(),
        lastSplitPersonId = jo.personId,
        canSave = true,
    )
}
