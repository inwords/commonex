package ru.commonex.screens

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

internal class DeleteEventDialogScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    fun confirmDeletion(): LocalEventsScreen {
        rule
            .onNodeWithTag("delete_event_dialog_delete_button")
            .performClick()
        waitForElementWithTagDoesNotExist("delete_event_dialog_delete_button")
        return LocalEventsScreen().waitUntilLoaded()
    }

    context(rule: ComposeTestRule)
    fun keepEvent(): LocalEventsScreen {
        rule
            .onNodeWithTag("delete_event_dialog_keep_button")
            .performClick()
        waitForElementWithTagDoesNotExist("delete_event_dialog_keep_button")
        return LocalEventsScreen().waitUntilLoaded()
    }
}
