package com.inwords.expenses.feature.events.ui.add_participants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.appbar.TopAppBarWithNavIconAndText
import com.inwords.expenses.core.ui.design.button.ButtonWithIconAndText
import com.inwords.expenses.core.ui.design.preview.ScreenPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.feature.events.ui.common.PersonNameField
import expenses.shared.feature.events.generated.resources.Res
import expenses.shared.feature.events.generated.resources.common_back
import expenses.shared.feature.events.generated.resources.events_add_people_another
import expenses.shared.feature.events.generated.resources.events_add_people_guidance
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
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ButtonWithIconAndText(
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxWidth()
                            .testTag("events_add_people_confirm"),
                        onClick = onConfirmClicked,
                        enabled = state.isConfirmEnabled,
                        text = stringResource(Res.string.events_continue_button),
                        imageVector = Icons.Outlined.Check,
                    )
                }
            }
        },
    ) { paddingValues ->
        val focusRequester = remember { FocusRequester() }
        val listState = rememberLazyListState()
        var pendingFocusIndex by remember { mutableStateOf<Int?>(null) }
        val appendPerson = {
            pendingFocusIndex = state.participants.size
            onAddParticipantClicked()
        }
        LaunchedEffect(state.participants.size, pendingFocusIndex) {
            val index = pendingFocusIndex
            if (index != null && index <= state.participants.lastIndex) {
                listState.scrollToItem(index + 2)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(paddingValues)
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .testTag("events_add_people_form"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                item(key = "guidance", contentType = "guidance") {
                    Text(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                        text = stringResource(Res.string.events_add_people_guidance),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item(key = "heading", contentType = "heading") {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = MaterialTheme.shapes.extraLarge.copy(
                                    bottomStart = CornerSize(0.dp),
                                    bottomEnd = CornerSize(0.dp),
                                ),
                            )
                            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)
                            .semantics { heading() },
                        text = stringResource(Res.string.events_participants_label),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                itemsIndexed(
                    items = state.participants,
                    key = { index, _ -> "participant_$index" },
                    contentType = { _, _ -> "field" },
                ) { index, name ->
                    PersonNameField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .testTag("events_add_person_name_$index")
                            .then(
                                if (index == state.participants.lastIndex) {
                                    Modifier
                                        .focusRequester(focusRequester)
                                        .onGloballyPositioned {
                                            if (pendingFocusIndex == index) {
                                                focusRequester.requestFocus()
                                                pendingFocusIndex = null
                                            }
                                        }
                                } else {
                                    Modifier
                                }
                            ),
                        participantName = name,
                        labelText = stringResource(Res.string.events_person_name_placeholder),
                        enabled = true,
                        imeAction = ImeAction.Next,
                        onImeAction = appendPerson,
                        onParticipantNameChanged = { onParticipantNameChanged(index, it) },
                    )
                }
                item(key = "append", contentType = "action") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = MaterialTheme.shapes.extraLarge.copy(
                                    topStart = CornerSize(0.dp),
                                    topEnd = CornerSize(0.dp),
                                ),
                            )
                            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
                    ) {
                        TextButton(
                            modifier = Modifier.testTag("events_add_person"),
                            onClick = appendPerson,
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                            Text(
                                text = stringResource(Res.string.events_add_people_another),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@ScreenPreviews
@Preview(name = "Expanded", widthDp = 840, heightDp = 600)
@Composable
private fun AddParticipantsToEventPanePreview() {
    CommonExTheme {
        AddParticipantsToEventPane(
            state = AddParticipantsToEventPaneUiModel(
                participants = listOf("Alex", "Jo", "")
            ),
            onParticipantNameChanged = { _, _ -> },
            onAddParticipantClicked = {},
            onConfirmClicked = {},
            onNavIconClicked = {},
        )
    }
}
