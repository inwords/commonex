package ru.commonex.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import expenses.shared.feature.menu.generated.resources.Res
import expenses.shared.feature.menu.generated.resources.menu_add_participants_action
import expenses.shared.feature.menu.generated.resources.menu_choose_person_action
import expenses.shared.feature.menu.generated.resources.menu_copy_action
import expenses.shared.feature.menu.generated.resources.menu_open_events_list
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class)
internal class MenuDialogScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    fun verifyEventName(eventName: String): MenuDialogScreen {
        waitForElementWithTag("menu_event_name")
        rule.onNode(
            hasTestTag("menu_event_name") and hasText(eventName) and hasAnyAncestor(hasTestTag("menu_options"))
        ).assertIsDisplayed()
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun openEventsList(): LocalEventsScreen {
        val openEventsLabel = getString(Res.string.menu_open_events_list)
        rule.onNode(hasText(openEventsLabel) and hasAnyAncestor(hasTestTag("menu_options"))).performClick()
        return LocalEventsScreen().waitUntilLoaded()
    }

    context(rule: ComposeTestRule)
    suspend fun chooseParticipant(): ChoosePersonScreen {
        val choosePersonLabel = getString(Res.string.menu_choose_person_action)
        rule.onNode(hasText(choosePersonLabel) and hasAnyAncestor(hasTestTag("menu_options"))).performClick()
        return ChoosePersonScreen()
    }

    context(rule: ComposeTestRule)
    suspend fun addParticipant(): AddParticipantsToEventScreen {
        val addParticipantsLabel = getString(Res.string.menu_add_participants_action)
        rule.onNode(hasText(addParticipantsLabel) and hasAnyAncestor(hasTestTag("menu_options"))).performClick()
        return AddParticipantsToEventScreen()
    }

    context(rule: ComposeTestRule)
    suspend fun waitUntilCopyEnabled(timeout: Long = 10000): MenuDialogScreen {
        val copyLabel = getString(Res.string.menu_copy_action)
        rule.waitUntilAtLeastOneExists(hasText(copyLabel) and isEnabled() and hasAnyAncestor(hasTestTag("menu_options")), timeout)
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun clickCopyShareLink(): MenuDialogScreen {
        val copyLabel = getString(Res.string.menu_copy_action)
        rule.onNode(hasText(copyLabel) and hasAnyAncestor(hasTestTag("menu_options"))).performClick()
        return this
    }
}
