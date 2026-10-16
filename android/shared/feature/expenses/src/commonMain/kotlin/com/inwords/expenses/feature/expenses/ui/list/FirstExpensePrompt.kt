package com.inwords.expenses.feature.expenses.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.button.ButtonWithIconAndText
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_first_action
import expenses.shared.feature.expenses.generated.resources.expenses_first_body
import expenses.shared.feature.expenses.generated.resources.expenses_first_created
import expenses.shared.feature.expenses.generated.resources.expenses_first_details
import expenses.shared.feature.expenses.generated.resources.expenses_first_expense_hint
import expenses.shared.feature.expenses.generated.resources.expenses_first_identity
import expenses.shared.feature.expenses.generated.resources.expenses_first_invite
import expenses.shared.feature.expenses.generated.resources.expenses_first_invite_hint
import expenses.shared.feature.expenses.generated.resources.expenses_first_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FirstExpensePrompt(
    eventName: String,
    currentPersonName: String,
    personCount: Int,
    currencyCode: String,
    onAddExpenseClick: () -> Unit,
    onInviteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .testTag(ExpensesPaneTags.EMPTY_STATE)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = eventName,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = pluralStringResource(Res.plurals.expenses_first_details, personCount, personCount, currencyCode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(Res.string.expenses_first_identity, currentPersonName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("expenses_current_person"),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.expenses_first_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(Res.string.expenses_first_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                ReadyStep(
                    title = stringResource(Res.string.expenses_first_created),
                    description = pluralStringResource(Res.plurals.expenses_first_details, personCount, personCount, currencyCode),
                    step = 1,
                )
                ReadyStep(
                    title = stringResource(Res.string.expenses_first_action),
                    description = stringResource(Res.string.expenses_first_expense_hint),
                    step = 2,
                )
                ReadyStep(
                    title = stringResource(Res.string.expenses_first_invite),
                    description = stringResource(Res.string.expenses_first_invite_hint),
                    step = 3,
                )
            }
        }
        Column(
            modifier = Modifier.padding(top = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ButtonWithIconAndText(
                onClick = onAddExpenseClick,
                text = stringResource(Res.string.expenses_first_action),
                imageVector = Icons.Outlined.Add,
                minHeight = ButtonDefaults.MediumContainerHeight,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ExpensesPaneTags.ADD_EXPENSE_BUTTON),
            )
            TextButton(
                onClick = onInviteClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ExpensesPaneTags.INVITE_BUTTON),
            ) {
                Text(stringResource(Res.string.expenses_first_invite))
            }
        }
    }
}

@Composable
private fun ReadyStep(
    title: String,
    description: String,
    step: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 32.dp, minHeight = 32.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (step == 1) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        text = step.toString(),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun FirstExpensePromptPreview() {
    CommonExTheme {
        FirstExpensePrompt(
            eventName = "Weekend in Lisbon",
            currentPersonName = "Alex",
            personCount = 3,
            currencyCode = "EUR",
            onAddExpenseClick = {},
            onInviteClick = {},
        )
    }
}
