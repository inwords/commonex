package ru.commonex.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import expenses.shared.feature.events.generated.resources.Res
import expenses.shared.feature.events.generated.resources.events_delete_everywhere
import expenses.shared.feature.events.generated.resources.events_delete_local_only
import expenses.shared.feature.events.generated.resources.events_event_deleted
import expenses.shared.feature.events.generated.resources.events_keep_event
import org.jetbrains.compose.resources.getString

internal class LocalEventsScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    fun waitUntilLoaded(): LocalEventsScreen {
        waitForElementWithTag("events_create")
        return this
    }

    context(rule: ComposeTestRule)
    fun clickCreateEvent(): CreateEventScreen {
        rule.onNodeWithTag("events_create").performClick()
        return CreateEventScreen()
    }

    context(rule: ComposeTestRule)
    fun clickJoinEvent(): JoinInvitationScreen {
        rule.onNodeWithTag("events_join").performClick()
        return JoinInvitationScreen()
    }

    context(rule: ComposeTestRule)
    fun clickEvent(eventName: String): ChoosePersonScreen {
        waitForElementWithText(eventName)
        rule.onNodeWithText(eventName).performClick()
        return ChoosePersonScreen()
    }

    context(rule: ComposeTestRule)
    fun swipeToRevealActions(eventName: String): LocalEventsScreen {
        waitForElementWithText(eventName)
        rule.onNodeWithText(eventName).performTouchInput { swipeLeft() }
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun clickDeleteEverywhere(): DeleteEventDialogScreen {
        val deleteEverywhereLabel = getString(Res.string.events_delete_everywhere)
        waitForElementWithText(deleteEverywhereLabel)
        rule.onNodeWithText(deleteEverywhereLabel).performClick()
        return DeleteEventDialogScreen()
    }

    context(rule: ComposeTestRule)
    suspend fun clickDeleteLocalOnly(): LocalEventsScreen {
        val deleteLocalOnlyLabel = getString(Res.string.events_delete_local_only)
        waitForElementWithText(deleteLocalOnlyLabel)
        rule.onNodeWithText(deleteLocalOnlyLabel).performClick()
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun clickKeepEvent(): LocalEventsScreen {
        val keepEventLabel = getString(Res.string.events_keep_event)
        waitForElementWithText(keepEventLabel)
        rule.onNodeWithText(keepEventLabel).performClick()
        return this
    }

    context(rule: ComposeTestRule)
    fun swipeBack(eventName: String): LocalEventsScreen {
        waitForElementWithText(eventName)
        rule.onNodeWithText(eventName).performTouchInput { swipeRight() }
        return this
    }

    context(rule: ComposeTestRule)
    fun assertEventExists(eventName: String): LocalEventsScreen {
        waitForElementWithText(eventName)
        assertElementWithTextExists(eventName)
        return this
    }

    context(rule: ComposeTestRule)
    fun assertEventNotExists(eventName: String): LocalEventsScreen {
        waitForElementWithTextDoesNotExist(eventName)
        return this
    }

    context(rule: ComposeTestRule)
    fun assertWelcomeVisible(): LocalEventsScreen {
        waitForElementWithTag("events_welcome")
        rule.onNodeWithTag("events_welcome").assertIsDisplayed()
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun assertEventDeletedSnackbar(eventName: String): LocalEventsScreen {
        val message = getString(Res.string.events_event_deleted, eventName)
        waitForElementWithText(message)
        return this
    }

}
