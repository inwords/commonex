package com.inwords.expenses.core.navigation

import androidx.navigation3.runtime.NavBackStack
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultNavigationControllerTest {

    private val controller = DefaultNavigationController()

    @Test
    fun `popBackStack removes the top destination`() {
        val backStack = NavBackStack<Destination>(Root, Detail)
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
        val backStack = NavBackStack<Destination>(Root, Detail)
        controller.attachTo(backStack)

        controller.popBackStack(toDestination = Other, inclusive = true)

        assertEquals(listOf<Destination>(Root, Detail), backStack.toList())
    }

    @Test
    fun `popBackStack to root preserves it even when inclusive`() {
        val backStack = NavBackStack<Destination>(Root, Detail)
        controller.attachTo(backStack)

        controller.popBackStack(toDestination = Root, inclusive = true)

        assertEquals(listOf<Destination>(Root), backStack.toList())
    }

    @Test
    fun `popBackStack inclusive removes the target and destinations above it`() {
        val backStack = NavBackStack<Destination>(Root, Detail, Other)
        controller.attachTo(backStack)

        controller.popBackStack(toDestination = Detail, inclusive = true)

        assertEquals(listOf<Destination>(Root), backStack.toList())
    }

    @Test
    fun `popBackStack noninclusive keeps the most recent matching destination`() {
        val backStack = NavBackStack<Destination>(Root, Detail, Other, Detail, Other)
        controller.attachTo(backStack)

        controller.popBackStack(toDestination = Detail, inclusive = false)

        assertEquals(listOf<Destination>(Root, Detail, Other, Detail), backStack.toList())
    }

    private data object Root : Destination
    private data object Detail : Destination
    private data object Other : Destination
}
