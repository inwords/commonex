package com.inwords.expenses.feature.events.ui.join_invitation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.appbar.TopAppBarWithNavIconAndText
import com.inwords.expenses.core.ui.design.button.ButtonWithIconAndText
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import expenses.shared.feature.events.generated.resources.Res
import expenses.shared.feature.events.generated.resources.common_back
import expenses.shared.feature.events.generated.resources.events_join_event_button
import expenses.shared.feature.events.generated.resources.events_join_link_guidance
import expenses.shared.feature.events.generated.resources.events_join_link_heading
import expenses.shared.feature.events.generated.resources.events_join_link_invalid
import expenses.shared.feature.events.generated.resources.events_join_link_label
import expenses.shared.feature.events.generated.resources.events_join_link_placeholder
import expenses.shared.feature.events.generated.resources.events_join_manual_action
import expenses.shared.feature.events.generated.resources.events_join_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun JoinInvitationPane(
    modifier: Modifier = Modifier,
    state: JoinInvitationPaneUiModel,
    onInvitationChanged: (String) -> Unit,
    onConfirmClicked: () -> Unit,
    onEnterCodeClicked: () -> Unit,
    onNavIconClicked: () -> Unit,
) {
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
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.events_join_link_heading),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.events_join_link_guidance),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().testTag("events_join_invitation"),
                    value = state.invitation,
                    onValueChange = onInvitationChanged,
                    label = { Text(stringResource(Res.string.events_join_link_label)) },
                    placeholder = { Text(stringResource(Res.string.events_join_link_placeholder)) },
                    singleLine = true,
                    isError = state.isInvitationInvalid,
                    supportingText = if (state.isInvitationInvalid) {
                        {
                            Text(
                                text = stringResource(Res.string.events_join_link_invalid),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConfirmClicked() }),
                )
                Spacer(Modifier.weight(1f).heightIn(min = 32.dp))
                ButtonWithIconAndText(
                    modifier = Modifier.fillMaxWidth().testTag("events_join_confirm"),
                    onClick = onConfirmClicked,
                    enabled = state.invitation.isNotBlank() && !state.isInvitationInvalid,
                    text = stringResource(Res.string.events_join_event_button),
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    minHeight = ButtonDefaults.MediumContainerHeight,
                )
                TextButton(
                    onClick = onEnterCodeClicked,
                    modifier = Modifier.fillMaxWidth().testTag("events_join_manual"),
                ) {
                    Text(stringResource(Res.string.events_join_manual_action))
                }
            }
        }
    }
}

@Preview
@Composable
private fun JoinInvitationPanePreview() {
    CommonExTheme {
        JoinInvitationPane(
            state = JoinInvitationPaneUiModel(invitation = "", isInvitationInvalid = false),
            onInvitationChanged = {},
            onConfirmClicked = {},
            onEnterCodeClicked = {},
            onNavIconClicked = {},
        )
    }
}
