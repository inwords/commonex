package com.inwords.expenses.integration.base

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.inwords.expenses.core.navigation.DeeplinkProvider
import com.inwords.expenses.core.ui.design.theme.CommonExTheme
import platform.UIKit.UIViewController

private val deeplinkProvider = DeeplinkProvider()

fun MainViewController(): UIViewController = ComposeUIViewController {
    CommonExTheme {
        MainNavHost(modifier = Modifier.fillMaxSize(), deeplinkProvider = deeplinkProvider)
    }
}

fun supplyDeeplink(deeplink: String) {
    if (deeplink.isBlank()) return
    deeplinkProvider.supplyDeeplink(deeplink)
}
