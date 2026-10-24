package com.inwords.expenses.core.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun rememberNavigationController(): AttachableNavigationController {
    return viewModel<NavigationViewModel>(factory = viewModelFactory {
        initializer { NavigationViewModel() }
    }).navigationController
}

private class NavigationViewModel : ViewModel() {

    val navigationController = DefaultNavigationController()
}
