package com.inwords.expenses.feature.menu.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.legal.LegalBlock
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.core.ui.utils.clipEntryOf
import com.inwords.expenses.feature.menu.ui.EventSharingUiModel.ShareState
import com.inwords.expenses.feature.menu.ui.EventSharingUiModel.ShareState.Companion.canShare
import com.inwords.expenses.feature.menu.ui.EventSharingUiModel.ShareText
import expenses.shared.feature.menu.generated.resources.Res
import expenses.shared.feature.menu.generated.resources.menu_add_participants_action
import expenses.shared.feature.menu.generated.resources.menu_choose_person_action
import expenses.shared.feature.menu.generated.resources.menu_copy_action
import expenses.shared.feature.menu.generated.resources.menu_join_other_event
import expenses.shared.feature.menu.generated.resources.menu_open_events_list
import expenses.shared.feature.menu.generated.resources.menu_share_action
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MenuDialog(
    state: MenuDialogUiModel,
    onJoinEventClicked: () -> Unit,
    onLeaveEventClicked: () -> Unit,
    onChoosePersonClicked: () -> Unit,
    onAddParticipantsClicked: () -> Unit,
    onShareClicked: () -> Unit,
    onCopyClicked: () -> Unit,
    onTextCopied: () -> Unit,
    onPrivacyPolicyClicked: () -> Unit,
    onTermsOfUseClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eventSharing = state.eventSharing
    val clipboard = LocalClipboard.current
    (eventSharing.shareState as? ShareState.PendingClipboardCopy)?.let { pendingCopy ->
        LaunchedEffect(pendingCopy.shareText) {
            clipboard.setClipEntry(clipEntryOf(eventSharing.eventName, pendingCopy.shareText.fullText))
            onTextCopied()
        }
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .clickable(
                    enabled = eventSharing.shareState.canShare,
                    onClick = onShareClicked
                )
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .align(Alignment.CenterVertically),
                imageVector = Icons.Outlined.Share,
                contentDescription = null
            )
            Text(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .weight(1f),
                text = stringResource(Res.string.menu_share_action),
                style = MaterialTheme.typography.bodyLarge
            )

            val isLoading = eventSharing.shareState is ShareState.Loading
            Box(
                modifier = Modifier.align(Alignment.CenterVertically),
                contentAlignment = Alignment.Center
            ) {
                // Always render Text to preserve intrinsic size for any font scale
                Text(
                    modifier = Modifier
                        .alpha(if (isLoading) 0f else 1f)
                        .then(if (isLoading) Modifier.clearAndSetSemantics {} else Modifier)
                        .border(
                            border = AssistChipDefaults.assistChipBorder(enabled = eventSharing.shareState.canShare),
                            shape = MaterialTheme.shapes.small
                        )
                        .clickable(
                            enabled = eventSharing.shareState.canShare,
                            onClick = onCopyClicked
                        )
                        .padding(8.dp),
                    text = stringResource(Res.string.menu_copy_action),
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (isLoading) {
                    LoadingIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onChoosePersonClicked)
                .padding(16.dp),
        ) {
            Icon(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .align(Alignment.CenterVertically),
                imageVector = Icons.Outlined.Person,
                contentDescription = null
            )
            Text(
                modifier = Modifier.align(Alignment.CenterVertically),
                text = stringResource(Res.string.menu_choose_person_action),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAddParticipantsClicked)
                .padding(16.dp),
        ) {
            Icon(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .align(Alignment.CenterVertically),
                imageVector = Icons.Outlined.Add,
                contentDescription = null
            )
            Text(
                modifier = Modifier.align(Alignment.CenterVertically),
                text = stringResource(Res.string.menu_add_participants_action),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Row(
            modifier = Modifier
                .clickable(onClick = onJoinEventClicked)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Icon(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .align(Alignment.CenterVertically),
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null
            )
            Text(
                modifier = Modifier.align(Alignment.CenterVertically),
                text = stringResource(Res.string.menu_join_other_event),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Row(
            modifier = Modifier
                .clickable(onClick = onLeaveEventClicked)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Icon(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .align(Alignment.CenterVertically),
                imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                contentDescription = null
            )
            Text(
                modifier = Modifier.align(Alignment.CenterVertically),
                text = stringResource(Res.string.menu_open_events_list),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        HorizontalDivider()

        LegalBlock(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            onPrivacyPolicyClicked = onPrivacyPolicyClicked,
            onTermsOfUseClicked = onTermsOfUseClicked
        )
    }
}

@Preview
@Composable
private fun MenuDialogPreview() {
    CommonExTheme {
        MenuDialog(
            state = MenuDialogUiModel(
                eventSharing = EventSharingUiModel(
                    eventName = "Пример события",
                    shareState = ShareState.Idle(
                        serverId = "event-id",
                        pinCode = "1234",
                    ),
                ),
            ),
            onJoinEventClicked = {},
            onLeaveEventClicked = {},
            onChoosePersonClicked = {},
            onAddParticipantsClicked = {},
            onShareClicked = {},
            onCopyClicked = {},
            onTextCopied = {},
            onPrivacyPolicyClicked = {},
            onTermsOfUseClicked = {},
        )
    }
}

@Preview
@Composable
private fun MenuDialogEmptyShareUrlPreview() {
    CommonExTheme {
        MenuDialog(
            state = MenuDialogUiModel(
                eventSharing = EventSharingUiModel(
                    eventName = "Пример события",
                    shareState = ShareState.Ready(
                        shareText = ShareText(
                            eventName = "Пример события",
                            fullText = "Пример события\n\nКакой-то текст и ссылка https://commonex.ru/event/ASDASD?pinCode=1234",
                        )
                    ),
                ),
            ),
            onJoinEventClicked = {},
            onLeaveEventClicked = {},
            onChoosePersonClicked = {},
            onAddParticipantsClicked = {},
            onShareClicked = {},
            onCopyClicked = {},
            onTextCopied = {},
            onPrivacyPolicyClicked = {},
            onTermsOfUseClicked = {},
        )
    }
}

@Preview
@Composable
private fun MenuDialogLoadingPreview() {
    CommonExTheme {
        MenuDialog(
            state = MenuDialogUiModel(
                eventSharing = EventSharingUiModel(
                    eventName = "Пример события",
                    shareState = ShareState.Loading,
                ),
            ),
            onJoinEventClicked = {},
            onLeaveEventClicked = {},
            onChoosePersonClicked = {},
            onAddParticipantsClicked = {},
            onShareClicked = {},
            onCopyClicked = {},
            onTextCopied = {},
            onPrivacyPolicyClicked = {},
            onTermsOfUseClicked = {},
        )
    }
}
