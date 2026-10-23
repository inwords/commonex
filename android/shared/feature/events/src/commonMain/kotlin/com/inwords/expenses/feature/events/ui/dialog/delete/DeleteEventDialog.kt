package com.inwords.expenses.feature.events.ui.dialog.delete

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.inwords.expenses.core.ui.design.preview.ScreenPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import expenses.shared.feature.events.generated.resources.Res
import expenses.shared.feature.events.generated.resources.events_delete_event
import expenses.shared.feature.events.generated.resources.events_delete_event_body
import expenses.shared.feature.events.generated.resources.events_delete_event_title
import expenses.shared.feature.events.generated.resources.events_keep_event
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DeleteEventDialog(
    state: DeleteEventDialogUiModel,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    Res.string.events_delete_event_title,
                    state.eventName,
                )
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.events_delete_event_body),
            )
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("delete_event_dialog_delete_button"),
                onClick = onConfirmDelete,
            ) {
                Text(
                    text = stringResource(Res.string.events_delete_event),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag("delete_event_dialog_keep_button"),
                onClick = onDismiss,
            ) {
                Text(text = stringResource(Res.string.events_keep_event))
            }
        }
    )
}

@ScreenPreviews
@Composable
private fun DeleteEventDialogPreview() {
    CommonExTheme {
        DeleteEventDialog(
            state = DeleteEventDialogUiModel(eventName = "Weekend in Lisbon"),
            onConfirmDelete = {},
            onDismiss = {},
        )
    }
}
