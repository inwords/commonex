package com.inwords.expenses.feature.expenses.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.preview.ComponentPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme

@Composable
internal fun ExpenseSettingsRow(
    label: String,
    value: String,
    imageVector: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    supportingContent: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 80.dp)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
            )
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                )
                supportingContent?.invoke()
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        }
    }
}

@ComponentPreviews
@Composable
private fun ExpenseSettingsRowPreview() {
    CommonExTheme {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            ExpenseSettingsRow(
                label = "Paid by",
                value = "Alex",
                imageVector = Icons.Outlined.Person,
                enabled = true,
                onClick = {},
                supportingContent = null,
            )
        }
    }
}

@ComponentPreviews
@Composable
private fun ExpenseSettingsRowWithSharePreview() {
    CommonExTheme {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            ExpenseSettingsRow(
                label = "Split",
                value = "Equally · 3 people",
                imageVector = Icons.AutoMirrored.Outlined.List,
                enabled = true,
                onClick = {},
                supportingContent = { ExpenseEqualShareSummary(amount = "20", currencyCode = "EUR") },
            )
        }
    }
}
