package ru.commonex.screens

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import expenses.shared.feature.events.generated.resources.Res
import expenses.shared.feature.events.generated.resources.events_add_participant
import expenses.shared.feature.events.generated.resources.events_continue_button
import expenses.shared.feature.events.generated.resources.events_person_name_placeholder
import org.jetbrains.compose.resources.getString

internal class AddPersonsScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    suspend fun waitUntilLoaded(): AddPersonsScreen {
        val continueLabel = getString(Res.string.events_continue_button)
        waitForElementWithText(continueLabel)
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun enterOwnerName(name: String): AddPersonsScreen {
        val nameLabel = getString(Res.string.events_person_name_placeholder)
        rule.onNodeWithText(nameLabel).performTextInput(name)
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun addParticipant(name: String): AddPersonsScreen {
        val addParticipantLabel = getString(Res.string.events_add_participant)
        rule.onNodeWithText(addParticipantLabel).performClick()
        val nameLabel = getString(Res.string.events_person_name_placeholder)
        rule.onAllNodesWithText(nameLabel).onLast().performTextInput(name)
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun clickContinueButton(): ExpensesScreen {
        val continueLabel = getString(Res.string.events_continue_button)
        rule.onNodeWithText(continueLabel).performClick()
        return ExpensesScreen()
    }
}
