package com.inwords.expenses.feature.expenses.ui.add

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_add_share_each
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExpenseEqualShareSummary(
    amount: String?,
    currencyCode: String,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = amount,
        modifier = modifier.fillMaxWidth(),
        contentKey = { it != null },
        transitionSpec = {
            fadeIn(motionScheme.fastEffectsSpec())
                .togetherWith(fadeOut(motionScheme.fastEffectsSpec()))
                .using(SizeTransform { _, _ -> motionScheme.defaultSpatialSpec() })
        },
    ) { shareAmount ->
        if (shareAmount != null) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = stringResource(Res.string.expenses_add_share_each, shareAmount, currencyCode),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(widthDp = 360)
@Preview(name = "Large text", widthDp = 320, fontScale = 2f)
@Composable
private fun ExpenseEqualShareSummaryPreview() {
    CommonExTheme {
        ExpenseEqualShareSummary(amount = "20", currencyCode = "EUR")
    }
}
