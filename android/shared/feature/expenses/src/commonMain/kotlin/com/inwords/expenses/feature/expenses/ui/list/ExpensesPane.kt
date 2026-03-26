package com.inwords.expenses.feature.expenses.ui.list

import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
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
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.ui.common.EventInfoBlock
import com.inwords.expenses.feature.events.ui.local.LocalEventsEmptyPane
import com.inwords.expenses.feature.events.ui.local.LocalEventsPane
import com.inwords.expenses.feature.events.ui.local.LocalEventsUiModel.LocalEventUiModel
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.ui.common.DebtShortUiModel
import com.inwords.expenses.feature.expenses.ui.converter.toUiModel
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.Expenses.ExpenseUiModel
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.LocalEvents
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.common_error
import expenses.shared.feature.expenses.generated.resources.expenses_app_name
import expenses.shared.feature.expenses.generated.resources.expenses_operation
import expenses.shared.feature.expenses.generated.resources.expenses_operations
import expenses.shared.feature.expenses.generated.resources.expenses_paid_by
import expenses.shared.feature.expenses.generated.resources.expenses_paid_by_you
import expenses.shared.feature.expenses.generated.resources.expenses_your_part
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock


@Composable
internal fun ExpensesPane(
    state: SimpleScreenState<ExpensesPaneUiModel>,
    onMenuClick: () -> Unit,
    onAddExpenseClick: () -> Unit,
    onExpenseClick: (expense: ExpenseUiModel) -> Unit,
    onDebtsDetailsClick: () -> Unit,
    onReplenishmentClick: (debtor: DebtShortUiModel) -> Unit,
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
                    onRefresh = onRefresh,
                    modifier = modifier
                )

                is LocalEvents -> LocalEventsPane(
                    onCreateEventClick = onCreateEventClick,
                    onJoinEventClick = onJoinEventClick,
                    onJoinLocalEventClick = onJoinLocalEventClick,
                    onDeleteEventClick = onDeleteEventClick,
                    onDeleteOnlyLocalEventClick = onDeleteOnlyLocalEventClick,
                    onKeepLocalEventClick = onKeepLocalEventClick,
                    localEvents = state.localEvents,
                    modifier = modifier
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
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
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
                alpha = 1f - easedProgress
            )
            TopAppBar(
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
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .testTag("expenses_menu_button"),
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
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .consumeWindowInsets(PaddingValues(bottom = bottomPadding)),
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
                    Text(
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 4.dp),
                        text = stringResource(Res.string.expenses_operations),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }

                items(
                    count = state.expenses.size,
                    key = { index ->
                        state.expenses[state.expenses.lastIndex - index].expenseId
                    }
                ) { index ->
                    val expense = state.expenses[state.expenses.lastIndex - index]
                    ExpenseItem(
                        expense = expense,
                        onExpenseClick = onExpenseClick,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
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
                        text = expense.timestamp,
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
            onMenuClick = {},
            onAddExpenseClick = {},
            onExpenseClick = {},
            onDebtsDetailsClick = {},
            onReplenishmentClick = {},
            onJoinEventClick = {},
            onJoinLocalEventClick = {},
            onDeleteEventClick = {},
            onDeleteOnlyLocalEventClick = {},
            onKeepLocalEventClick = {},
            onCreateEventClick = {},
            onRefresh = {},
            state = SimpleScreenState.Success(mockExpensesPaneUiModel(withDebts = true))
        )
    }
}

@Preview
@Composable
private fun ExpensesPanePreviewSuccessWithoutCreditors() {
    CommonExTheme {
        ExpensesPane(
            onMenuClick = {},
            onAddExpenseClick = {},
            onExpenseClick = {},
            onDebtsDetailsClick = {},
            onReplenishmentClick = {},
            onJoinEventClick = {},
            onJoinLocalEventClick = {},
            onDeleteEventClick = {},
            onDeleteOnlyLocalEventClick = {},
            onKeepLocalEventClick = {},
            onCreateEventClick = {},
            onRefresh = {},
            state = SimpleScreenState.Success(mockExpensesPaneUiModel(withDebts = false))
        )
    }
}

