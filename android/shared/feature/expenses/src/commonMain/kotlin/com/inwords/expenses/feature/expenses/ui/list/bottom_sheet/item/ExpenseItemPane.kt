package com.inwords.expenses.feature.expenses.ui.list.bottom_sheet.item

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.icons.Undo
import com.inwords.expenses.core.ui.design.loading.DefaultProgressIndicator
import com.inwords.expenses.core.ui.design.preview.ComponentPreviews
import com.inwords.expenses.core.ui.design.preview.ScreenPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.ui.list.bottom_sheet.item.ExpenseItemPaneUiModel.PersonSplitUiModel
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.common_error
import expenses.shared.feature.expenses.generated.resources.expenses_date
import expenses.shared.feature.expenses.generated.resources.expenses_details_collapsed
import expenses.shared.feature.expenses.generated.resources.expenses_details_expanded
import expenses.shared.feature.expenses.generated.resources.expenses_details_split
import expenses.shared.feature.expenses.generated.resources.expenses_exchange_rate
import expenses.shared.feature.expenses.generated.resources.expenses_exchange_rate_value
import expenses.shared.feature.expenses.generated.resources.expenses_from_person
import expenses.shared.feature.expenses.generated.resources.expenses_no_description
import expenses.shared.feature.expenses.generated.resources.expenses_no_expenses
import expenses.shared.feature.expenses.generated.resources.expenses_operation_details
import expenses.shared.feature.expenses.generated.resources.expenses_paid_by
import expenses.shared.feature.expenses.generated.resources.expenses_paid_by_person
import expenses.shared.feature.expenses.generated.resources.expenses_repayment
import expenses.shared.feature.expenses.generated.resources.expenses_revert_operation
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@Composable
internal fun ExpenseItemPane(
    state: SimpleScreenState<ExpenseItemPaneUiModel>,
    onRevertExpenseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        when (state) {
            is SimpleScreenState.Success -> ExpenseItemPaneSuccess(
                state = state.data,
                onRevertExpenseClick = onRevertExpenseClick,
            )

            SimpleScreenState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
                contentAlignment = Alignment.Center,
            ) {
                DefaultProgressIndicator()
            }

            SimpleScreenState.Error -> Text(
                modifier = Modifier.padding(24.dp),
                text = stringResource(Res.string.common_error),
            )

            SimpleScreenState.Empty -> Text(
                modifier = Modifier.padding(24.dp),
                text = stringResource(Res.string.expenses_no_expenses),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpenseItemPaneSuccess(
    state: ExpenseItemPaneUiModel,
    onRevertExpenseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var areOperationDetailsExpanded by rememberSaveable(state.expense.expenseId) { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .testTag("expense_item_pane_root"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        item(key = "summary", contentType = "summary") {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        modifier = Modifier
                            .testTag("expense_item_pane_description_value")
                            .semantics { heading() },
                        text = state.description.ifEmpty {
                            stringResource(
                                when (state.expense.expenseType) {
                                    ExpenseType.Spending -> Res.string.expenses_no_description
                                    ExpenseType.Replenishment -> Res.string.expenses_repayment
                                }
                            )
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(
                            when (state.expense.expenseType) {
                                ExpenseType.Spending -> Res.string.expenses_paid_by_person
                                ExpenseType.Replenishment -> Res.string.expenses_from_person
                            },
                            state.personName,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    modifier = Modifier.testTag("expense_item_pane_total_amount_value"),
                    text = buildAnnotatedString {
                        append(state.totalAmount)
                        withStyle(SpanStyle(fontSize = MaterialTheme.typography.bodyLarge.fontSize)) {
                            append(" ${state.primaryCurrencyCode}")
                        }
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        item(key = "split", contentType = "heading") {
            Text(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
                    .testTag("expense_item_pane_split_section")
                    .semantics { heading() },
                text = stringResource(Res.string.expenses_details_split),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        itemsIndexed(state.split, key = { _, person -> person.personId }, contentType = { _, _ -> "person" }) { index, person ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large.copy(
                    topStart = if (index == 0) MaterialTheme.shapes.large.topStart else CornerSize(0.dp),
                    topEnd = if (index == 0) MaterialTheme.shapes.large.topEnd else CornerSize(0.dp),
                    bottomStart = if (index == state.split.lastIndex) MaterialTheme.shapes.large.bottomStart else CornerSize(0.dp),
                    bottomEnd = if (index == state.split.lastIndex) MaterialTheme.shapes.large.bottomEnd else CornerSize(0.dp),
                ),
            ) {
                Column {
                    if (index != 0) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    DetailSplitRow(person = person, currencyCode = state.originalCurrencyCode)
                }
            }
        }
        item(key = "details", contentType = "details") {
            OperationDetails(
                modifier = Modifier.padding(top = 24.dp),
                state = state,
                expanded = areOperationDetailsExpanded,
                onClick = { areOperationDetailsExpanded = !areOperationDetailsExpanded },
            )
        }
        item(key = "revert", contentType = "action") {
            val minHeight = ButtonDefaults.MediumContainerHeight
            TextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .heightIn(min = minHeight)
                    .testTag("expense_item_revert_action"),
                onClick = onRevertExpenseClick,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                contentPadding = ButtonDefaults.contentPaddingFor(minHeight),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(minHeight)),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(minHeight)))
                Text(
                    text = stringResource(Res.string.expenses_revert_operation),
                    style = ButtonDefaults.textStyleFor(minHeight),
                )
            }
        }
    }
}

@Composable
private fun DetailSplitRow(
    person: PersonSplitUiModel,
    currencyCode: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (LocalDensity.current.fontScale < 1.5f) {
            Surface(
                modifier = Modifier.size(40.dp).clearAndSetSemantics {},
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = person.personInitial, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.padding(end = 12.dp).testTag("expense_item_pane_split_person_${person.personName}"),
                text = person.personName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                modifier = Modifier.testTag("expense_item_pane_split_amount_${person.personName}"),
                text = buildAnnotatedString {
                    append(person.amount)
                    withStyle(
                        SpanStyle(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    ) {
                        append(" $currencyCode")
                    }
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun OperationDetails(
    state: ExpenseItemPaneUiModel,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motionScheme.fastSpatialSpec(),
    )
    val expansionDescription = stringResource(
        if (expanded) Res.string.expenses_details_expanded else Res.string.expenses_details_collapsed
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("expense_item_pane_operation_details_toggle")
                    .semantics { stateDescription = expansionDescription },
                onClick = onClick,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier.minimumInteractiveComponentSize().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f).semantics { heading() },
                        text = stringResource(Res.string.expenses_operation_details),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Icon(
                        modifier = Modifier.rotate(iconRotation),
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motionScheme.defaultSpatialSpec(), expandFrom = Alignment.Top) +
                    fadeIn(motionScheme.fastEffectsSpec()),
                exit = shrinkVertically(motionScheme.defaultSpatialSpec(), shrinkTowards = Alignment.Top) +
                    fadeOut(motionScheme.fastEffectsSpec()),
            ) {
                Column(
                    modifier = Modifier
                        .testTag("expense_item_pane_operation_details")
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DetailRow(
                        label = stringResource(Res.string.expenses_paid_by),
                        value = state.personName,
                        valueTestTag = "expense_item_pane_paid_by_value",
                    )
                    DetailRow(
                        label = stringResource(Res.string.expenses_date),
                        value = state.timestamp,
                        valueTestTag = "expense_item_pane_date_value",
                    )
                    state.exchangeRate?.let { exchangeRate ->
                        DetailRow(
                            label = stringResource(Res.string.expenses_exchange_rate),
                            value = stringResource(
                                Res.string.expenses_exchange_rate_value,
                                state.originalCurrencyCode,
                                exchangeRate,
                                state.primaryCurrencyCode,
                            ),
                            valueTestTag = "expense_item_pane_exchange_rate_value",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueTestTag: String,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            modifier = Modifier.padding(end = 16.dp),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            modifier = Modifier.testTag(valueTestTag),
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@ScreenPreviews
@Composable
private fun ExpenseItemPanePreview() {
    CommonExTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ExpenseItemPane(state = SimpleScreenState.Success(mockExpenseItem()), onRevertExpenseClick = {})
        }
    }
}

@ComponentPreviews
@Composable
private fun OperationDetailsPreview() {
    CommonExTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            OperationDetails(state = mockExpenseItem(), expanded = true, onClick = {})
        }
    }
}

@Preview(name = "Person split row", widthDp = 360)
@Composable
private fun DetailSplitRowPreview() {
    CommonExTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            DetailSplitRow(person = PersonSplitUiModel(1, "Alex", "-20.00"), currencyCode = "USD")
        }
    }
}

@Preview(name = "Repayment", widthDp = 360, heightDp = 800)
@Composable
private fun ExpenseItemPaneRepaymentPreview() {
    val expense = mockExpenseItem()
    CommonExTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ExpenseItemPane(
                state = SimpleScreenState.Success(
                    expense.copy(
                        expense = expense.expense.copy(
                            expenseType = ExpenseType.Replenishment,
                            currency = expense.expense.currency.copy(code = "EUR", name = "Euro"),
                            description = "Repayment to Sam",
                            subjectExpenseSplitWithPersons = listOf(
                                expense.expense.subjectExpenseSplitWithPersons[1].copy(
                                    originalAmount = 20.toBigDecimal(),
                                    exchangedAmount = 20.toBigDecimal(),
                                )
                            ),
                        ),
                        description = "Repayment to Sam",
                        totalAmount = "+20.00",
                        originalCurrencyCode = "EUR",
                        exchangeRate = null,
                        split = persistentListOf(PersonSplitUiModel(2, "Sam", "+20.00")),
                    )
                ),
                onRevertExpenseClick = {},
            )
        }
    }
}

@Preview(name = "Dark", widthDp = 360, heightDp = 800)
@Composable
private fun ExpenseItemPaneDarkPreview() {
    CommonExTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ExpenseItemPane(state = SimpleScreenState.Success(mockExpenseItem()), onRevertExpenseClick = {})
        }
    }
}

@Preview(name = "Loading", widthDp = 360, heightDp = 800)
@Composable
private fun ExpenseItemPaneLoadingPreview() {
    CommonExTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ExpenseItemPane(state = SimpleScreenState.Loading, onRevertExpenseClick = {})
        }
    }
}

