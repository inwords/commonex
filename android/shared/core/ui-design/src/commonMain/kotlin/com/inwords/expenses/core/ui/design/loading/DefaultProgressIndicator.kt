package com.inwords.expenses.core.ui.design.loading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inwords.expenses.core.ui.design.preview.ComponentPreviews
import com.inwords.expenses.core.ui.design.theme.CommonExTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DefaultProgressIndicator(modifier: Modifier = Modifier) {
    ContainedLoadingIndicator(modifier = modifier)
}

@ComponentPreviews
@Composable
private fun DefaultProgressIndicatorPreview() {
    CommonExTheme {
        Surface {
            Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                DefaultProgressIndicator()
            }
        }
    }
}
