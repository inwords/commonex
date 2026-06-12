package com.inwords.expenses.core.navigation

import androidx.navigation3.runtime.NavBackStack

internal class DefaultNavigationController() : AttachableNavigationController {

    private var backStack: NavBackStack<Destination>? = null

    override fun attachTo(navBackStack: NavBackStack<Destination>) {
        backStack = navBackStack
    }

    override fun navigateTo(destination: Destination) {
        getBackStack().add(destination)
    }

    override fun navigateTo(destination: Destination, popUpTo: Destination, launchSingleTop: Boolean) {
        getBackStack().apply {
            popBackStack(toDestination = popUpTo, inclusive = false)

            val existingDestinationIndex = lastIndexOf(destination)
            if (launchSingleTop && existingDestinationIndex != -1) {
                if (existingDestinationIndex != lastIndex) {
                    val top = removeAt(existingDestinationIndex)
                    add(top)
                }
            } else {
                add(destination)
            }
        }
    }

    override fun popBackStack() {
        getBackStack().apply {
            if (size > 1) {
                removeAt(lastIndex)
            }
        }
    }

    override fun popBackStack(toDestination: Destination, inclusive: Boolean) {
        getBackStack().apply {
            while (isNotEmpty()) {
                val top = last()
                if (top == toDestination) {
                    if (inclusive) {
                        removeAt(lastIndex)
                    }
                    break
                } else {
                    removeAt(lastIndex)
                }
            }
        }
    }

    private fun getBackStack(): NavBackStack<Destination> {
        return requireNotNull(backStack) { "NavigationController is not attached to NavBackStack" }
    }

}
