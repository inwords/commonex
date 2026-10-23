package com.inwords.expenses.core.ui.design.appbar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.preview.ComponentPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme

@Composable
fun TopAppBarWithText() {
    TopAppBar(
        title = {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp),
                text = "CommonEx",
                textAlign = TextAlign.Center,
            )
        }
    )
}

@ComponentPreviews
@Composable
private fun TopAppBarWithTextPreview() {
    CommonExTheme {
        TopAppBarWithText()
    }
}
