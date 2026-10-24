package com.inwords.expenses.core.ui.design.button

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.preview.ComponentPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TextButtonWithText(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val minHeight = ButtonDefaults.MediumContainerHeight
    TextButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        enabled = enabled,
        modifier = modifier.heightIn(min = minHeight),
        contentPadding = ButtonDefaults.contentPaddingFor(minHeight),
    ) {
        Text(text = text, style = ButtonDefaults.textStyleFor(minHeight))
    }
}

@Preview(name = "Disabled", widthDp = 360)
@Composable
private fun TextButtonWithTextDisabledPreview() {
    CommonExTheme {
        Surface {
            TextButtonWithText(
                modifier = Modifier.padding(16.dp),
                onClick = {},
                text = "Join a group",
                enabled = false,
            )
        }
    }
}

@ComponentPreviews
@Composable
private fun TextButtonWithTextEnabledPreview() {
    CommonExTheme {
        Surface {
            TextButtonWithText(
                modifier = Modifier.padding(16.dp),
                onClick = {},
                text = "Join a group",
                enabled = true,
            )
        }
    }
}
