package com.inwords.expenses.feature.expenses.ui.list

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.appbar.TopAppBarWithText
import com.inwords.expenses.core.ui.design.button.BasicFloatingActionButton
import com.inwords.expenses.core.ui.design.loading.DefaultProgressIndicator
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.feature.events.ui.common.EventInfoBlock
import com.inwords.expenses.feature.events.ui.local.LocalEventsEmptyPane
import com.inwords.expenses.feature.events.ui.local.LocalEventsPane
import com.inwords.expenses.feature.events.ui.local.LocalEventsUiModel.LocalEventUiModel
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.ui.common.DebtShortUiModel
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.Expenses.DayChipUiModel
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.Expenses.DaySectionUiModel
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.Expenses.ExpenseUiModel
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.LocalEvents
import com.inwords.expenses.feature.expenses.ui.list.ExpensesTimelineVisibleDayResolver.Layout
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.common_error
import expenses.shared.feature.expenses.generated.resources.expenses_app_name
import expenses.shared.feature.expenses.generated.resources.expenses_operation
import expenses.shared.feature.expenses.generated.resources.expenses_operations
import expenses.shared.feature.expenses.generated.resources.expenses_paid_by
import expenses.shared.feature.expenses.generated.resources.expenses_paid_by_you
import expenses.shared.feature.expenses.generated.resources.expenses_total_spent
import expenses.shared.feature.expenses.generated.resources.expenses_your_part
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Stable key for the sticky day-chips row item in expenses timeline [LazyColumn].
 * Used with [androidx.compose.foundation.lazy.LazyListItemInfo.key] so scroll/sync logic can relate layout to days.
 */
private const val STICKY_DAY_CHIPS_ITEM_KEY = "expenses_timeline_sticky_day_chips"

private data class ExpensesTimelineListLayout(
    val visibleDayLayout: Layout,
    val dayChipsRowItemIndex: Int?,
) {
    val dayHeaderIndexByDayKey: Map<String, Int>
        get() = visibleDayLayout.dayHeaderIndexByDayKey

    companion object {

        val Empty = ExpensesTimelineListLayout(
            visibleDayLayout = Layout(
                orderedDayKeys = emptyList(),
                orderedDayHeaderIndices = emptyList(),
                dayHeaderIndexByDayKey = emptyMap(),
            ),
            dayChipsRowItemIndex = null,
        )
    }
}

/**
 * Lazily computed mapping from each section's calendar day key to the lazy row index of that day's header.
 * Must match the item order emitted by [ExpensesPaneSuccess]'s [LazyColumn].
 *
 * [LazyListState](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyListState) scroll
 * APIs are index-based, so indices are derived here from the same structure as the list so chip
 * actions and visibility tracking stay aligned with list content.
 */
@Composable
private fun rememberExpensesTimelineListLayout(
    daySections: ImmutableList<DaySectionUiModel>,
    showDayChipsRow: Boolean,
): ExpensesTimelineListLayout {
    return remember(daySections, showDayChipsRow) {
        if (daySections.isEmpty()) {
            return@remember ExpensesTimelineListLayout.Empty
        }

        var index = STATIC_ITEMS_BEFORE_DAY_TIMELINE
        val dayChipsRowIndex = if (showDayChipsRow) index++ else null
        val map = HashMap<String, Int>(daySections.size)
        val dayHeaderIndices = ArrayList<Int>(daySections.size)
        val orderedDayKeys = ArrayList<String>(daySections.size)
        for (section in daySections) {
            map[section.dayKey] = index
            dayHeaderIndices += index
            index += 1 + section.expenses.size
            orderedDayKeys += section.dayKey
        }
        ExpensesTimelineListLayout(
            visibleDayLayout = Layout(
                orderedDayKeys = orderedDayKeys,
                orderedDayHeaderIndices = dayHeaderIndices,
                dayHeaderIndexByDayKey = map,
            ),
            dayChipsRowItemIndex = dayChipsRowIndex,
        )
    }
}

private const val STATIC_ITEMS_BEFORE_DAY_TIMELINE = 3

