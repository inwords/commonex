package com.inwords.expenses.feature.events.ui.add_participants

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.appbar.TopAppBarWithNavIconAndText
import com.inwords.expenses.core.ui.design.button.ButtonWithIconAndText
import com.inwords.expenses.core.ui.design.button.OutlinedButtonWithIconAndText
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.feature.events.ui.common.PersonNameField
import expenses.shared.feature.events.generated.resources.Res
import expenses.shared.feature.events.generated.resources.common_back
import expenses.shared.feature.events.generated.resources.events_add_participant
import expenses.shared.feature.events.generated.resources.events_continue_button
import expenses.shared.feature.events.generated.resources.events_participants_label
import expenses.shared.feature.events.generated.resources.events_participants_title
import expenses.shared.feature.events.generated.resources.events_person_name_placeholder
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AddParticipantsToEventPane(
    modifier: Modifier = Modifier,
    state: AddParticipantsToEventPaneUiModel,
    onParticipantNameChanged: (Int, String) -> Unit,
    onAddParticipantClicked: () -> Unit,
    onConfirmClicked: () -> Unit,
    onNavIconClicked: () -> Unit,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBarWithNavIconAndText(
                onNavIconClicked = onNavIconClicked,
                title = stringResource(Res.string.events_participants_title),
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(Res.string.common_back),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .consumeWindowInsets(paddingValues)
                .padding(horizontal = 8.dp)
                .padding(paddingValues)
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                text = stringResource(Res.string.events_participants_label),
                style = MaterialTheme.typography.headlineMedium
            )
            Column(
                modifier = Modifier
                    .animateContentSize()
            ) {
                val focusRequester = remember { FocusRequester() }
                val requestFocus = remember { mutableStateOf(false) }

                state.participants.forEachIndexed { i, name ->
                    PersonNameField(
                        modifier = if (i == state.participants.size - 1) {
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .focusRequester(focusRequester)
                                .onGloballyPositioned {
                                    if (requestFocus.value) {
                                        focusRequester.requestFocus()
                                        requestFocus.value = false
                                    }
                                }
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        },
                        participantName = name,
                        labelText = stringResource(Res.string.events_person_name_placeholder),
                        enabled = true,
                        imeAction = ImeAction.Next,
                        onImeAction = {
                            onAddParticipantClicked.invoke()
                            requestFocus.value = true
                        },
                        onParticipantNameChanged = { onParticipantNameChanged(i, it) }
                    )
                }
            }

            OutlinedButtonWithIconAndText(
                modifier = Modifier
                    .fillMaxWidth(),
                onClick = onAddParticipantClicked,
                text = stringResource(Res.string.events_add_participant),
                imageVector = Icons.Outlined.Add,
            )

            Spacer(modifier = Modifier.weight(1f))

            ButtonWithIconAndText(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(vertical = 16.dp),
                onClick = onConfirmClicked,
                enabled = state.isConfirmEnabled,
                text = stringResource(Res.string.events_continue_button),
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                minHeight = ButtonDefaults.MediumContainerHeight,
            )
        }
    }
}

@Preview
@Composable
private fun AddParticipantsToEventPanePreview() {
    CommonExTheme {
        AddParticipantsToEventPane(
            state = AddParticipantsToEventPaneUiModel(
                participants = listOf("Анжела", "Саша")
            ),
            onParticipantNameChanged = { _, _ -> },
            onAddParticipantClicked = {},
            onConfirmClicked = {},
            onNavIconClicked = {},
        )
    }
}
