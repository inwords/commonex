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

    private data object Root : Destination
    private data object Detail : Destination
}