@Preview(name = "Error", widthDp = 360, heightDp = 800)
@Composable
private fun ExpenseItemPaneErrorPreview() {
    CommonExTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ExpenseItemPane(state = SimpleScreenState.Error, onRevertExpenseClick = {})
        }
    }
}

private fun mockExpenseItem(): ExpenseItemPaneUiModel {
    val persons = listOf("Alex", "Sam", "Jo").mapIndexed { index, name ->
        Person(id = index + 1L, serverId = null, clientCreateId = "person-$index", name = name)
    }
    val person = persons.first()
    val expense = Expense(
        expenseId = 1,
        serverId = "1",
        clientCreateId = "expense-1",
        currency = Currency(id = 1, serverId = "1", code = "USD", name = "US Dollar", rate = 1.toBigDecimal()),
        expenseType = ExpenseType.Spending,
        person = person,
        subjectExpenseSplitWithPersons = persons.map { splitPerson ->
            ExpenseSplitWithPerson(
                expenseSplitId = splitPerson.id, expenseId = 1, person = splitPerson,
                originalAmount = 20.toBigDecimal(), exchangedAmount = "18.40".toBigDecimal(),
            )
        },
        isCustomRate = false,
        timestamp = Instant.parse("2026-10-19T18:40:00Z"),
        description = "Dinner by the river",
    )
    return ExpenseItemPaneUiModel(
        expense = expense,
        description = expense.description,
        totalAmount = "-55.20",
        primaryCurrencyCode = "EUR",
        personName = person.name,
        timestamp = "19 Oct 2026, 18:40",
        originalCurrencyCode = "USD",
        exchangeRate = "0.92",
        split = persistentListOf(
            PersonSplitUiModel(1, "Alex", "-20.00"),
            PersonSplitUiModel(2, "Sam", "-20.00"),
            PersonSplitUiModel(3, "Jo", "-20.00"),
        ),
    )
}
