package com.inwords.expenses.feature.expenses.ui.list.bottom_sheet.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.loading.DefaultProgressIndicator
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.common_error
import expenses.shared.feature.expenses.generated.resources.expenses_date
import expenses.shared.feature.expenses.generated.resources.expenses_description
import expenses.shared.feature.expenses.generated.resources.expenses_exchange_rate
import expenses.shared.feature.expenses.generated.resources.expenses_exchange_rate_value
import expenses.shared.feature.expenses.generated.resources.expenses_expense_details
import expenses.shared.feature.expenses.generated.resources.expenses_no_expenses
import expenses.shared.feature.expenses.generated.resources.expenses_original_currency
import expenses.shared.feature.expenses.generated.resources.expenses_paid_by
import expenses.shared.feature.expenses.generated.resources.expenses_revert_operation
import expenses.shared.feature.expenses.generated.resources.expenses_split
import expenses.shared.feature.expenses.generated.resources.expenses_total_amount
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@Composable
internal fun ExpenseItemPane(
    state: SimpleScreenState<ExpenseItemPaneUiModel>,
    onRevertExpenseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is SimpleScreenState.Success -> ExpenseItemPaneSuccess(
            state = state.data,
            onRevertExpenseClick = onRevertExpenseClick,
            modifier = modifier,
        )

        is SimpleScreenState.Loading -> Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp),
            contentAlignment = Alignment.Center,
        ) {
            DefaultProgressIndicator()
        }

        is SimpleScreenState.Error -> {
            Text(text = stringResource(Res.string.common_error))
        }

        SimpleScreenState.Empty -> {
            Text(text = stringResource(Res.string.expenses_no_expenses))
        }
    }
}

@Composable
private fun ExpenseItemPaneSuccess(
    state: ExpenseItemPaneUiModel,
    onRevertExpenseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("expense_item_pane_root")
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("expense_item_pane_title")
                .padding(horizontal = 16.dp, vertical = 8.dp),
            text = stringResource(Res.string.expenses_expense_details),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))

        DetailRow(
            label = stringResource(Res.string.expenses_description),
            value = state.description,
            modifier = Modifier.padding(top = 8.dp),
            valueTestTag = "expense_item_pane_description_value",
        )
        DetailRow(
            label = stringResource(Res.string.expenses_total_amount),
            value = "${state.totalAmount} ${state.primaryCurrencyCode}",
            modifier = Modifier.padding(top = 12.dp),
            isValueEmphasized = true,
            valueTestTag = "expense_item_pane_total_amount_value",
        )
        DetailRow(
            label = stringResource(Res.string.expenses_paid_by),
            value = state.personName,
            modifier = Modifier.padding(top = 12.dp),
            valueTestTag = "expense_item_pane_paid_by_value",
        )
        DetailRow(
            label = stringResource(Res.string.expenses_date),
            value = state.timestamp,
            modifier = Modifier.padding(top = 12.dp),
            valueTestTag = "expense_item_pane_date_value",
        )
        DetailRow(
            label = stringResource(Res.string.expenses_original_currency),
            value = "${state.originalCurrencyCode} (${state.originalCurrencyName})",
            modifier = Modifier.padding(top = 12.dp),
            valueTestTag = "expense_item_pane_original_currency_value",
        )
        state.exchangeRate?.let { exchangeRate ->
            DetailRow(
                label = stringResource(Res.string.expenses_exchange_rate),
                modifier = Modifier.padding(top = 16.dp),
                value = stringResource(
                    Res.string.expenses_exchange_rate_value,
                    state.originalCurrencyCode,
                    exchangeRate,
                    state.primaryCurrencyCode,
                ),
                valueTestTag = "expense_item_pane_exchange_rate_value",
            )
        }

        DetailSplitSection(
            modifier = Modifier.padding(top = 8.dp),
            state = state
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("expense_item_revert_action")
                .padding(horizontal = 16.dp, vertical = 12.dp),
            onClick = onRevertExpenseClick,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.expenses_revert_operation),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isValueEmphasized: Boolean = false,
    valueTestTag: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            modifier = Modifier.weight(0.44f),
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            modifier = Modifier
                .weight(0.56f)
                .then(
                    if (valueTestTag == null) {
                        Modifier
                    } else {
                        Modifier.testTag(valueTestTag)
                    }
                ),
            text = value,
            textAlign = TextAlign.End,
            style = if (isValueEmphasized) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DetailSplitSection(
    state: ExpenseItemPaneUiModel,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("expense_item_pane_split_section")
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(Res.string.expenses_split),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.split.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (index == 0) 8.dp else 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.testTag(splitPersonTag(item.personName)),
                        text = item.personName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${item.amount} ${state.originalCurrencyCode}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun splitPersonTag(personName: String): String {
    return "expense_item_pane_split_person_$personName"
}

@Preview
@Composable
private fun ExpenseItemPanePreview() {
    CommonExTheme {
        Surface {
            ExpenseItemPane(
                state = SimpleScreenState.Success(
                    ExpenseItemPaneUiModel(
                        expense = mockExpense(),
                        description = "Булка с маслом из хорошей булочной, что тут ещё сказать",
                        totalAmount = "-300",
                        primaryCurrencyCode = "EUR",
                        personName = "Василий",
                        timestamp = "21 Feb 2026, 14:30",
                        originalCurrencyCode = "USD",
                        originalCurrencyName = "US Dollar",
                        exchangeRate = "0.92",
                        split = persistentListOf(
                            ExpenseItemPaneUiModel.PersonSplitUiModel(
                                personName = "Василий",
                                amount = "-150"
                            ),
                            ExpenseItemPaneUiModel.PersonSplitUiModel(
                                personName = "Максим",
                                amount = "-150"
                            ),
                        ),
                    )
                ),
                onRevertExpenseClick = {},
            )
        }
    }
}

private fun mockExpense(): Expense {
    val person = Person(
        id = 1,
        serverId = "1",
        name = "Василий",
    )
    return Expense(
        expenseId = 1,
        serverId = "1",
        currency = Currency(
            id = 1,
            serverId = "1",
            code = "USD",
            name = "US Dollar",
            rate = 1.toBigDecimal(),
        ),
        expenseType = ExpenseType.Spending,
        person = person,
        subjectExpenseSplitWithPersons = listOf(
            ExpenseSplitWithPerson(
                expenseSplitId = 1,
                expenseId = 1,
                person = person,
                originalAmount = 150.toBigDecimal(),
                exchangedAmount = 150.toBigDecimal(),
            )
        ),
        timestamp = Instant.parseOrNull("2026-03-02T18:43:00Z")!!,
        description = "Preview expense",
    )
}
