package com.inwords.expenses.core.ui.design.layout

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.button.ButtonWithIconAndText
import com.inwords.expenses.core.ui.design.preview.ComponentPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme

@Composable
fun AdaptiveButtonsRow(
    horizontalSpacing: Dp,
    verticalSpacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        if (measurables.isEmpty()) {
            return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        }

        val horizontalGap = horizontalSpacing.roundToPx()
        val verticalGap = verticalSpacing.roundToPx()
        val inlineWidth = ((constraints.maxWidth - horizontalGap * measurables.lastIndex) / measurables.size).coerceAtLeast(0)
        val isInline = measurables.all { it.maxIntrinsicWidth(Constraints.Infinity) <= inlineWidth }
        val buttonWidth = if (isInline) inlineWidth else constraints.maxWidth
        val buttonConstraints = constraints.copy(minWidth = buttonWidth, maxWidth = buttonWidth, minHeight = 0)
        val buttons = measurables.map { it.measure(buttonConstraints) }
        val height = if (isInline) {
            buttons.maxOf { it.height }
        } else {
            buttons.sumOf { it.height } + verticalGap * buttons.lastIndex
        }

        layout(constraints.maxWidth, constraints.constrainHeight(height)) {
            var y = 0
            buttons.forEachIndexed { index, button ->
                if (isInline) {
                    button.placeRelative(x = index * (buttonWidth + horizontalGap), y = (height - button.height) / 2)
                } else {
                    button.placeRelative(x = 0, y = y)
                    y += button.height + verticalGap
                }
            }
        }
    }
}

@ComponentPreviews
@Composable
private fun AdaptiveButtonsRowPreview() {
    CommonExTheme {
        Surface {
            AdaptiveButtonsRow(
                modifier = Modifier.padding(16.dp),
                horizontalSpacing = 12.dp,
                verticalSpacing = 8.dp,
            ) {
                ButtonWithIconAndText(onClick = {}, text = "Create a group", imageVector = Icons.Outlined.Add)
                TextButton(onClick = {}) { Text("Join a group") }
            }
        }
    }
}
