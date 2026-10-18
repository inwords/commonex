package com.inwords.expenses.feature.expenses.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.button.ButtonWithIconAndText
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneUiModel.ExpenseSplitWithPersonUiModel
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneUiModel.PersonInfoUiModel
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_add_by_amount
import expenses.shared.feature.expenses.generated.resources.expenses_add_done
import expenses.shared.feature.expenses.generated.resources.expenses_add_select_people
import expenses.shared.feature.expenses.generated.resources.expenses_add_split_title
import expenses.shared.feature.expenses.generated.resources.expenses_equally
import expenses.shared.feature.expenses.generated.resources.expenses_total_amount
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExpenseSplitEditor(
    title: String,
    persons: ImmutableList<PersonInfoUiModel>,
    selectedPersonsCount: Int,
    lastSplitPersonId: Long?,
    splitByPerson: ImmutableMap<Long, ExpenseSplitWithPersonUiModel>,
    equalSplit: Boolean,
    totalAmount: String,
    currencyCode: String,
    enabled: Boolean,
    onSubjectPersonClicked: (PersonInfoUiModel) -> Unit,
    onEqualSplitChange: (Boolean) -> Unit,
    onSplitAmountChanged: (ExpenseSplitWithPersonUiModel, String) -> Unit,
    onDoneClicked: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodyLarge
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val fieldPadding = OutlinedTextFieldDefaults.contentPaddingWithoutLabel()
    // Stable content budgets keep typing from changing the row layout.
    val minPersonWidth = with(density) {
        textMeasurer.measure("Alexandra", style = textStyle, softWrap = false).size.width.toDp() +
            24.dp + 12.dp // Passive checkbox including its padding, then the gap before the name.
    }
    val minAmountWidth = with(density) {
        textMeasurer.measure("-1,234.56", style = textStyle, softWrap = false).size.width.toDp() +
            textMeasurer.measure(currencyCode, style = textStyle, softWrap = false).size.width.toDp() +
            fieldPadding.calculateLeftPadding(layoutDirection) + fieldPadding.calculateRightPadding(layoutDirection) +
            4.dp // Material's suffix gap and space for the input caret.
    }
    LazyColumn(
        modifier = modifier.testTag("add_expense_split_editor"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "title", contentType = "header") {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
        }
        item(key = "mode", contentType = "mode") {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                TonalToggleButton(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("split_equal_button"),
                    checked = equalSplit,
                    enabled = enabled,
                    onCheckedChange = { onEqualSplitChange(true) },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                ) { Text(stringResource(Res.string.expenses_equally)) }
                TonalToggleButton(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("split_custom_button"),
                    checked = !equalSplit,
                    enabled = enabled,
                    onCheckedChange = { onEqualSplitChange(false) },
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                ) { Text(stringResource(Res.string.expenses_add_by_amount)) }
            }
        }

        items(persons, key = { it.personId }, contentType = { "person" }) { person ->
            val personSplit = splitByPerson[person.personId]
            val amountInput: @Composable (Modifier) -> Unit = { amountModifier ->
                if (!equalSplit && person.selected && personSplit != null) {
                    val keyboardController = LocalSoftwareKeyboardController.current
                    OutlinedTextField(
                        modifier = amountModifier
                            .testTag("add_expense_split_amount_${person.personId}")
                            .semantics { contentDescription = person.personName },
                        value = personSplit.amount,
                        textStyle = textStyle,
                        onValueChange = { onSplitAmountChanged(personSplit, it) },
                        enabled = enabled,
                        suffix = { Text(currencyCode) },
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Decimal,
                            imeAction = if (person.personId == lastSplitPersonId) {
                                ImeAction.Done
                            } else {
                                ImeAction.Next
                            },
                        ),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                        singleLine = true,
                    )
                }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val amountFitsInline = maxWidth >= minPersonWidth + minAmountWidth + 12.dp
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(minPersonWidth.value)
                                .widthIn(min = minPersonWidth)
                                .heightIn(min = 64.dp)
                                .testTag("add_expense_split_person_${person.personId}")
                                .toggleable(
                                    value = person.selected,
                                    enabled = enabled,
                                    role = Role.Checkbox,
                                    onValueChange = { onSubjectPersonClicked(person) },
                                ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = person.selected,
                                onCheckedChange = null,
                                enabled = enabled,
                            )
                            Text(
                                text = person.personName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (equalSplit && person.selected && personSplit != null) {
                                Text(
                                    text = personSplit.amount,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                        if (amountFitsInline) {
                            amountInput(Modifier.weight(minAmountWidth.value).widthIn(min = minAmountWidth))
                        }
                    }
                    if (!amountFitsInline) {
                        amountInput(Modifier.fillMaxWidth().widthIn(min = minAmountWidth))
                    }
                }
            }
        }
        if (selectedPersonsCount == 0) {
            item(key = "empty", contentType = "status") {
                Text(
                    text = stringResource(Res.string.expenses_add_select_people),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item(key = "total", contentType = "total") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.expenses_total_amount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "$totalAmount $currencyCode",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        if (onDoneClicked != null) {
            item(key = "done", contentType = "action") {
                ButtonWithIconAndText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("split_done_button"),
                    onClick = onDoneClicked,
                    text = stringResource(Res.string.expenses_add_done),
                    imageVector = Icons.Outlined.Check,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpenseSplitBottomSheet(
    state: AddExpensePaneUiModel,
    title: String,
    currencyCode: String,
    onSubjectPersonClicked: (PersonInfoUiModel) -> Unit,
    onEqualSplitChange: (Boolean) -> Unit,
    onSplitAmountChanged: (ExpenseSplitWithPersonUiModel, String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = if (LocalInspectionMode.current) SheetValue.Expanded else SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        modifier = Modifier.testTag("add_expense_split_sheet"),
        sheetState = sheetState,
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ExpenseSplitEditor(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            title = title,
            persons = state.subjectPersons,
            selectedPersonsCount = state.selectedSubjectPersonsCount,
            lastSplitPersonId = state.lastSplitPersonId,
            splitByPerson = state.splitByPerson,
            equalSplit = state.equalSplit,
            totalAmount = state.totalAmount,
            currencyCode = currencyCode,
            enabled = true,
            onSubjectPersonClicked = onSubjectPersonClicked,
            onEqualSplitChange = onEqualSplitChange,
            onSplitAmountChanged = onSplitAmountChanged,
            onDoneClicked = {
                scope.launch {
                    sheetState.hide()
                    if (!sheetState.isVisible) {
                        onDismissRequest()
                    }
                }
            },
        )
    }
}

@Preview(widthDp = 360, heightDp = 600)
@Preview(name = "Narrow", widthDp = 280, heightDp = 600)
@Preview(name = "Large text", widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun ExpenseSplitEditorPreview() {
    val state = mockAddExpenseScreenUiModel()
    CommonExTheme {
        ExpenseSplitEditor(
            title = stringResource(Res.string.expenses_add_split_title),
            persons = state.subjectPersons,
            selectedPersonsCount = state.selectedSubjectPersonsCount,
            lastSplitPersonId = state.lastSplitPersonId,
            splitByPerson = state.splitByPerson,
            equalSplit = state.equalSplit,
            totalAmount = state.totalAmount,
            currencyCode = "USD",
            enabled = true,
            onSubjectPersonClicked = {},
            onEqualSplitChange = {},
            onSplitAmountChanged = { _, _ -> },
            onDoneClicked = {},
        )
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Composable
private fun ExpenseSplitBottomSheetPreview() {
    CommonExTheme {
        ExpenseSplitBottomSheet(
            state = mockAddExpenseScreenUiModel(),
            title = stringResource(Res.string.expenses_add_split_title),
            currencyCode = "USD",
            onSubjectPersonClicked = {},
            onEqualSplitChange = {},
            onSplitAmountChanged = { _, _ -> },
            onDismissRequest = {},
        )
    }
}
