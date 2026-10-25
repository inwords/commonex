package com.inwords.expenses.feature.expenses.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.preview.ComponentPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import com.inwords.expenses.feature.banner.domain.Banner
import com.inwords.expenses.feature.banner.ui.BannerContent
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_first_action
import expenses.shared.feature.expenses.generated.resources.expenses_first_details
import expenses.shared.feature.expenses.generated.resources.expenses_first_expense_hint
import expenses.shared.feature.expenses.generated.resources.expenses_first_identity
import expenses.shared.feature.expenses.generated.resources.expenses_first_invite
import expenses.shared.feature.expenses.generated.resources.expenses_first_invite_hint
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FirstExpensePrompt(
    eventName: String,
    banner: Banner?,
    currentPersonName: String,
    personCount: Int,
    currencyCode: String,
    onBannerDismissClick: () -> Unit,
    appStoreUrl: String,
    onAddExpenseClick: () -> Unit,
    onInviteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .testTag(ExpensesPaneTags.EMPTY_STATE)
            .padding(vertical = 24.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = eventName,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = pluralStringResource(
                        resource = Res.plurals.expenses_first_details,
                        personCount, personCount, currencyCode
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(resource = Res.string.expenses_first_identity, currentPersonName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("expenses_current_person"),
            )
        }

        BannerContent(
            banner = banner,
            onDismissClick = onBannerDismissClick,
            appStoreUrl = appStoreUrl,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
        )
        Spacer(modifier = Modifier.height(32.dp))

        val inviteLabel = stringResource(Res.string.expenses_first_invite)
        val textMeasurer = rememberTextMeasurer()
        // Keep the shorter action title on one line before allowing two columns.
        val minimumTileWidth = with(LocalDensity.current) {
            textMeasurer.measure(inviteLabel, style = MaterialTheme.typography.titleLarge, softWrap = false).size.width.toDp()
        } + 32.dp
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            val isCompact = maxWidth < minimumTileWidth * 2 + 12.dp
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 2,
            ) {
                val tileModifier = if (isCompact) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                        .weight(1f)
                        .widthIn(min = minimumTileWidth)
                        .fillMaxRowHeight()
                }
                FirstExpenseActionCard(
                    onClick = onAddExpenseClick,
                    title = stringResource(Res.string.expenses_first_action),
                    description = stringResource(Res.string.expenses_first_expense_hint),
                    imageVector = Icons.Outlined.Add,
                    isEmphasized = true,
                    isCompact = isCompact,
                    modifier = tileModifier.testTag(ExpensesPaneTags.ADD_EXPENSE_BUTTON),
                )
                FirstExpenseActionCard(
                    onClick = onInviteClick,
                    title = inviteLabel,
                    description = stringResource(Res.string.expenses_first_invite_hint),
                    imageVector = Icons.Outlined.Share,
                    isEmphasized = false,
                    isCompact = isCompact,
                    modifier = tileModifier.testTag(ExpensesPaneTags.INVITE_BUTTON),
                )
            }
        }
    }
}

@Composable
private fun FirstExpenseActionCard(
    onClick: () -> Unit,
    title: String,
    description: String,
    imageVector: ImageVector,
    isEmphasized: Boolean,
    isCompact: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (isCompact) 0.dp else 240.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isEmphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = if (isEmphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 24.dp else 32.dp),
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isEmphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@ComponentPreviews
@Composable
private fun FirstExpensePromptPreview() {
    CommonExTheme {
        Surface {
            FirstExpensePrompt(
                eventName = "Weekend in Lisbon",
                banner = null,
                currentPersonName = "Alex",
                personCount = 3,
                currencyCode = "EUR",
                onBannerDismissClick = {},
                appStoreUrl = "https://commonex.ru",
                onAddExpenseClick = {},
                onInviteClick = {},
            )
        }
    }
}

@Preview(name = "First expense tile", widthDp = 184)
@Composable
private fun FirstExpenseActionCardPreview() {
    CommonExTheme {
        FirstExpenseActionCard(
            onClick = {},
            title = stringResource(Res.string.expenses_first_action),
            description = stringResource(Res.string.expenses_first_expense_hint),
            imageVector = Icons.Outlined.Add,
            isEmphasized = true,
            isCompact = false,
        )
    }
}
