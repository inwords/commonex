package com.inwords.expenses.feature.expenses.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.preview.ComponentPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.Expenses.ExpenseUiModel
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_from_person
import expenses.shared.feature.expenses.generated.resources.expenses_from_you
import expenses.shared.feature.expenses.generated.resources.expenses_no_description
import expenses.shared.feature.expenses.generated.resources.expenses_original_amount
import expenses.shared.feature.expenses.generated.resources.expenses_paid_by_person
import expenses.shared.feature.expenses.generated.resources.expenses_paid_by_you
import expenses.shared.feature.expenses.generated.resources.expenses_repayment
import expenses.shared.feature.expenses.generated.resources.expenses_your_part
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExpenseItem(
    expense: ExpenseUiModel,
    onExpenseClick: (ExpenseUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = { onExpenseClick(expense) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    modifier = Modifier.alignByBaseline().padding(end = 12.dp),
                    text = expense.description.ifEmpty {
                        stringResource(
                            when (expense.expenseType) {
                                ExpenseType.Spending -> Res.string.expenses_no_description
                                ExpenseType.Replenishment -> Res.string.expenses_repayment
                            }
                        )
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(
                    modifier = Modifier.alignByBaseline(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                        text = expense.totalAmount,
                        style = MaterialTheme.typography.titleLarge,
                        color = when (expense.expenseType) {
                            ExpenseType.Spending -> MaterialTheme.colorScheme.onSurface
                            ExpenseType.Replenishment -> MaterialTheme.colorScheme.primary
                        },
                    )
                    Text(
                        modifier = Modifier.alignByBaseline(),
                        text = expense.currencyCode,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    modifier = Modifier.padding(end = 12.dp),
                    text = when (expense.expenseType) {
                        ExpenseType.Spending -> if (expense.isPaidByCurrentPerson) {
                            stringResource(Res.string.expenses_paid_by_you)
                        } else {
                            stringResource(resource = Res.string.expenses_paid_by_person, expense.personName)
                        }

                        ExpenseType.Replenishment -> if (expense.isPaidByCurrentPerson) {
                            stringResource(Res.string.expenses_from_you)
                        } else {
                            stringResource(resource = Res.string.expenses_from_person, expense.personName)
                        }
                    } + " · " + expense.timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                expense.currentPersonPartAmount?.let { amount ->
                    Text(
                        text = stringResource(resource = Res.string.expenses_your_part, "$amount ${expense.currencyCode}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            expense.originalAmount?.let { amount ->
                Text(
                    text = stringResource(resource = Res.string.expenses_original_amount, amount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@ComponentPreviews
@Composable
private fun ExpenseItemPreview() {
    CommonExTheme {
        ExpenseItem(
            expense = ExpenseUiModel(
                expenseId = 1, currencyCode = "EUR", originalAmount = "66 USD", expenseType = ExpenseType.Spending,
                personName = "Jo", isPaidByCurrentPerson = false, totalAmount = "-60", timeText = "14:25",
                description = "Museum tickets", currentPersonPartAmount = "-20",
            ),
            onExpenseClick = {},
        )
    }
}

@Preview(name = "Repayment", widthDp = 360)
@Composable
private fun ExpenseItemRepaymentPreview() {
    CommonExTheme {
        ExpenseItem(
            expense = ExpenseUiModel(
                expenseId = 2, currencyCode = "EUR", originalAmount = null, expenseType = ExpenseType.Replenishment,
                personName = "Alex", isPaidByCurrentPerson = true, totalAmount = "+20.00", timeText = "10:20",
                description = "Repayment to Sam", currentPersonPartAmount = null,
            ),
            onExpenseClick = {},
        )
    }
}
