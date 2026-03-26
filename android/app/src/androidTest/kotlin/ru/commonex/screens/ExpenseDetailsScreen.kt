package ru.commonex.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack

internal class ExpenseDetailsScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    suspend fun waitUntilLoaded(): ExpenseDetailsScreen {
        waitForElementWithTag("expense_item_pane_root")
        rule.onNodeWithTag("expense_item_pane_root").assertIsDisplayed()
        rule.onNodeWithTag("expense_item_pane_title").assertIsDisplayed()
        rule.onNodeWithTag("expense_item_pane_description_value").assertIsDisplayed()
        rule.onNodeWithTag("expense_item_pane_total_amount_value").assertIsDisplayed()
        rule.onNodeWithTag("expense_item_pane_paid_by_value").assertIsDisplayed()
        rule.onNodeWithTag("expense_item_pane_date_value").assertIsDisplayed()
        rule.onNodeWithTag("expense_item_pane_original_currency_value").assertIsDisplayed()
        rule.onNodeWithTag("expense_item_pane_split_section").assertIsDisplayed()
        rule.onNodeWithTag("expense_item_revert_action").assertIsDisplayed()
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyDescription(description: String): ExpenseDetailsScreen {
        rule.onNodeWithTag("expense_item_pane_description_value").assertTextEquals(description)
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyTotalAmount(totalAmount: String): ExpenseDetailsScreen {
        rule.onNodeWithTag("expense_item_pane_total_amount_value").assertTextEquals(totalAmount)
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyPaidBy(personName: String): ExpenseDetailsScreen {
        rule.onNodeWithTag("expense_item_pane_paid_by_value").assertTextEquals(personName)
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyOriginalCurrency(originalCurrency: String): ExpenseDetailsScreen {
        rule.onNodeWithTag("expense_item_pane_original_currency_value").assertTextEquals(originalCurrency)
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun verifyExchangeRateVisible(): ExpenseDetailsScreen {
        rule.onNodeWithTag("expense_item_pane_exchange_rate_value").assertIsDisplayed()
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyExchangeRate(exchangeRate: String): ExpenseDetailsScreen {
        rule.onNodeWithTag("expense_item_pane_exchange_rate_value").assertTextEquals(exchangeRate)
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun verifyExchangeRateHidden(): ExpenseDetailsScreen {
        rule.onAllNodesWithTag("expense_item_pane_exchange_rate_value").assertCountEquals(0)
        return this
    }

    context(rule: ComposeTestRule)
    fun verifySplitContainsPerson(personName: String): ExpenseDetailsScreen {
        rule.onNodeWithTag(splitPersonTag(personName)).assertIsDisplayed()
        return this
    }

    context(rule: ComposeTestRule)
    fun dismissPane(): ExpensesScreen {
        pressBack()
        waitForElementWithTagDoesNotExist("expense_item_pane_root")
        return ExpensesScreen()
    }

    context(rule: ComposeTestRule)
    suspend fun confirmRevertExpense(): ExpensesScreen {
        rule.onNodeWithTag("expense_item_revert_action").performClick()
        rule.onNodeWithTag("expense_revert_dialog_confirm_button").assertIsDisplayed()
        rule.onNodeWithTag("expense_revert_dialog_confirm_button").performClick()
        waitForElementWithTagDoesNotExist("expense_revert_dialog_confirm_button")
        waitForElementWithTagDoesNotExist("expense_item_pane_root")
        waitForElementWithTag("expenses_menu_button")
        return ExpensesScreen()
    }
}

private fun splitPersonTag(personName: String): String {
    return "expense_item_pane_split_person_$personName"
}
