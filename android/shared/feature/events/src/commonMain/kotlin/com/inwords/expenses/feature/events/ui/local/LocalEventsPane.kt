package com.inwords.expenses.feature.events.ui.local

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.inwords.expenses.core.ui.design.appbar.TopAppBarWithText
import com.inwords.expenses.core.ui.design.button.ButtonWithIconAndText
import com.inwords.expenses.core.ui.design.layout.AdaptiveButtonsRow
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
import expenses.shared.feature.events.generated.resources.events_event_deleted
import expenses.shared.feature.events.generated.resources.events_join
import expenses.shared.feature.events.generated.resources.events_keep_event
import expenses.shared.feature.events.generated.resources.events_not_synced_yet
import expenses.shared.feature.events.generated.resources.events_welcome_brand
import expenses.shared.feature.events.generated.resources.events_welcome_copy
import expenses.shared.feature.events.generated.resources.events_welcome_create_description
import expenses.shared.feature.events.generated.resources.events_welcome_create_label
import expenses.shared.feature.events.generated.resources.events_welcome_display
import expenses.shared.feature.events.generated.resources.events_welcome_join_description
import expenses.shared.feature.events.generated.resources.events_welcome_join_label
import expenses.shared.feature.events.generated.resources.events_welcome_kicker
import expenses.shared.feature.events.generated.resources.events_welcome_reassurance
import expenses.shared.feature.events.generated.resources.events_your_groups
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
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val showTwoColumns = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) &&
        LocalDensity.current.fontScale < 1.5f

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(paddingValues)
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyVerticalGrid(
                modifier = Modifier
                    .widthIn(max = if (showTwoColumns) 960.dp else 560.dp)
                    .fillMaxSize(),
                columns = GridCells.Fixed(if (showTwoColumns) 2 else 1),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            ) {
                item(key = "heading", contentType = "heading", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).semantics { heading() },
                        text = stringResource(Res.string.events_your_groups),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
                item(key = "actions", contentType = "actions", span = { GridItemSpan(maxLineSpan) }) {
                    LocalEventsActions(
                        modifier = Modifier.padding(bottom = 12.dp),
                        onCreateEventClick = onCreateEventClick,
                        onJoinEventClick = onJoinEventClick,
                    )
                }
                items(
                    items = localEvents.events,
                    key = { it.eventId },
                    contentType = { "event" },
                ) { event ->
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalEventsActions(
    onCreateEventClick: () -> Unit,
    onJoinEventClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val createLabel = stringResource(Res.string.events_create)
    val joinLabel = stringResource(Res.string.events_join)
    val buttonHeight = ButtonDefaults.MinHeight
    val textStyle = ButtonDefaults.textStyleFor(buttonHeight)
    val buttonPadding = ButtonDefaults.contentPaddingFor(buttonHeight)

    AdaptiveButtonsRow(
        modifier = modifier.fillMaxWidth(),
        horizontalSpacing = 12.dp,
        verticalSpacing = 8.dp,
    ) {
        ButtonWithIconAndText(
            modifier = Modifier.testTag("events_create"),
            onClick = onCreateEventClick,
            text = createLabel,
            imageVector = Icons.Outlined.Add,
        )
        TextButton(
            modifier = Modifier.testTag("events_join"),
            onClick = onJoinEventClick,
            contentPadding = buttonPadding,
        ) {
            Text(text = joinLabel, style = textStyle)
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
                            modifier = Modifier.semantics { heading() },
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
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.events_welcome_reassurance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
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
    val showDecorativeIcons = LocalDensity.current.fontScale < 1.5f
    Card(
        modifier = modifier,
        onClick = { onJoinLocalEventClick(event) },
        enabled = !deletionInProgress,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showDecorativeIcons) {
                Surface(
                    modifier = Modifier.size(40.dp).clearAndSetSemantics {},
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Outlined.Person, contentDescription = null)
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = event.eventName, style = MaterialTheme.typography.titleLarge)
                if (!event.isSynced) {
                    Text(
                        text = stringResource(Res.string.events_not_synced_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (deletionInProgress) {
                LoadingIndicator(
                    modifier = Modifier.size(ButtonDefaults.LargeIconSize),
                )
            } else if (showDecorativeIcons) {
                Icon(
                    modifier = Modifier.size(ButtonDefaults.LargeIconSize),
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


@Composable
@Preview
@Preview(widthDp = 320, fontScale = 2f)
@Preview(widthDp = 840, heightDp = 600)
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
                        eventName = "Weekend in Lisbon with friends and family",
                        isSynced = true,
                        deletionState = EventDeletionState.None,
                    ),
                    LocalEventUiModel(
                        eventId = 2,
                        eventName = "Friday dinner",
                        isSynced = false,
                        deletionState = EventDeletionState.None,
                    ),
                    LocalEventUiModel(
                        eventId = 3,
                        eventName = "Road trip",
                        isSynced = false,
                        deletionState = EventDeletionState.PendingDeletionChoice,
                    ),
                    LocalEventUiModel(
                        eventId = 4,
                        eventName = "Mountain cabin",
                        isSynced = true,
                        deletionState = EventDeletionState.Loading,
                    ),
                    LocalEventUiModel(
                        eventId = 5,
                        eventName = "Flatmates",
                        isSynced = true,
                        deletionState = EventDeletionState.RemoteDeletionFailed,
                    ),
                ),
                recentlyRemovedEventName = null,
            ),
        )
    }
}

@Preview(widthDp = 400)
@Preview(widthDp = 320, fontScale = 2f)
@Composable
private fun LocalEventsActionsPreview() {
    CommonExTheme {
        Surface {
            LocalEventsActions(onCreateEventClick = {}, onJoinEventClick = {})
        }
    }
}

@Composable
@Preview
@Preview(widthDp = 320, fontScale = 2f, locale = "ru")
private fun LocalEventsEmptyPanePreview() {
    CommonExTheme {
        LocalEventsEmptyPane(
            onCreateEventClick = {},
            onJoinEventClick = {},
        )
    }
}
