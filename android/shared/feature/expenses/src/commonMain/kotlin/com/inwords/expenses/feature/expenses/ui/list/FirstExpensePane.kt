package com.inwords.expenses.feature.expenses.ui.list

import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.preview.ScreenPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_event_options
import expenses.shared.feature.expenses.generated.resources.expenses_your_events
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FirstExpensePane(
    state: ExpensesPaneUiModel.Expenses,
    onBannerDismissClick: () -> Unit,
    appStoreUrl: String,
    onMenuClick: () -> Unit,
    onOpenEventsListClick: () -> Unit,
    onInviteClick: () -> Unit,
    onAddExpenseClick: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.testTag(ExpensesPaneTags.TOP_APP_BAR),
                navigationIcon = {
                    IconButton(
                        onClick = onOpenEventsListClick,
                        modifier = Modifier.testTag("expenses_events_list_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(Res.string.expenses_your_events),
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(Res.string.expenses_your_events),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.testTag(ExpensesPaneTags.MENU_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = stringResource(Res.string.expenses_event_options),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(paddingValues)
                .padding(paddingValues),
            state = pullToRefreshState,
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = state.isRefreshing,
                    state = pullToRefreshState,
                )
            },
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                FirstExpensePrompt(
                    banner = state.banner,
                    onBannerDismissClick = onBannerDismissClick,
                    appStoreUrl = appStoreUrl,
                    eventName = state.eventName,
                    currentPersonName = state.currentPersonName,
                    personCount = state.personCount,
                    currencyCode = state.currencyCode,
                    onAddExpenseClick = onAddExpenseClick,
                    onInviteClick = onInviteClick,
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight),
                )
            }
        }
    }
}

@ScreenPreviews
@Preview(name = "Expanded", widthDp = 840, heightDp = 800)
@Composable
private fun FirstExpensePanePreview() {
    CommonExTheme {
        FirstExpensePane(
            onBannerDismissClick = {},
            appStoreUrl = "https://commonex.ru",
            state = mockExpensesPaneUiModel(withDebts = false).copy(
                dayChips = persistentListOf(),
                daySections = persistentListOf(),
            ),
            onMenuClick = {},
            onOpenEventsListClick = {},
            onInviteClick = {},
            onAddExpenseClick = {},
            onRefresh = {},
        )
    }
}
