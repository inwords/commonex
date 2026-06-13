package com.inwords.expenses.core.navigation

import androidx.navigation3.runtime.NavBackStack
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultNavigationControllerTest {

    @Test
    fun popBackStack_keepsRootDestination() {
        val backStack = NavBackStack<Destination>(RootDestination)
        val controller = DefaultNavigationController()

        controller.attachTo(backStack)
        controller.popBackStack()

        assertEquals(listOf(RootDestination), backStack.toList())
    }

    @Test
    fun popBackStackToMissingDestination_doesNotDrainBackStack() {
        val backStack = NavBackStack<Destination>(RootDestination, DetailsDestination)
        val controller = DefaultNavigationController()

        controller.attachTo(backStack)
        controller.popBackStack(toDestination = MissingDestination, inclusive = false)

        assertEquals(listOf(RootDestination, DetailsDestination), backStack.toList())
    }

    @Test
    fun popBackStackToRootInclusive_keepsRootDestination() {
        val backStack = NavBackStack<Destination>(RootDestination, DetailsDestination)
        val controller = DefaultNavigationController()

        controller.attachTo(backStack)
        controller.popBackStack(toDestination = RootDestination, inclusive = true)

        assertEquals(listOf(RootDestination), backStack.toList())
    }
}

private data object RootDestination : Destination

private data object DetailsDestination : Destination

private data object MissingDestination : Destination
