package ru.commonex.screens

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput

internal class JoinEventScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    fun enterEventId(eventId: String): JoinEventScreen {
        rule.onNodeWithTag("events_join_id").performScrollTo().performTextInput(eventId)
        return this
    }

    context(rule: ComposeTestRule)
    fun enterAccessCode(accessCode: String): JoinEventScreen {
        rule.onNodeWithTag("events_join_access_code").performScrollTo().performTextInput(accessCode)
        return this
    }

    context(rule: ComposeTestRule)
    fun clickConfirmButton(): ChoosePersonScreen {
        rule.onNodeWithTag("events_join_confirm").performScrollTo().performClick()
        return ChoosePersonScreen()
    }

    context(rule: ComposeTestRule)
    fun joinEvent(eventId: String, accessCode: String): ChoosePersonScreen {
        enterEventId(eventId)
        enterAccessCode(accessCode)
        return clickConfirmButton()
    }
}
