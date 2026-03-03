package com.inwords.expenses.feature.expenses.ui.list.dialog.revert

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_revert_cancel
import expenses.shared.feature.expenses.generated.resources.expenses_revert_confirm_body
import expenses.shared.feature.expenses.generated.resources.expenses_revert_confirm_title
import expenses.shared.feature.expenses.generated.resources.expenses_revert_operation
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExpenseRevertDialog(
    expenseDescription: String,
    onConfirmRevert: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    Res.string.expenses_revert_confirm_title,
                    expenseDescription,
                )
            )
        },
        text = {
            Text(text = stringResource(Res.string.expenses_revert_confirm_body))
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("expense_revert_dialog_confirm_button"),
                onClick = onConfirmRevert,
            ) {
                Text(
                    text = stringResource(Res.string.expenses_revert_operation),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag("expense_revert_dialog_dismiss_button"),
                onClick = onDismiss,
            ) {
                Text(text = stringResource(Res.string.expenses_revert_cancel))
            }
        }
    )
}
