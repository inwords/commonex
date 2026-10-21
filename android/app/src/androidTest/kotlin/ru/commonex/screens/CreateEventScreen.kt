package ru.commonex.screens

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput

internal class CreateEventScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    fun waitUntilLoaded(): CreateEventScreen {
        waitForElementWithTag("events_create_form")
        return this
    }

    context(rule: ComposeTestRule)
    fun enterEventName(name: String): CreateEventScreen {
        rule.onNodeWithTag("events_create_name").performTextInput(name)
        return this
    }

    context(rule: ComposeTestRule)
    fun openCurrencyPicker(): EventCurrencyPickerScreen {
        scrollToItem("currency")
        rule.onNodeWithTag("events_create_currency").performClick()
        return EventCurrencyPickerScreen()
    }

    context(rule: ComposeTestRule)
    fun enterOwnerName(name: String): CreateEventScreen {
        rule.onNodeWithTag("events_owner_name").performTextInput(name)
        return this
    }

    context(rule: ComposeTestRule)
    fun addParticipant(index: Int, name: String): CreateEventScreen {
        rule.onNodeWithTag("events_add_participant").performScrollTo().performClick()
        rule.onNodeWithTag("events_participant_$index").performTextInput(name)
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyDraft(eventName: String, ownerName: String, participantName: String): CreateEventScreen {
        scrollToItem("event")
        assertElementWithTextExists(eventName)
        scrollToItem("owner")
        assertElementWithTextExists(ownerName)
        scrollToItem("participant_0")
        assertElementWithTextExists(participantName)
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyCurrency(currencyCode: String): CreateEventScreen {
        scrollToItem("currency")
        rule.onNodeWithTag("events_create_currency").assert(hasText(currencyCode))
        return this
    }

    context(rule: ComposeTestRule)
    fun createEvent(): ExpensesScreen {
        scrollToItem("confirm")
        rule.onNodeWithTag("events_create_confirm").performClick()
        return ExpensesScreen()
    }

    context(rule: ComposeTestRule)
    private fun scrollToItem(key: String) {
        rule.onNodeWithTag("events_create_form").performScrollToKey(key)
    }
}
