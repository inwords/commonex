package com.inwords.expenses.feature.events.ui.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.appbar.TopAppBarWithNavIconAndText
import com.inwords.expenses.core.ui.design.button.ButtonWithIconAndText
import com.inwords.expenses.core.ui.design.preview.ScreenPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.feature.events.ui.common.EventAccessCodeField
import com.inwords.expenses.feature.events.ui.common.EventIdField
import com.inwords.expenses.feature.events.ui.join.JoinEventPaneUiModel.EventJoiningState
import expenses.shared.feature.events.generated.resources.Res
import expenses.shared.feature.events.generated.resources.common_back
import expenses.shared.feature.events.generated.resources.events_join_description
import expenses.shared.feature.events.generated.resources.events_join_event_button
import expenses.shared.feature.events.generated.resources.events_join_link_action
import expenses.shared.feature.events.generated.resources.events_join_manual_heading
import expenses.shared.feature.events.generated.resources.events_join_title
import expenses.shared.feature.events.generated.resources.events_joining_event
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun JoinEventPane(
    modifier: Modifier = Modifier,
    state: JoinEventPaneUiModel,
    onEventIdChanged: (String) -> Unit,
    onEventAccessCodeChanged: (String) -> Unit,
    onConfirmClicked: () -> Unit,
    onUseInvitationClicked: () -> Unit,
    onNavIconClicked: () -> Unit,
) {
    val joining = state.joiningState == EventJoiningState.Joining
    val joiningError = state.joiningState as? EventJoiningState.Error
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBarWithNavIconAndText(
                onNavIconClicked = onNavIconClicked,
                title = stringResource(Res.string.events_join_title),
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(Res.string.common_back),
            )
        },
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(paddingValues)
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.events_join_manual_heading),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.events_join_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
                EventIdField(
                    modifier = Modifier.fillMaxWidth().testTag("events_join_id"),
                    eventId = state.eventId,
                    onEventIdChanged = onEventIdChanged,
                    enabled = !joining,
                )
                EventAccessCodeField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .testTag("events_join_access_code"),
                    eventAccessCode = state.eventAccessCode,
                    onDone = onConfirmClicked,
                    onEventAccessCodeChanged = onEventAccessCodeChanged,
                    enabled = !joining,
                )
                if (joiningError != null) {
                    Text(
                        text = joiningError.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Spacer(Modifier.weight(1f).heightIn(min = 32.dp))
                ButtonWithIconAndText(
                    modifier = Modifier.fillMaxWidth().testTag("events_join_confirm"),
                    onClick = onConfirmClicked,
                    enabled = !joining && state.eventId.isNotBlank() && state.eventAccessCode.isNotBlank(),
                    text = stringResource(Res.string.events_join_event_button),
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    minHeight = ButtonDefaults.MediumContainerHeight,
                )
                if (joining) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LoadingIndicator()
                        Text(
                            text = stringResource(Res.string.events_joining_event),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                TextButton(
                    onClick = onUseInvitationClicked,
                    enabled = !joining,
                    modifier = Modifier.fillMaxWidth().testTag("events_join_use_invitation"),
                ) {
                    Text(stringResource(Res.string.events_join_link_action))
                }
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun JoinEventPanePreview() {
    JoinEventPaneSample(EventJoiningState.None)
}

@Preview(name = "Joining", widthDp = 360, heightDp = 800)
@Composable
private fun JoinEventPaneJoiningPreview() {
    JoinEventPaneSample(EventJoiningState.Joining)
}

@Preview(name = "Join failed", widthDp = 360, heightDp = 800)
@Composable
private fun JoinEventPaneErrorPreview() {
    JoinEventPaneSample(EventJoiningState.Error("Could not join the group. Check the group ID and PIN, then try again."))
}

@Composable
private fun JoinEventPaneSample(joiningState: EventJoiningState) {
    CommonExTheme {
        JoinEventPane(
            state = JoinEventPaneUiModel(
                eventId = "7K4M2D8P",
                eventAccessCode = "1234",
                joiningState = joiningState,
            ),
            onEventIdChanged = {},
            onEventAccessCodeChanged = {},
            onConfirmClicked = {},
            onUseInvitationClicked = {},
            onNavIconClicked = {},
        )
    }
}
