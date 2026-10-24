package com.inwords.expenses.feature.menu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.button.ButtonWithIconAndText
import com.inwords.expenses.core.ui.design.layout.AdaptiveButtonsRow
import com.inwords.expenses.core.ui.design.legal.LegalBlock
import com.inwords.expenses.core.ui.design.preview.ScreenPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.core.ui.utils.clipEntryOf
import com.inwords.expenses.feature.menu.ui.EventSharingUiModel.ShareState
import com.inwords.expenses.feature.menu.ui.EventSharingUiModel.ShareState.Companion.canShare
import expenses.shared.feature.menu.generated.resources.Res
import expenses.shared.feature.menu.generated.resources.menu_add_participants_action
import expenses.shared.feature.menu.generated.resources.menu_choose_person_action
import expenses.shared.feature.menu.generated.resources.menu_copy_action
import expenses.shared.feature.menu.generated.resources.menu_events_heading
import expenses.shared.feature.menu.generated.resources.menu_invite_body
import expenses.shared.feature.menu.generated.resources.menu_invite_preparing
import expenses.shared.feature.menu.generated.resources.menu_invite_sync
import expenses.shared.feature.menu.generated.resources.menu_invite_title
import expenses.shared.feature.menu.generated.resources.menu_join_other_event
import expenses.shared.feature.menu.generated.resources.menu_open_events_list
import expenses.shared.feature.menu.generated.resources.menu_people_heading
import expenses.shared.feature.menu.generated.resources.menu_share_action
import expenses.shared.feature.menu.generated.resources.menu_title
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
    Box(modifier = modifier.fillMaxWidth().testTag("menu_options"), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Column(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.menu_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    modifier = Modifier.testTag("menu_event_name").semantics { heading() },
                    text = eventSharing.eventName,
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        modifier = Modifier.semantics { heading() },
                        text = stringResource(Res.string.menu_invite_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(
                            if (eventSharing.shareState is ShareState.Idle && !eventSharing.shareState.isAvailable) {
                                Res.string.menu_invite_sync
                            } else {
                                Res.string.menu_invite_body
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    val buttonHeight = ButtonDefaults.MediumContainerHeight
                    val buttonTextStyle = ButtonDefaults.textStyleFor(buttonHeight)
                    val shareLabel = stringResource(Res.string.menu_share_action)
                    val copyLabel = stringResource(
                        if (eventSharing.shareState is ShareState.Loading) Res.string.menu_invite_preparing else Res.string.menu_copy_action
                    )
                    val buttonPadding = ButtonDefaults.contentPaddingFor(buttonHeight)

                    AdaptiveButtonsRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalSpacing = 8.dp,
                        verticalSpacing = 8.dp,
                    ) {
                        ButtonWithIconAndText(
                            modifier = Modifier.testTag("menu_share"),
                            onClick = onShareClicked,
                            enabled = eventSharing.shareState.canShare,
                            text = shareLabel,
                            imageVector = Icons.Outlined.Share,
                        )
                        FilledTonalButton(
                            modifier = Modifier.heightIn(min = buttonHeight)
                                .testTag("menu_copy"),
                            onClick = onCopyClicked,
                            enabled = eventSharing.shareState.canShare,
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = buttonPadding,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            if (eventSharing.shareState is ShareState.Loading) {
                                LoadingIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                            }
                            Text(text = copyLabel, style = buttonTextStyle)
                        }
                    }
                }
            }
            Text(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 8.dp)
                    .semantics { heading() },
                text = stringResource(Res.string.menu_people_heading),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    GroupOptionsAction(
                        text = stringResource(Res.string.menu_choose_person_action),
                        icon = Icons.Outlined.Person,
                        onClick = onChoosePersonClicked,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    GroupOptionsAction(
                        text = stringResource(Res.string.menu_add_participants_action),
                        icon = Icons.Outlined.Add,
                        onClick = onAddParticipantsClicked,
                    )
                }
            }
            Text(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 8.dp)
                    .semantics { heading() },
                text = stringResource(Res.string.menu_events_heading),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    GroupOptionsAction(
                        text = stringResource(Res.string.menu_open_events_list),
                        icon = Icons.AutoMirrored.Outlined.List,
                        onClick = onLeaveEventClicked,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    GroupOptionsAction(
                        text = stringResource(Res.string.menu_join_other_event),
                        icon = Icons.AutoMirrored.Outlined.ExitToApp,
                        onClick = onJoinEventClicked,
                    )
                }
            }
            LegalBlock(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 32.dp, bottom = 16.dp),
                onPrivacyPolicyClicked = onPrivacyPolicyClicked,
                onTermsOfUseClicked = onTermsOfUseClicked,
            )
        }
    }
}

@Composable
private fun GroupOptionsAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(modifier = Modifier.weight(1f), text = text, style = MaterialTheme.typography.bodyLarge)
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@ScreenPreviews
@Composable
private fun MenuDialogPreview() {
    MenuDialogSample(shareState = ShareState.Idle(serverId = "7K4M2D8P", pinCode = "1234"), darkTheme = false)
}

@Preview(name = "Not synced yet", widthDp = 360, heightDp = 800)
@Composable
private fun MenuDialogUnsyncedPreview() {
    MenuDialogSample(shareState = ShareState.Idle(serverId = null, pinCode = "1234"), darkTheme = false)
}

@Preview(name = "Preparing invitation", widthDp = 360, heightDp = 800)
@Composable
private fun MenuDialogLoadingPreview() {
    MenuDialogSample(shareState = ShareState.Loading, darkTheme = false)
}

@Preview(name = "Dark", widthDp = 360, heightDp = 800)
@Composable
private fun MenuDialogDarkPreview() {
    MenuDialogSample(shareState = ShareState.Idle(serverId = "7K4M2D8P", pinCode = "1234"), darkTheme = true)
}

@Composable
private fun MenuDialogSample(shareState: ShareState, darkTheme: Boolean) {
    CommonExTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            MenuDialog(
                modifier = Modifier.padding(vertical = 24.dp),
                state = MenuDialogUiModel(
                    eventSharing = EventSharingUiModel(eventName = "Weekend in Lisbon", shareState = shareState),
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
}
