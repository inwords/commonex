package ru.commonex.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement

internal class AddParticipantsToEventScreen : BaseScreen() {
    context(rule: ComposeTestRule)
    fun enterParticipantName(index: Int, name: String): AddParticipantsToEventScreen {
        rule.onNodeWithTag("events_add_person_name_$index").performScrollTo().performTextReplacement(name)
        return this
    }

    context(rule: ComposeTestRule)
    fun appendParticipant(): AddParticipantsToEventScreen {
        rule.onNodeWithTag("events_add_person").performScrollTo().performClick()
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyConfirmDisabled(): AddParticipantsToEventScreen {
        rule.onNodeWithTag("events_add_people_confirm").assertIsNotEnabled()
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyConfirmEnabled(): AddParticipantsToEventScreen {
        rule.onNodeWithTag("events_add_people_confirm").assertIsEnabled()
        return this
    }

    context(rule: ComposeTestRule)
    fun clickContinueButton(): ExpensesScreen {
        rule.onNodeWithTag("events_add_people_confirm").performClick()
        return ExpensesScreen()
    }
}
