package com.inwords.expenses.core.ui.design.button

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.preview.ComponentPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ButtonWithIconAndText(
    onClick: () -> Unit,
    text: String,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
    minHeight: Dp = ButtonDefaults.MinHeight,
) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        enabled = enabled,
        modifier = modifier.heightIn(min = minHeight),
        contentPadding = ButtonDefaults.contentPaddingFor(minHeight),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(minHeight)),
        )
        Spacer(modifier = Modifier.size(ButtonDefaults.iconSpacingFor(minHeight)))
        Text(text = text, style = ButtonDefaults.textStyleFor(minHeight))
    }
}

@Preview(name = "Disabled", widthDp = 360)
@Composable
private fun ButtonWithIconAndTextDisabledPreview() {
    CommonExTheme {
        Surface {
            ButtonWithIconAndText(
                modifier = Modifier.padding(16.dp),
                onClick = {},
                text = "Add expense",
                imageVector = Icons.Outlined.Add,
                enabled = false,
            )
        }
    }
}


@ComponentPreviews
@Composable
private fun ButtonWithIconAndTextEnabledPreview() {
    CommonExTheme {
        Surface {
            ButtonWithIconAndText(
                modifier = Modifier.padding(16.dp),
                onClick = {},
                text = "Add expense",
                imageVector = Icons.Outlined.Add,
                enabled = true,
            )
        }
    }
}

@Preview(name = "Medium", widthDp = 360)
@Composable
private fun ButtonWithIconAndTextMediumEnabledPreview() {
    CommonExTheme {
        Surface {
            ButtonWithIconAndText(
                modifier = Modifier.padding(16.dp),
                onClick = {},
                text = "Add expense",
                imageVector = Icons.Outlined.Add,
                enabled = true,
                minHeight = ButtonDefaults.MediumContainerHeight
            )
        }
    }
}
