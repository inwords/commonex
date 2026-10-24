package com.inwords.expenses.core.navigation

import androidx.navigation3.runtime.NavBackStack
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultNavigationControllerTest {

    private val controller = DefaultNavigationController()

    @Test
    fun `navigation appends to the currently attached stack without changing an older stack`() {
        val oldStack = NavBackStack<Destination>(Root)
        controller.attachTo(oldStack)
        controller.navigateTo(Detail)
        val restoredStack = NavBackStack(Root, Other)
        controller.attachTo(restoredStack)

        controller.navigateTo(Detail)

        assertEquals(listOf(Root, Detail), oldStack.toList())
        assertEquals(listOf(Root, Other, Detail), restoredStack.toList())
    }

    @Test
    fun `navigation removes intervening routes before adding a new single top destination`() {
        val backStack = NavBackStack(Root, Detail, Other, Detail)
        controller.attachTo(backStack)

        controller.navigateTo(Other, popUpTo = Root, launchSingleTop = true)

        assertEquals(listOf(Root, Other), backStack.toList())
    }

    @Test
    fun `single top navigation moves the existing destination above preserved routes`() {
        val backStack = NavBackStack(Root, Detail, Other)
        controller.attachTo(backStack)

        controller.navigateTo(Detail, popUpTo = Other, launchSingleTop = true)

        assertEquals(listOf(Root, Other, Detail), backStack.toList())
    }

    @Test
    fun `single top navigation leaves an already visible destination unduplicated`() {
        val backStack = NavBackStack(Root, Detail)
        controller.attachTo(backStack)

        controller.navigateTo(Detail, popUpTo = Detail, launchSingleTop = true)

        assertEquals(listOf(Root, Detail), backStack.toList())
    }

    @Test
    fun `navigation permits repeated destinations when single top is disabled`() {
        val backStack = NavBackStack(Root, Detail)
        controller.attachTo(backStack)

        controller.navigateTo(Detail, popUpTo = Detail, launchSingleTop = false)

        assertEquals(listOf(Root, Detail, Detail), backStack.toList())
    }

    @Test
    fun `popBackStack removes the top destination`() {
        val backStack = NavBackStack(Root, Detail)
        controller.attachTo(backStack)

        controller.popBackStack()

        assertEquals(listOf<Destination>(Root), backStack.toList())
    }

    @Test
    fun `popBackStack preserves the root destination`() {
        val backStack = NavBackStack<Destination>(Root)
        controller.attachTo(backStack)

        controller.popBackStack()

        assertEquals(listOf<Destination>(Root), backStack.toList())
    }

    @Test
    fun `popBackStack to a missing destination leaves the stack unchanged`() {
        val backStack = NavBackStack(Root, Detail)
        controller.attachTo(backStack)

        controller.popBackStack(toDestination = Other, inclusive = true)

        assertEquals(listOf(Root, Detail), backStack.toList())
    }

    @Test
    fun `popBackStack to root preserves it even when inclusive`() {
        val backStack = NavBackStack(Root, Detail)
        controller.attachTo(backStack)

        controller.popBackStack(toDestination = Root, inclusive = true)

        assertEquals(listOf<Destination>(Root), backStack.toList())
    }

    @Test
    fun `popBackStack inclusive removes the target and destinations above it`() {
        val backStack = NavBackStack(Root, Detail, Other)
        controller.attachTo(backStack)

        controller.popBackStack(toDestination = Detail, inclusive = true)

        assertEquals(listOf<Destination>(Root), backStack.toList())
    }

    @Test
    fun `popBackStack noninclusive keeps the most recent matching destination`() {
        val backStack = NavBackStack(Root, Detail, Other, Detail, Other)
        controller.attachTo(backStack)

        controller.popBackStack(toDestination = Detail, inclusive = false)

        assertEquals(listOf(Root, Detail, Other, Detail), backStack.toList())
    }

    private data object Root : Destination
    private data object Detail : Destination
    private data object Other : Destination
}