@Composable
internal fun ExpensesPane(
    state: SimpleScreenState<ExpensesPaneUiModel>,
    onMenuClick: () -> Unit,
    onAddExpenseClick: () -> Unit,
    onExpenseClick: (expense: ExpenseUiModel) -> Unit,
    onDebtsDetailsClick: () -> Unit,
    onReplenishmentClick: (debtor: DebtShortUiModel) -> Unit,
    onDayChipClick: (dayKey: String) -> Unit,
    onVisibleDayChanged: (dayKey: String) -> Unit,
    onCreateEventClick: () -> Unit,
    onJoinEventClick: () -> Unit,
    onJoinLocalEventClick: (event: LocalEventUiModel) -> Unit,
    onDeleteEventClick: (event: LocalEventUiModel) -> Unit,
    onDeleteOnlyLocalEventClick: (event: LocalEventUiModel) -> Unit,
    onKeepLocalEventClick: (event: LocalEventUiModel) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is SimpleScreenState.Success -> {
            when (val state = state.data) {
                is ExpensesPaneUiModel.Expenses -> ExpensesPaneSuccess(
                    state = state,
                    onMenuClick = onMenuClick,
                    onAddExpenseClick = onAddExpenseClick,
                    onExpenseClick = onExpenseClick,
                    onDebtsDetailsClick = onDebtsDetailsClick,
                    onReplenishmentClick = onReplenishmentClick,
                    onDayChipClick = onDayChipClick,
                    onVisibleDayChanged = onVisibleDayChanged,
                    onRefresh = onRefresh,
                    modifier = modifier,
                )

                is LocalEvents -> LocalEventsPane(
                    onCreateEventClick = onCreateEventClick,
                    onJoinEventClick = onJoinEventClick,
                    onJoinLocalEventClick = onJoinLocalEventClick,
                    onDeleteEventClick = onDeleteEventClick,
                    onDeleteOnlyLocalEventClick = onDeleteOnlyLocalEventClick,
                    onKeepLocalEventClick = onKeepLocalEventClick,
                    localEvents = state.localEvents,
                    modifier = modifier,
                )
            }
        }

        is SimpleScreenState.Loading -> ExpensesPaneLoading(modifier)

        is SimpleScreenState.Error -> {
            Text(text = stringResource(Res.string.common_error))
        }

        SimpleScreenState.Empty -> LocalEventsEmptyPane(
            onCreateEventClick = onCreateEventClick,
            onJoinEventClick = onJoinEventClick,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpensesPaneSuccess(
    state: ExpensesPaneUiModel.Expenses,
    onMenuClick: () -> Unit,
    onAddExpenseClick: () -> Unit,
    onExpenseClick: (expense: ExpenseUiModel) -> Unit,
    onDebtsDetailsClick: () -> Unit,
    onReplenishmentClick: (debtor: DebtShortUiModel) -> Unit,
    onDayChipClick: (dayKey: String) -> Unit,
    onVisibleDayChanged: (dayKey: String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val collapsedFraction = scrollBehavior.state.collapsedFraction
            val fadeStart = 0.8f
            val fadeProgress = ((collapsedFraction - fadeStart) / (1f - fadeStart)).coerceIn(0f, 1f)
            val easedProgress = FastOutLinearInEasing.transform(fadeProgress)
            val appBarContainerColor = MaterialTheme.colorScheme.surface.copy(
                alpha = 1f - easedProgress,
            )
            TopAppBar(
                modifier = Modifier.testTag(ExpensesPaneTags.TOP_APP_BAR),
                title = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp),
                    ) {
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = stringResource(Res.string.expenses_app_name),
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Bold,
                        )

                        IconButton(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .testTag(ExpensesPaneTags.MENU_BUTTON),
                            onClick = onMenuClick,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Menu,
                                contentDescription = null,
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appBarContainerColor,
                    scrolledContainerColor = appBarContainerColor,
                )
            )
        },
        floatingActionButton = {
            BasicFloatingActionButton(
                text = stringResource(Res.string.expenses_operation),
                imageVector = Icons.Outlined.Add,
                onClick = onAddExpenseClick,
            )
        }
    ) { paddingValues ->
        val topPadding = paddingValues.calculateTopPadding()
        val topPaddingPx = with(LocalDensity.current) { topPadding.toPx() }
        val bottomPadding = paddingValues.calculateBottomPadding()
        val horizontalPaddings = PaddingValues(
            start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
            end = paddingValues.calculateEndPadding(LocalLayoutDirection.current),
        )

        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(horizontalPaddings)
                .padding(horizontalPaddings),
            state = pullToRefreshState,
            isRefreshing = state.isRefreshing,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topPadding),
                    isRefreshing = state.isRefreshing,
                    state = pullToRefreshState,
                )
            },
            onRefresh = onRefresh,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(PaddingValues(bottom = bottomPadding))
            ) {
                val showDayChipsRow = state.dayChips.size > 1
                var dayChipsRowHeightPx by remember { mutableIntStateOf(0) }

                val listLayout = rememberExpensesTimelineListLayout(state.daySections, showDayChipsRow)
                val listState = rememberLazyListState()

                val coroutineScope = rememberCoroutineScope()
                fun scrollToDayHeader(chip: DayChipUiModel) {
                    onDayChipClick(chip.dayKey)
                    val itemIndex = listLayout.dayHeaderIndexByDayKey[chip.dayKey] ?: return
                    // LazyList scroll offsets are applied "forward", so reveal the target header
                    // below the pinned chips row by using a negative obstruction height.
                    val dayHeaderScrollOffsetPx = if (showDayChipsRow) -dayChipsRowHeightPx else 0
                    coroutineScope.launch {
                        listState.animateScrollToItem(
                            index = itemIndex,
                            scrollOffset = dayHeaderScrollOffsetPx,
                        )
                    }
                }

                var inlineDayChipsTopPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
                val showStickyDayChipsOverlay by remember(listState, listLayout, topPaddingPx) {
                    derivedStateOf {
                        val dayChipsRowIndex = listLayout.dayChipsRowItemIndex ?: return@derivedStateOf false
                        when {
                            topPaddingPx <= 0f -> false
                            listState.firstVisibleItemIndex > dayChipsRowIndex -> true
                            listState.firstVisibleItemIndex < dayChipsRowIndex -> false
                            else -> inlineDayChipsTopPx <= topPaddingPx
                        }
                    }
                }

                LaunchedEffect(listState, listLayout) {
                    snapshotFlow {
                        ExpensesTimelineVisibleDayResolver.currentDayKey(
                            visibleItems = listState.layoutInfo.visibleItemsInfo,
                            layout = listLayout.visibleDayLayout,
                            excludedItemIndex = listLayout.dayChipsRowItemIndex,
                            stickyDayChipsHeightPx = dayChipsRowHeightPx,
                            hasStickyDayChipsOverlay = showStickyDayChipsOverlay,
                        )
                    }
                        .filterNotNull()
                        .distinctUntilChanged()
                        .collect(onVisibleDayChanged)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(ExpensesPaneTags.TIMELINE_LIST),
                    state = listState,
                    contentPadding = PaddingValues(
                        top = topPadding,
                        bottom = 88.dp + bottomPadding,
                    ),
                ) {
                    item {
                        EventInfoBlock(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            eventName = state.eventName,
                            currentPersonName = state.currentPersonName
                        )
                    }

                    item {
                        DebtsBlock(
                            onDebtsDetailsClick = onDebtsDetailsClick,
                            state = state,
                            onReplenishmentClick = onReplenishmentClick,
                        )
                    }

                    item {
                        Row(modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = if (showDayChipsRow) 4.dp else 8.dp)) {
                            Text(
                                modifier = Modifier
                                    .alignByBaseline()
                                    .padding(end = 16.dp),
                                text = stringResource(Res.string.expenses_operations),
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                modifier = Modifier
                                    .alignByBaseline()
                                    .testTag(ExpensesPaneTags.TOTAL_SPENDING_VALUE),
                                text = stringResource(Res.string.expenses_total_spent, state.totalSpending),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (showDayChipsRow) {
                        item(
                            key = STICKY_DAY_CHIPS_ITEM_KEY,
                            contentType = "day_chips",
                        ) {
                            DayChipsBar(
                                dayChips = state.dayChips,
                                onDayChipClick = ::scrollToDayHeader,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coordinates ->
                                        inlineDayChipsTopPx = coordinates.boundsInRoot().top
                                        dayChipsRowHeightPx = coordinates.size.height
                                    }
                                    .alpha(if (showStickyDayChipsOverlay) 0f else 1f)
                                    .then(
                                        if (showStickyDayChipsOverlay) {
                                            Modifier.clearAndSetSemantics {}
                                        } else {
                                            Modifier
                                        }
                                    ),
                                rowTestTag = if (showStickyDayChipsOverlay) null else ExpensesPaneTags.DAY_CHIPS_ROW,
                                chipTestTagPrefix = if (showStickyDayChipsOverlay) null else ExpensesPaneTags.DAY_CHIP_PREFIX,
                            )
                        }
                    }

                    state.daySections.forEachIndexed { index, daySection ->
                        item(
                            key = ExpensesPaneTags.timelineDayHeaderKey(daySection.dayKey),
                            contentType = "day_header",
                        ) {
                            DaySectionHeader(
                                daySection = daySection,
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 16.dp, top = if (index == 0) 8.dp else 16.dp, bottom = 16.dp)
                                    .fillMaxWidth(),
                            )
                        }

                        items(
                            items = daySection.expenses,
                            key = { expense -> expense.expenseId },
                            contentType = { "expense_item" },
                        ) { expense ->
                            ExpenseItem(
                                expense = expense,
                                onExpenseClick = onExpenseClick,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                }

                if (showDayChipsRow && showStickyDayChipsOverlay) {
                    DayChipsBar(
                        dayChips = state.dayChips,
                        onDayChipClick = ::scrollToDayHeader,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .offset(y = topPadding)
                            .onGloballyPositioned { coordinates ->
                                dayChipsRowHeightPx = coordinates.size.height
                            }
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                        rowTestTag = ExpensesPaneTags.DAY_CHIPS_ROW,
                        chipTestTagPrefix = ExpensesPaneTags.DAY_CHIP_PREFIX,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayChipsBar(
    dayChips: List<DayChipUiModel>,
    onDayChipClick: (chip: DayChipUiModel) -> Unit,
    rowTestTag: String?,
    chipTestTagPrefix: String?,
    modifier: Modifier = Modifier,
) {
    val chipsListState = rememberLazyListState()
    val selectedChipIndex = remember(dayChips) { dayChips.indexOfFirst { it.isSelected } }
    LaunchedEffect(selectedChipIndex) {
        if (selectedChipIndex < 0) return@LaunchedEffect
        val isSelectedChipVisible = chipsListState.layoutInfo.visibleItemsInfo.any { it.index == selectedChipIndex }
        if (!isSelectedChipVisible) {
            chipsListState.animateScrollToItem(selectedChipIndex)
        }
    }

    LazyRow(
        state = chipsListState,
        modifier = modifier
            .fillMaxWidth()
            .then(if (rowTestTag == null) Modifier else Modifier.testTag(rowTestTag)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(all = 8.dp),
    ) {
        items(
            items = dayChips,
            key = { it.dayKey },
        ) { chip ->
            FilterChip(
                modifier = if (chipTestTagPrefix == null) {
                    Modifier
                } else {
                    Modifier.testTag("$chipTestTagPrefix${chip.dayKey}")
                },
                selected = chip.isSelected,
                onClick = { onDayChipClick(chip) },
                label = {
                    Text(text = chip.label)
                },
            )
        }
    }
}

@Composable
private fun DaySectionHeader(
    daySection: DaySectionUiModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.testTag(ExpensesPaneTags.dayHeader(daySection.dayKey)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = daySection.headerLabel,
            style = MaterialTheme.typography.bodyLarge,
        )
        daySection.spendingTotal?.let { spendingTotal ->
            Text(
                modifier = Modifier.testTag(ExpensesPaneTags.dayHeaderTotal(daySection.dayKey)),
                text = spendingTotal,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpensesPaneLoading(
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBarWithText() },
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .consumeWindowInsets(paddingValues)
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            DefaultProgressIndicator()
        }
    }
}

@Composable
private fun ExpenseItem(
    expense: ExpenseUiModel,
    onExpenseClick: (expense: ExpenseUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable { onExpenseClick.invoke(expense) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val amountColor = when (expense.expenseType) {
                ExpenseType.Spending -> MaterialTheme.colorScheme.onSurface
                ExpenseType.Replenishment -> MaterialTheme.colorScheme.primary
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = expense.currencyText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = expense.totalAmount,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        color = amountColor,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (expense.isPaidByCurrentPerson) {
                                stringResource(Res.string.expenses_paid_by_you)
                            } else {
                                "${stringResource(Res.string.expenses_paid_by)}:"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        if (!expense.isPaidByCurrentPerson) {
                            Text(
                                text = expense.personName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        text = expense.timeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            expense.currentPersonPartAmount?.let { currentPersonPart ->
                Text(
                    text = stringResource(Res.string.expenses_your_part, currentPersonPart),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = expense.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview
@Composable
private fun ExpensesPanePreviewSuccessWithCreditors() {
    CommonExTheme {
        ExpensesPane(
            state = SimpleScreenState.Success(mockExpensesPaneUiModel(withDebts = true)),
            onMenuClick = {},
            onAddExpenseClick = {},
            onExpenseClick = {},
            onDebtsDetailsClick = {},
            onReplenishmentClick = {},
            onDayChipClick = {},
            onVisibleDayChanged = {},
            onCreateEventClick = {},
            onJoinEventClick = {},
            onJoinLocalEventClick = {},
            onDeleteEventClick = {},
            onDeleteOnlyLocalEventClick = {},
            onKeepLocalEventClick = {},
            onRefresh = {},
        )
    }
}

@Preview
@Composable
private fun ExpensesPanePreviewSuccessWithoutCreditors() {
    CommonExTheme {
        ExpensesPane(
            state = SimpleScreenState.Success(mockExpensesPaneUiModel(withDebts = false)),
            onMenuClick = {},
            onAddExpenseClick = {},
            onExpenseClick = {},
            onDebtsDetailsClick = {},
            onReplenishmentClick = {},
            onDayChipClick = {},
            onVisibleDayChanged = {},
            onCreateEventClick = {},
            onJoinEventClick = {},
            onJoinLocalEventClick = {},
            onDeleteEventClick = {},
            onDeleteOnlyLocalEventClick = {},
            onKeepLocalEventClick = {},
            onRefresh = {},
        )
    }
}

@Preview
@Composable
private fun ExpensesPanePreviewEmpty() {
    CommonExTheme {
        ExpensesPane(
            state = SimpleScreenState.Empty,
            onMenuClick = {},
            onAddExpenseClick = {},
            onExpenseClick = {},
            onDebtsDetailsClick = {},
            onReplenishmentClick = {},
            onDayChipClick = {},
            onVisibleDayChanged = {},
            onCreateEventClick = {},
            onJoinEventClick = {},
            onJoinLocalEventClick = {},
            onDeleteEventClick = {},
            onDeleteOnlyLocalEventClick = {},
            onKeepLocalEventClick = {},
            onRefresh = {},
        )
    }
}

@Preview
@Composable
private fun ExpensesPanePreviewLoading() {
    CommonExTheme {
        ExpensesPane(
            state = SimpleScreenState.Loading,
            onMenuClick = {},
            onAddExpenseClick = {},
            onExpenseClick = {},
            onDebtsDetailsClick = {},
            onReplenishmentClick = {},
            onDayChipClick = {},
            onVisibleDayChanged = {},
            onCreateEventClick = {},
            onJoinEventClick = {},
            onJoinLocalEventClick = {},
            onDeleteEventClick = {},
            onDeleteOnlyLocalEventClick = {},
            onKeepLocalEventClick = {},
            onRefresh = {},
        )
    }
}

internal fun mockExpensesPaneUiModel(withDebts: Boolean): ExpensesPaneUiModel {
    return ExpensesPaneUiModel.Expenses(
        eventId = 1,
        eventName = "France trip",
        currentPersonId = 1,
        currentPersonName = "Vasiliy",
        debts = persistentListOf(
            DebtShortUiModel(
                personId = 2,
                personName = "Maksim",
                currencyCode = "EUR",
                currencyName = "Euro",
                amount = "100",
            ),
        ).takeIf { withDebts } ?: persistentListOf(),
        totalSpending = "180 EUR",
        dayChips = persistentListOf(
            DayChipUiModel(dayKey = "2026-03-28", label = "Today", isSelected = true),
            DayChipUiModel(dayKey = "2026-03-27", label = "Yesterday", isSelected = false),
        ),
        daySections = persistentListOf(
            DaySectionUiModel(
                dayKey = "2026-03-28",
                headerLabel = "28 March 2026",
                spendingTotal = "120 EUR",
                expenses = persistentListOf(
                    mockExpenseUiModel(
                        expenseId = 1,
                        expenseType = ExpenseType.Spending,
                        totalAmount = "-120",
                        timeText = "18:30",
                        description = "Lunch",
                    ),
                    mockExpenseUiModel(
                        expenseId = 2,
                        expenseType = ExpenseType.Replenishment,
                        totalAmount = "+20",
                        timeText = "12:05",
                        description = "Refund",
                    ),
                ),
            ),
            DaySectionUiModel(
                dayKey = "2026-03-27",
                headerLabel = "27 March 2026",
                spendingTotal = "60 EUR",
                expenses = persistentListOf(
                    mockExpenseUiModel(
                        expenseId = 3,
                        expenseType = ExpenseType.Spending,
                        totalAmount = "-60",
                        timeText = "09:15",
                        description = "Museum",
                    ),
                ),
            ),
        ),
        isRefreshing = false,
    )
}

private fun mockExpenseUiModel(
    expenseId: Long,
    expenseType: ExpenseType,
    totalAmount: String,
    timeText: String,
    description: String,
): ExpenseUiModel {
    return ExpenseUiModel(
        expenseId = expenseId,
        currencyText = "Euro",
        expenseType = expenseType,
        personName = "Vasiliy",
        isPaidByCurrentPerson = true,
        totalAmount = totalAmount,
        timeText = timeText,
        description = description,
        currentPersonPartAmount = null,
    )
}
