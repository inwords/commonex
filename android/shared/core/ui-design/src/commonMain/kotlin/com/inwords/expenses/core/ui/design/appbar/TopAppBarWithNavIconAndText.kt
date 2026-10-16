package com.inwords.expenses.core.ui.design.appbar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun TopAppBarWithNavIconAndText(
    onNavIconClicked: () -> Unit,
    title: String,
    imageVector: ImageVector,
    contentDescription: String,
) {
    TopAppBar(
        title = {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 48.dp),
                text = title,
                textAlign = TextAlign.Center,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onNavIconClicked
            ) {
                Icon(imageVector = imageVector, contentDescription = contentDescription)
            }
        }
    )
}
