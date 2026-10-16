package com.inwords.expenses.feature.events.ui.local

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.appbar.TopAppBarWithText
import com.inwords.expenses.core.ui.design.button.ButtonWithIconAndText
import com.inwords.expenses.core.ui.design.button.OutlinedButtonWithText
import com.inwords.expenses.core.ui.design.layout.RevealState
import com.inwords.expenses.core.ui.design.layout.SwipeToRevealBox
import com.inwords.expenses.core.ui.design.layout.rememberSwipeToRevealBoxState
import com.inwords.expenses.core.ui.design.legal.LegalBlock
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.feature.events.api.EventDeletionStateManager.EventDeletionState
import com.inwords.expenses.feature.events.ui.local.LocalEventsUiModel.LocalEventUiModel
import expenses.shared.core.ui_design.generated.resources.agree_by_continuing
import expenses.shared.feature.events.generated.resources.Res
import expenses.shared.feature.events.generated.resources.events_create
import expenses.shared.feature.events.generated.resources.events_delete_event_offline_message
import expenses.shared.feature.events.generated.resources.events_delete_everywhere
import expenses.shared.feature.events.generated.resources.events_delete_local_only
import expenses.shared.feature.events.generated.resources.events_event
import expenses.shared.feature.events.generated.resources.events_event_deleted
import expenses.shared.feature.events.generated.resources.events_join
import expenses.shared.feature.events.generated.resources.events_keep_event
import expenses.shared.feature.events.generated.resources.events_welcome_brand
import expenses.shared.feature.events.generated.resources.events_welcome_copy
import expenses.shared.feature.events.generated.resources.events_welcome_create_description
import expenses.shared.feature.events.generated.resources.events_welcome_create_label
import expenses.shared.feature.events.generated.resources.events_welcome_display
import expenses.shared.feature.events.generated.resources.events_welcome_join_description
import expenses.shared.feature.events.generated.resources.events_welcome_join_label
import expenses.shared.feature.events.generated.resources.events_welcome_kicker
import expenses.shared.feature.events.generated.resources.events_welcome_reassurance
import expenses.shared.feature.events.generated.resources.events_your
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import expenses.shared.core.ui_design.generated.resources.Res as DesignRes


@Composable
fun LocalEventsPane(
    onCreateEventClick: () -> Unit,
    onJoinEventClick: () -> Unit,
    onJoinLocalEventClick: (event: LocalEventUiModel) -> Unit,
    onDeleteEventClick: (event: LocalEventUiModel) -> Unit,
    onDeleteOnlyLocalEventClick: (event: LocalEventUiModel) -> Unit,
    onKeepLocalEventClick: (event: LocalEventUiModel) -> Unit,
    localEvents: LocalEventsUiModel,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    localEvents.recentlyRemovedEventName?.let { eventName ->
        val message = stringResource(Res.string.events_event_deleted, eventName)
        LaunchedEffect(eventName) {
            snackbarHostState.showSnackbar(message = message)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBarWithText() },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        val topAndHorizontalPaddings = PaddingValues(
            top = paddingValues.calculateTopPadding(),
            start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
            end = paddingValues.calculateEndPadding(LocalLayoutDirection.current),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(topAndHorizontalPaddings)
                .padding(topAndHorizontalPaddings),
        ) {
            Text(
                text = stringResource(Res.string.events_event),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButtonWithText(
                    modifier = Modifier
                        .testTag("events_join")
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onJoinEventClick,
                    text = stringResource(Res.string.events_join),
                    minHeight = ButtonDefaults.MediumContainerHeight,
                )
                ButtonWithIconAndText(
                    modifier = Modifier
                        .testTag("events_create")
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onCreateEventClick,
                    text = stringResource(Res.string.events_create),
                    imageVector = Icons.Outlined.Add,
                    minHeight = ButtonDefaults.MediumContainerHeight,
                )
            }

            Text(
                text = stringResource(Res.string.events_your),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val bottomPadding = paddingValues.calculateBottomPadding()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .consumeWindowInsets(PaddingValues(bottom = bottomPadding))
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp + bottomPadding),
            ) {
                items(
                    count = localEvents.events.size,
                    key = { index -> localEvents.events[index].eventId }
                ) { index ->
                    val event = localEvents.events[index]
                    LocalEventItem(
                        event = event,
                        onJoinLocalEventClick = onJoinLocalEventClick,
                        onDeleteEventClick = onDeleteEventClick,
                        onDeleteOnlyLocalEventClick = onDeleteOnlyLocalEventClick,
                        onKeepLocalEventClick = onKeepLocalEventClick,
                    )
                }
            }
        }
    }
}

