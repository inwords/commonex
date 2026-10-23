package com.inwords.expenses.feature.events.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.preview.ComponentPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import expenses.shared.feature.events.generated.resources.Res
import expenses.shared.feature.events.generated.resources.events_access_code_placeholder
import expenses.shared.feature.events.generated.resources.events_id_placeholder
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EventIdField(
    eventId: String,
    onEventIdChanged: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        modifier = modifier,
        value = eventId,
        onValueChange = onEventIdChanged,
        label = { Text(stringResource(Res.string.events_id_placeholder)) },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next
        )
    )
}

@Composable
internal fun EventAccessCodeField(
    eventAccessCode: String,
    onEventAccessCodeChanged: (String) -> Unit,
    onDone: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        modifier = modifier,
        value = eventAccessCode,
        onValueChange = onEventAccessCodeChanged,
        label = { Text(stringResource(Res.string.events_access_code_placeholder)) },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardActions = KeyboardActions(onDone = { onDone.invoke() }),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done
        )
    )
}

@Composable
internal fun PersonNameField(
    participantName: String,
    labelText: String,
    imeAction: ImeAction,
    onParticipantNameChanged: (String) -> Unit,
    onImeAction: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        modifier = modifier,
        value = participantName,
        onValueChange = onParticipantNameChanged,
        label = { Text(labelText) },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardActions = KeyboardActions(onAny = { onImeAction.invoke() }),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            keyboardType = KeyboardType.Text,
            imeAction = imeAction
        )
    )
}

@ComponentPreviews
@Composable
private fun EventIdFieldPreview() {
    CommonExTheme {
        Surface {
            EventIdField(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                eventId = "7K4M2D8P",
                onEventIdChanged = {},
                enabled = true,
            )
        }
    }
}

@ComponentPreviews
@Composable
private fun EventAccessCodeFieldPreview() {
    CommonExTheme {
        Surface {
            EventAccessCodeField(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                eventAccessCode = "1234",
                onEventAccessCodeChanged = {},
                onDone = {},
                enabled = true,
            )
        }
    }
}

@ComponentPreviews
@Composable
private fun PersonNameFieldPreview() {
    CommonExTheme {
        Surface {
            PersonNameField(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                participantName = "Alex Morgan",
                labelText = "Your name",
                imeAction = ImeAction.Done,
                onParticipantNameChanged = {},
                onImeAction = {},
                enabled = true,
            )
        }
    }
}
