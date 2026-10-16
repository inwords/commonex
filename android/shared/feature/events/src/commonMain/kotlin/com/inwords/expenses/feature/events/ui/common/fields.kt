package com.inwords.expenses.feature.events.ui.common

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import expenses.shared.feature.events.generated.resources.Res
import expenses.shared.feature.events.generated.resources.events_access_code_placeholder
import expenses.shared.feature.events.generated.resources.events_id_placeholder
import expenses.shared.feature.events.generated.resources.events_name_placeholder
import expenses.shared.feature.events.generated.resources.events_person_name_placeholder
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EventNameField(
    eventName: String,
    onEventNameChanged: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        modifier = modifier,
        value = eventName,
        onValueChange = onEventNameChanged,
        label = { Text(stringResource(Res.string.events_name_placeholder)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium,
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done
        )
    )
}

@Composable
internal fun EventIdField(
    eventId: String,
    onEventIdChanged: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    OutlinedTextField(
        modifier = modifier,
        value = eventId,
        onValueChange = onEventIdChanged,
        label = { Text(stringResource(Res.string.events_id_placeholder)) },
        enabled = enabled,
        isError = isError,
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium,
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
    isError: Boolean = false
) {
    OutlinedTextField(
        modifier = modifier,
        value = eventAccessCode,
        onValueChange = onEventAccessCodeChanged,
        label = { Text(stringResource(Res.string.events_access_code_placeholder)) },
        enabled = enabled,
        isError = isError,
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium,
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
    imeAction: ImeAction,
    onParticipantNameChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    onImeAction: () -> Unit = {},
) {
    OutlinedTextField(
        modifier = modifier,
        value = participantName,
        onValueChange = onParticipantNameChanged,
        label = { Text(stringResource(Res.string.events_person_name_placeholder)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium,
        keyboardActions = KeyboardActions(onAny = { onImeAction.invoke() }),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            keyboardType = KeyboardType.Text,
            imeAction = imeAction
        )
    )
}
