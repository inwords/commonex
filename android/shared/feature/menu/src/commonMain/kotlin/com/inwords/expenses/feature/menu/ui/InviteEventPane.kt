package com.inwords.expenses.feature.menu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.appbar.TopAppBarWithNavIconAndText
import com.inwords.expenses.core.ui.design.button.ButtonWithIconAndText
import com.inwords.expenses.core.ui.design.button.OutlinedButtonWithText
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.core.ui.utils.clipEntryOf
import com.inwords.expenses.feature.menu.ui.EventSharingUiModel.ShareState
import com.inwords.expenses.feature.menu.ui.EventSharingUiModel.ShareState.Companion.canShare
import expenses.shared.feature.menu.generated.resources.Res
import expenses.shared.feature.menu.generated.resources.menu_copy_action
import expenses.shared.feature.menu.generated.resources.menu_invite_back
import expenses.shared.feature.menu.generated.resources.menu_invite_body
import expenses.shared.feature.menu.generated.resources.menu_invite_heading
import expenses.shared.feature.menu.generated.resources.menu_invite_sync
import expenses.shared.feature.menu.generated.resources.menu_invite_title
import expenses.shared.feature.menu.generated.resources.menu_share_action
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InviteEventPane(
    state: EventSharingUiModel,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onCopyClick: () -> Unit,
    onTextCopied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    (state.shareState as? ShareState.PendingClipboardCopy)?.let { pending ->
        LaunchedEffect(pending.shareText) {
            clipboard.setClipEntry(clipEntryOf(state.eventName, pending.shareText.fullText))
            onTextCopied()
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("event_invite"),
        topBar = {
            TopAppBarWithNavIconAndText(
                onNavIconClicked = onBackClick,
                title = stringResource(Res.string.menu_invite_title),
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(Res.string.menu_invite_back),
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        modifier = Modifier.semantics { heading() },
                        text = stringResource(Res.string.menu_invite_heading),
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = stringResource(Res.string.menu_invite_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.eventName,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                    if (state.shareState is ShareState.Idle && !state.shareState.isAvailable) {
                        Text(
                            text = stringResource(Res.string.menu_invite_sync),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.shareState is ShareState.Loading) {
                        LoadingIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
                Column(modifier = Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ButtonWithIconAndText(
                        onClick = onShareClick,
                        text = stringResource(Res.string.menu_share_action),
                        imageVector = Icons.Outlined.Share,
                        enabled = state.shareState.canShare,
                        minHeight = ButtonDefaults.MediumContainerHeight,
                        modifier = Modifier.fillMaxWidth().testTag("event_invite_share"),
                    )
                    OutlinedButtonWithText(
                        onClick = onCopyClick,
                        enabled = state.shareState.canShare,
                        text = stringResource(Res.string.menu_copy_action),
                        modifier = Modifier.fillMaxWidth().testTag("event_invite_copy"),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun InviteEventPanePreview() {
    CommonExTheme {
        InviteEventPane(
            state = EventSharingUiModel("Weekend in Lisbon", ShareState.Idle(serverId = null, pinCode = "")),
            onBackClick = {},
            onShareClick = {},
            onCopyClick = {},
            onTextCopied = {},
        )
    }
}
