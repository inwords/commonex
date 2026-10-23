package com.inwords.expenses.core.ui.design.preview

import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Phone", widthDp = 360, heightDp = 800)
@Preview(name = "Narrow / large text", widthDp = 320, heightDp = 800, fontScale = 2f)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class ScreenPreviews
