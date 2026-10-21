package ru.commonex.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import expenses.shared.feature.menu.generated.resources.Res
import expenses.shared.feature.menu.generated.resources.menu_invite_back
import org.jetbrains.compose.resources.getString

internal class InviteEventScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    fun waitUntilLoaded(eventName: String): InviteEventScreen {
        waitForElementWithTag("event_invite")
        waitForElementWithText(eventName)
        rule.onNodeWithTag("event_invite").assertIsDisplayed()
        rule.onNode(hasText(eventName) and hasAnyAncestor(hasTestTag("event_invite"))).assertIsDisplayed()
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun goBack(): ExpensesScreen {
        val backLabel = getString(Res.string.menu_invite_back)
        rule.onNodeWithContentDescription(backLabel).performClick()
        return ExpensesScreen()
    }
}
