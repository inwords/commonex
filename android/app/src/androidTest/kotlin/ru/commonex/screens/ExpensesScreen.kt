package ru.commonex.screens

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import expenses.shared.feature.events.generated.resources.events_info_with_person
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_details
import expenses.shared.feature.expenses.generated.resources.expenses_none
import expenses.shared.feature.expenses.generated.resources.expenses_operation
import expenses.shared.feature.expenses.generated.resources.expenses_revert_description
import org.jetbrains.compose.resources.getString
import expenses.shared.feature.events.generated.resources.Res as EventsRes

internal class ExpensesScreen : BaseScreen() {
    context(rule: ComposeTestRule)
    suspend fun waitUntilLoadedEmpty(): ExpensesScreen {
        val noneLabel = getString(Res.string.expenses_none)
        waitForElementWithText(noneLabel)
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun verifyCurrentPerson(eventName: String, personName: String): ExpensesScreen {
        val titleText = getString(EventsRes.string.events_info_with_person, eventName, personName)
        waitForElementWithText(titleText)
        assertElementWithTextExists(titleText)
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun clickAddExpense(): AddExpenseScreen {
        val operationLabel = getString(Res.string.expenses_operation)
        rule.onNodeWithText(operationLabel).performClick()
        return AddExpenseScreen()
    }

    context(rule: ComposeTestRule)
    fun openMenu(): MenuDialogScreen {
        rule.onNodeWithTag("expenses_menu_button").performClick()
        return MenuDialogScreen()
    }

    context(rule: ComposeTestRule)
    fun clickOnExpense(description: String): ExpenseDetailsScreen {
        waitForElementWithText(description)
        rule.onNodeWithText(description).performClick()
        return ExpenseDetailsScreen()
    }

    context(rule: ComposeTestRule)
    suspend fun clickDebtDetails(): DebtsListScreen {
        val detailsLabel = getString(Res.string.expenses_details)
        rule.onNodeWithText(detailsLabel).performClick()
        return DebtsListScreen()
    }

    context(rule: ComposeTestRule)
    fun verifyExpenseAmount(amount: String): ExpensesScreen {
        waitForElementWithText(amount)
        assertElementWithTextExists(amount)
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyExpenseExists(description: String): ExpensesScreen {
        waitForElementWithText(description)
        assertElementWithTextExists(description)
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun verifyRevertedExpenseExists(originalExpenseDescription: String): ExpensesScreen {
        val description = getString(Res.string.expenses_revert_description, originalExpenseDescription)
        return verifyExpenseExists(description)
    }

}
