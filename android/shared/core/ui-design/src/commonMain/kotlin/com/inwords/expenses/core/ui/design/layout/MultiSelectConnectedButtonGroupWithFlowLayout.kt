package com.inwords.expenses.core.ui.design.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.preview.ComponentPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme

data class ToggleButtonOption<T>(
    val text: String,
    val checked: Boolean,
    val payload: T,
    val testTag: String? = null,
)

@Composable
fun <T> MultiSelectConnectedButtonGroupWithFlowLayout(
    options: List<ToggleButtonOption<T>>,
    onCheckedChange: (index: Int, checked: Boolean, payload: T) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            TonalToggleButton(
                modifier = if (option.testTag == null) {
                    Modifier
                } else {
                    Modifier.testTag(option.testTag)
                },
                checked = option.checked,
                onCheckedChange = { onCheckedChange(index, it, option.payload) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { Text(text = option.text) }
        }
    }
}

@ComponentPreviews
@Composable
private fun MultiSelectConnectedButtonGroupWithFlowLayoutPreview() {
    CommonExTheme {
        Surface {
            MultiSelectConnectedButtonGroupWithFlowLayout(
                modifier = Modifier.padding(16.dp),
                options = listOf(
                    ToggleButtonOption(text = "Alex", checked = true, payload = Unit),
                    ToggleButtonOption(text = "Sam", checked = true, payload = Unit),
                    ToggleButtonOption(text = "Jo", checked = false, payload = Unit),
                ),
                onCheckedChange = { _, _, _ -> },
            )
        }
    }
}
