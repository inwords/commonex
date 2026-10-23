package com.inwords.expenses.feature.expenses.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.preview.ComponentPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_add_to
import expenses.shared.feature.expenses.generated.resources.expenses_record_repayment
import org.jetbrains.compose.resources.stringResource

internal data class DebtShortUiModel(
    val personId: Long,
    val personName: String,
    val currencyCode: String,
    val currencyName: String,
    val amount: String,
) {

    val personInitial: String by lazy {
        personName.take(1)
    }
}

@Composable
internal fun DebtReplenishmentButton(
    debt: DebtShortUiModel,
    debtorName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(Res.string.expenses_record_repayment, debt.amount, debt.currencyCode, debtorName, debt.personName),
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = 64.dp)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).width(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                modifier = Modifier.alignByBaseline(),
                text = stringResource(Res.string.expenses_add_to),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                text = debt.personName,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                    text = debt.amount,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    modifier = Modifier.alignByBaseline(),
                    text = debt.currencyCode,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@ComponentPreviews
@Composable
private fun DebtReplenishmentButtonPreview() {
    CommonExTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            DebtReplenishmentButton(
                debt = DebtShortUiModel(
                    personId = 1L,
                    personName = "Alex",
                    currencyCode = "EUR",
                    currencyName = "Euro",
                    amount = "42.50",
                ),
                debtorName = "Sam",
                onClick = {}
            )
        }
    }
}