@Composable
fun LocalEventsEmptyPane(
    onCreateEventClick: () -> Unit,
    onJoinEventClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("events_welcome"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.events_welcome_brand),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
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
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = stringResource(Res.string.events_welcome_kicker),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(Res.string.events_welcome_display),
                            style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Serif),
                        )
                        Text(
                            text = stringResource(Res.string.events_welcome_copy),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        WelcomeChoice(
                            title = stringResource(Res.string.events_welcome_create_label),
                            description = stringResource(Res.string.events_welcome_create_description),
                            imageVector = Icons.Outlined.Person,
                            onClick = onCreateEventClick,
                            modifier = Modifier.testTag("events_create"),
                        )
                        WelcomeChoice(
                            title = stringResource(Res.string.events_welcome_join_label),
                            description = stringResource(Res.string.events_welcome_join_description),
                            imageVector = Icons.Outlined.MailOutline,
                            onClick = onJoinEventClick,
                            modifier = Modifier.testTag("events_join"),
                        )
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.events_welcome_reassurance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(DesignRes.string.agree_by_continuing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    LegalBlock(
                        onPrivacyPolicyClicked = { /* No additional action needed - URL opening handled by component */ },
                        onTermsOfUseClicked = { /* No additional action needed - URL opening handled by component */ },
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeChoice(
    title: String,
    description: String,
    imageVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { role = Role.Button },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LocalEventItem(
    event: LocalEventUiModel,
    onJoinLocalEventClick: (event: LocalEventUiModel) -> Unit,
    onDeleteEventClick: (event: LocalEventUiModel) -> Unit,
    onDeleteOnlyLocalEventClick: (event: LocalEventUiModel) -> Unit,
    onKeepLocalEventClick: (event: LocalEventUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRemoteDeletionFailed = event.deletionState == EventDeletionState.RemoteDeletionFailed
    val deletionInProgress = event.deletionState == EventDeletionState.Loading

    val revealBoxState = rememberSwipeToRevealBoxState(
        initialValue = if (isRemoteDeletionFailed) RevealState.Revealed else RevealState.Hidden
    )
    val scope = rememberCoroutineScope()

    val settledValue = revealBoxState.settledValue
    LaunchedEffect(deletionInProgress, settledValue, isRemoteDeletionFailed) {
        val targetValue = when {
            deletionInProgress -> RevealState.Hidden
            isRemoteDeletionFailed -> RevealState.Revealed
            else -> settledValue
        }
        revealBoxState.animateTo(targetValue)
    }

    var dismissedByButton by remember { mutableStateOf(false) }
    // When user swipes back to hidden on a RemoteDeletionFailed event, call onKeepLocalEventClick
    LaunchedEffect(event.eventId, isRemoteDeletionFailed) {
        if (isRemoteDeletionFailed) {
            snapshotFlow { revealBoxState.settledValue }
                .drop(1) // Skip initial/triggered reveal
                .collect { state ->
                    if (state == RevealState.Hidden && !deletionInProgress) {
                        if (dismissedByButton) {
                            dismissedByButton = false // Reset flag, callback already called by button
                        } else {
                            onKeepLocalEventClick(event) // User swiped to keep the event
                        }
                    }
                }
        }
    }

    SwipeToRevealBox(
        modifier = modifier.fillMaxWidth(),
        state = revealBoxState,
        actions = {
            val enabled = !deletionInProgress
            if (event.isSynced) {
                if (isRemoteDeletionFailed) {
                    SwipeMessagePanel(
                        modifier = Modifier.fillMaxHeight().width(100.dp),
                        text = stringResource(Res.string.events_delete_event_offline_message),
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    SwipeActionButton(
                        modifier = Modifier.fillMaxHeight().width(90.dp),
                        onClick = { onDeleteOnlyLocalEventClick(event) },
                        enabled = enabled,
                        backgroundColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        icon = Icons.Outlined.Delete,
                        text = stringResource(Res.string.events_delete_local_only),
                        roundedStart = true,
                        roundedEnd = false,
                    )
                    SwipeActionButton(
                        modifier = Modifier.fillMaxHeight().width(90.dp),
                        onClick = {
                            scope.launch {
                                dismissedByButton = true
                                revealBoxState.animateTo(RevealState.Hidden)
                                onKeepLocalEventClick(event)
                            }
                        },
                        enabled = enabled,
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        icon = Icons.Outlined.Check,
                        text = stringResource(Res.string.events_keep_event),
                        roundedStart = false,
                        roundedEnd = true,
                    )
                } else {
                    SwipeActionButton(
                        modifier = Modifier.fillMaxHeight().width(90.dp),
                        onClick = { onDeleteEventClick(event) },
                        enabled = enabled,
                        backgroundColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        icon = Icons.Outlined.Delete,
                        text = stringResource(Res.string.events_delete_everywhere),
                        roundedStart = true,
                        roundedEnd = false,
                    )
                    SwipeActionButton(
                        modifier = Modifier.fillMaxHeight().width(90.dp),
                        onClick = { onDeleteOnlyLocalEventClick(event) },
                        enabled = enabled,
                        backgroundColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        icon = Icons.Outlined.Delete,
                        text = stringResource(Res.string.events_delete_local_only),
                        roundedStart = false,
                        roundedEnd = true,
                    )
                }
            } else {
                SwipeActionButton(
                    modifier = Modifier.fillMaxHeight().width(90.dp),
                    onClick = { onDeleteOnlyLocalEventClick(event) },
                    enabled = enabled,
                    backgroundColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    icon = Icons.Outlined.Delete,
                    text = stringResource(Res.string.events_delete_local_only),
                    roundedStart = true,
                    roundedEnd = false,
                )
                SwipeActionButton(
                    modifier = Modifier.fillMaxHeight().width(90.dp),
                    onClick = {
                        scope.launch {
                            revealBoxState.animateTo(RevealState.Hidden)
                            onKeepLocalEventClick(event)
                        }
                    },
                    enabled = enabled,
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    icon = Icons.Outlined.Check,
                    text = stringResource(Res.string.events_keep_event),
                    roundedStart = false,
                    roundedEnd = true,
                )
            }
        },
        content = {
            LocalEventCard(
                modifier = Modifier.fillMaxWidth(),
                event = event,
                deletionInProgress = deletionInProgress,
                onJoinLocalEventClick = onJoinLocalEventClick,
            )
        }
    )
}


@Composable
private fun SwipeActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    backgroundColor: Color,
    contentColor: Color,
    icon: ImageVector,
    text: String,
    roundedStart: Boolean,
    roundedEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    val baseShape = MaterialTheme.shapes.medium
    val shape = baseShape.copy(
        topStart = if (roundedStart) baseShape.topStart else ZeroCornerSize,
        bottomStart = if (roundedStart) baseShape.bottomStart else ZeroCornerSize,
        topEnd = if (roundedEnd) baseShape.topEnd else ZeroCornerSize,
        bottomEnd = if (roundedEnd) baseShape.bottomEnd else ZeroCornerSize,
    )
    Column(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .clickable(onClick = onClick, enabled = enabled),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun SwipeMessagePanel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalEventCard(
    event: LocalEventUiModel,
    onJoinLocalEventClick: (event: LocalEventUiModel) -> Unit,
    deletionInProgress: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        onClick = { onJoinLocalEventClick(event) },
        enabled = !deletionInProgress,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                text = event.eventName,
                style = MaterialTheme.typography.titleLarge,
            )


            if (deletionInProgress) {
                LoadingIndicator(
                    modifier = Modifier.size(ButtonDefaults.LargeIconSize),
                )
            } else {
                Icon(
                    modifier = Modifier.size(ButtonDefaults.LargeIconSize),
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}


@Composable
@Preview
private fun LocalEventsPanePreview() {
    CommonExTheme {
        LocalEventsPane(
            onCreateEventClick = {},
            onJoinEventClick = {},
            onJoinLocalEventClick = {},
            onDeleteEventClick = {},
            onDeleteOnlyLocalEventClick = {},
            onKeepLocalEventClick = {},
            localEvents = LocalEventsUiModel(
                events = persistentListOf(
                    LocalEventUiModel(
                        eventId = 1,
                        eventName = "Local Event 1",
                        isSynced = true,
                        deletionState = EventDeletionState.None,
                    ),
                    LocalEventUiModel(
                        eventId = 2,
                        eventName = "Local Event 2 (local only)",
                        isSynced = false,
                        deletionState = EventDeletionState.None,
                    ),
                    LocalEventUiModel(
                        eventId = 3,
                        eventName = "Local Event 3 (local only)",
                        isSynced = false,
                        deletionState = EventDeletionState.PendingDeletionChoice,
                    ),
                    LocalEventUiModel(
                        eventId = 4,
                        eventName = "Local Event 4",
                        isSynced = true,
                        deletionState = EventDeletionState.Loading,
                    ),
                    LocalEventUiModel(
                        eventId = 5,
                        eventName = "Local Event 5",
                        isSynced = true,
                        deletionState = EventDeletionState.RemoteDeletionFailed,
                    ),
                ),
                recentlyRemovedEventName = null,
            ),
        )
    }
}

@Composable
@Preview
private fun LocalEventsEmptyPanePreview() {
    CommonExTheme {
        LocalEventsEmptyPane(
            onCreateEventClick = {},
            onJoinEventClick = {},
        )
    }
}
