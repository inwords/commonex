package com.inwords.expenses.feature.expenses.ui.debts_list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.inwords.expenses.core.ui.design.appbar.TopAppBarWithNavIconAndText
import com.inwords.expenses.core.ui.design.loading.DefaultProgressIndicator
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.feature.expenses.ui.common.DebtReplenishmentButton
import com.inwords.expenses.feature.expenses.ui.common.DebtShortUiModel
import com.inwords.expenses.feature.expenses.ui.debts_list.DebtsListPaneUiModel.DebtorUiModel
import com.inwords.expenses.feature.expenses.ui.debts_list.DebtsListPaneUiModel.PersonUiModel
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.common_back
import expenses.shared.feature.expenses.generated.resources.common_error
import expenses.shared.feature.expenses.generated.resources.expenses_add_context
import expenses.shared.feature.expenses.generated.resources.expenses_all_settled
import expenses.shared.feature.expenses.generated.resources.expenses_back_to_group
import expenses.shared.feature.expenses.generated.resources.expenses_balances_hint
import expenses.shared.feature.expenses.generated.resources.expenses_balances_title
import expenses.shared.feature.expenses.generated.resources.expenses_debtor_owes
import expenses.shared.feature.expenses.generated.resources.expenses_no_outstanding_repayments
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DebtsListPane(
    onReplenishmentClick: (debtor: PersonUiModel, creditor: DebtShortUiModel) -> Unit,
    onNavIconClicked: () -> Unit,
    state: SimpleScreenState<DebtsListPaneUiModel>,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBarWithNavIconAndText(
                onNavIconClicked = onNavIconClicked,
                title = stringResource(Res.string.expenses_balances_title),
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(Res.string.common_back),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(paddingValues)
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (state) {
                is SimpleScreenState.Success -> DebtsListPaneSuccess(
                    state = state.data,
                    onReplenishmentClick = onReplenishmentClick,
                    onNavIconClicked = onNavIconClicked,
                )

                SimpleScreenState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    DefaultProgressIndicator()
                }

                SimpleScreenState.Error -> Text(
                    modifier = Modifier.padding(24.dp),
                    text = stringResource(Res.string.common_error),
                )

                SimpleScreenState.Empty -> SettledBalances(onNavIconClicked = onNavIconClicked)
            }
        }
    }
}

@Composable
private fun DebtsListPaneSuccess(
    state: DebtsListPaneUiModel,
    onReplenishmentClick: (debtor: PersonUiModel, creditor: DebtShortUiModel) -> Unit,
    onNavIconClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val showTwoColumns = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) &&
        LocalDensity.current.fontScale < 1.5f
    LazyVerticalGrid(
        modifier = modifier
            .widthIn(max = if (showTwoColumns) 960.dp else 560.dp)
            .fillMaxSize()
            .testTag("debts_list"),
        columns = GridCells.Fixed(if (showTwoColumns) 2 else 1),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "event", contentType = "heading", span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    modifier = Modifier.semantics { heading() },
                    text = state.eventName,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(Res.string.expenses_add_context, state.primaryCurrencyCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.debtors.isNotEmpty()) {
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = stringResource(Res.string.expenses_balances_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.debtors.isEmpty()) {
            item(key = "settled", contentType = "empty", span = { GridItemSpan(maxLineSpan) }) {
                SettledBalances(onNavIconClicked = onNavIconClicked)
            }
        } else {
            items(items = state.debtors, key = { it.person.personId }, contentType = { "debtor" }) { debtor ->
                DebtorSection(debtor = debtor, onReplenishmentClick = onReplenishmentClick)
            }
        }
    }
}

@Composable
private fun DebtorSection(
    debtor: DebtorUiModel,
    onReplenishmentClick: (debtor: PersonUiModel, creditor: DebtShortUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag("debts_list_debtor"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .size(32.dp)
                    .clearAndSetSemantics {},
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = debtor.person.personInitial, style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                modifier = Modifier.semantics { heading() },
                text = stringResource(Res.string.expenses_debtor_owes, debtor.person.personName),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                debtor.creditors.forEachIndexed { index, debt ->
                    if (index != 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    DebtReplenishmentButton(
                        modifier = Modifier.testTag("debts_list_debt_button"),
                        debt = debt,
                        debtorName = debtor.person.personName,
                        onClick = { onReplenishmentClick(debtor.person, debt) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettledBalances(
    onNavIconClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("debts_list_settled"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Outlined.Check, contentDescription = null)
                }
            }
            Text(
                modifier = Modifier.semantics { heading() },
                text = stringResource(Res.string.expenses_all_settled),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(Res.string.expenses_no_outstanding_repayments),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onNavIconClicked,
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                Text(text = stringResource(Res.string.expenses_back_to_group))
            }
        }
    }
}

@Preview
@Composable
private fun DebtsListPanePreview() {
    CommonExTheme {
        DebtsListPane(
            state = SimpleScreenState.Success(mockDebtsListPaneUiModel()),
            onReplenishmentClick = { _, _ -> },
            onNavIconClicked = {},
        )
    }
}

@Preview(widthDp = 840)
@Composable
private fun DebtsListPaneExpandedPreview() {
    DebtsListPanePreview()
}

@Preview(fontScale = 2f, widthDp = 320)
@Composable
private fun DebtsListPaneLargeTextPreview() {
    DebtsListPanePreview()
}

@Preview
@Composable
private fun DebtsListPaneSettledPreview() {
    CommonExTheme {
        DebtsListPane(
            state = SimpleScreenState.Success(mockDebtsListPaneUiModel().copy(debtors = persistentListOf())),
            onReplenishmentClick = { _, _ -> },
            onNavIconClicked = {},
        )
    }
}

@Preview
@Composable
private fun DebtorSectionPreview() {
    CommonExTheme {
        DebtorSection(debtor = mockDebtsListPaneUiModel().debtors.first(), onReplenishmentClick = { _, _ -> })
    }
}

@Preview
@Composable
private fun SettledBalancesPreview() {
    CommonExTheme { SettledBalances(onNavIconClicked = {}) }
}

private fun mockDebtsListPaneUiModel(): DebtsListPaneUiModel {
    return DebtsListPaneUiModel(
        eventName = "Weekend in Lisbon",
        primaryCurrencyCode = "EUR",
        debtors = persistentListOf(
            DebtorUiModel(
                person = PersonUiModel(personId = 1, personName = "Sam"),
                creditors = persistentListOf(
                    DebtShortUiModel(personId = 2, personName = "Alex", currencyCode = "EUR", currencyName = "Euro", amount = "42.50"),
                    DebtShortUiModel(personId = 3, personName = "Jo", currencyCode = "EUR", currencyName = "Euro", amount = "20.47"),
                ),
            ),
            DebtorUiModel(
                person = PersonUiModel(personId = 3, personName = "Jo"),
                creditors = persistentListOf(
                    DebtShortUiModel(personId = 2, personName = "Alex", currencyCode = "EUR", currencyName = "Euro", amount = "18.00"),
                ),
            ),
        ),
    )
}
