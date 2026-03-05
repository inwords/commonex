package com.inwords.expenses.core.ui.design.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

data class ToggleButtonOption<T>(
    val text: String,
    val checked: Boolean,
    val payload: T,
    val testTag: String? = null,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true)
@Composable
private fun MultiSelectConnectedButtonGroupWithFlowLayoutPreview() {
    val options = listOf(
        ToggleButtonOption(text = "Work", checked = false, payload = Unit),
        ToggleButtonOption(text = "Restaurant", checked = true, payload = Unit),
        ToggleButtonOption(text = "Coffee", checked = false, payload = Unit),
        ToggleButtonOption(text = "Search", checked = true, payload = Unit),
        ToggleButtonOption(text = "Home", checked = false, payload = Unit),
    )
    MultiSelectConnectedButtonGroupWithFlowLayout(
        options = options,
        onCheckedChange = { index, checked, _ ->
            options[index].copy(checked = checked)
        }
    )
}
