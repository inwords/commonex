package com.inwords.expenses.feature.expenses.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.feature.expenses.ui.common.DebtShortUiModel
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_all_balances
import expenses.shared.feature.expenses.generated.resources.expenses_repay_person
import expenses.shared.feature.expenses.generated.resources.expenses_you_owe
import expenses.shared.feature.expenses.generated.resources.expenses_you_owe_nothing
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DebtsBlock(
    onDebtsDetailsClick: () -> Unit,
    state: ExpensesPaneUiModel.Expenses,
    onReplenishmentClick: (DebtShortUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag(ExpensesPaneTags.PERSONAL_DEBTS),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val hasDebt = state.debts.isNotEmpty()
                    Text(
                        text = stringResource(if (!hasDebt) Res.string.expenses_you_owe_nothing else Res.string.expenses_you_owe),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (hasDebt) {
                        Text(text = state.totalDebt, style = MaterialTheme.typography.headlineLarge)
                    }
                }
                TextButton(
                    onClick = onDebtsDetailsClick,
                    contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 0.dp, bottom = 8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                ) {
                    Text(text = stringResource(Res.string.expenses_all_balances))
                    Icon(
                        modifier = Modifier.padding(start = 8.dp),
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                    )
                }
            }
            state.debts.forEach { debt ->
                PersonalDebtRow(debt = debt, onClick = { onReplenishmentClick(debt) })
            }
        }
    }
}

@Composable
private fun PersonalDebtRow(
    debt: DebtShortUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                onClickLabel = stringResource(
                    resource = Res.string.expenses_repay_person,
                    debt.amount, debt.currencyCode, debt.personName
                ),
                onClick = onClick,
            )
            .minimumInteractiveComponentSize()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = debt.personInitial, style = MaterialTheme.typography.titleMedium)
            }
        }
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.padding(end = 12.dp),
                text = debt.personName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = "${debt.amount} ${debt.currencyCode}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Preview
@Composable
private fun DebtsBlockPreview() {
    CommonExTheme {
        DebtsBlock(state = mockExpensesPaneUiModel(withDebts = true), onDebtsDetailsClick = {}, onReplenishmentClick = {})
    }
}

@Preview
@Composable
private fun DebtsBlockNoDebtsPreview() {
    CommonExTheme {
        DebtsBlock(state = mockExpensesPaneUiModel(withDebts = false), onDebtsDetailsClick = {}, onReplenishmentClick = {})
    }
}

@Preview
@Composable
private fun PersonalDebtRowPreview() {
    CommonExTheme {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            PersonalDebtRow(
                debt = DebtShortUiModel(personId = 1, personName = "Sam", currencyCode = "EUR", currencyName = "Euro", amount = "14"),
                onClick = {},
            )
        }
    }
}