@Composable
@Preview
private fun ExpensesPanePreviewEmpty() {
    CommonExTheme {
        ExpensesPane(
            onMenuClick = {},
            onAddExpenseClick = {},
            onExpenseClick = {},
            onDebtsDetailsClick = {},
            onReplenishmentClick = {},
            onJoinEventClick = {},
            onJoinLocalEventClick = {},
            onCreateEventClick = {},
            onRefresh = {},
            onDeleteEventClick = {},
            onDeleteOnlyLocalEventClick = {},
            onKeepLocalEventClick = {},
            state = SimpleScreenState.Empty
        )
    }
}

@Composable
@Preview
private fun ExpensesPanePreviewLoading() {
    CommonExTheme {
        ExpensesPane(
            onMenuClick = {},
            onAddExpenseClick = {},
            onExpenseClick = {},
            onDebtsDetailsClick = {},
            onReplenishmentClick = {},
            onJoinEventClick = {},
            onJoinLocalEventClick = {},
            onCreateEventClick = {},
            onRefresh = {},
            onDeleteEventClick = {},
            onDeleteOnlyLocalEventClick = {},
            onKeepLocalEventClick = {},
            state = SimpleScreenState.Loading
        )
    }
}

internal fun mockExpensesPaneUiModel(withDebts: Boolean): ExpensesPaneUiModel {
    val person1 = Person(
        id = 1,
        serverId = "11",
        name = "Василий"
    )
    val person2 = Person(
        id = 2,
        serverId = "12",
        name = "Максим"
    )
    return ExpensesPaneUiModel.Expenses(
        eventId = 1,
        eventName = "France trip",
        currentPersonId = person1.id,
        currentPersonName = person1.name,
        debts = persistentListOf(
            DebtShortUiModel(
                personId = person1.id,
                personName = person1.name,
                currencyCode = "EUR",
                currencyName = "Euro",
                amount = "100"
            ),
            DebtShortUiModel(
                personId = person2.id,
                personName = person2.name,
                currencyCode = "EUR",
                currencyName = "Euro",
                amount = "150"
            )
        ).takeIf { withDebts } ?: persistentListOf(),
        expenses = persistentListOf(
            Expense(
                expenseId = 1,
                serverId = "11",
                currency = Currency(
                    id = 1,
                    serverId = "11",
                    code = "RUB",
                    name = "Russian Ruble",
                    rate = 1.toBigDecimal(),
                ),
                expenseType = ExpenseType.Spending,
                person = person1,
                subjectExpenseSplitWithPersons = listOf(
                    ExpenseSplitWithPerson(
                        expenseSplitId = 1,
                        expenseId = 1,
                        person = person1,
                        originalAmount = 100.toBigDecimal(),
                        exchangedAmount = 100.toBigDecimal(),
                    ),
                    ExpenseSplitWithPerson(
                        expenseSplitId = 2,
                        expenseId = 1,
                        person = person2,
                        originalAmount = 150.333.toBigDecimal(),
                        exchangedAmount = 100.toBigDecimal(),
                    )
                ),
                isCustomRate = false,
                timestamp = Clock.System.now(),
                description = "Lunch",
            ).toUiModel(primaryCurrencyName = "EUR", currentPersonId = person1.id),
            Expense(
                expenseId = 2,
                serverId = "12",
                currency = Currency(
                    id = 2,
                    serverId = "11",
                    code = "USD",
                    name = "US Dollar",
                    rate = 1.toBigDecimal(),
                ),
                expenseType = ExpenseType.Replenishment,
                person = person2,
                subjectExpenseSplitWithPersons = listOf(
                    ExpenseSplitWithPerson(
                        expenseSplitId = 4,
                        expenseId = 2,
                        person = person2,
                        originalAmount = 132423423.toBigDecimal(),
                        exchangedAmount = 132423423.toBigDecimal(),
                    )
                ),
                isCustomRate = false,
                timestamp = Clock.System.now(),
                description = "Dinner and some text",
            ).toUiModel(primaryCurrencyName = "EUR", currentPersonId = person1.id)
        ),
        isRefreshing = false
    )
}
