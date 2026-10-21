package ru.commonex.screens

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

internal class ExpenseCurrencyPickerScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    fun selectCurrency(currencyName: String): AddExpenseScreen {
        rule.onNode(hasText(currencyName) and hasAnyAncestor(hasTestTag("add_expense_currency_sheet")))
            .performScrollTo().performClick()
        waitForElementWithTagDoesNotExist("add_expense_currency_sheet")
        return AddExpenseScreen()
    }

    context(rule: ComposeTestRule)
    fun selectCurrencyByCode(currencyCode: String): AddExpenseScreen {
        rule.onNode(hasTestTag("add_expense_currency_option_$currencyCode") and hasAnyAncestor(hasTestTag("add_expense_currency_sheet")))
            .performScrollTo().performClick()
        waitForElementWithTagDoesNotExist("add_expense_currency_sheet")
        return AddExpenseScreen()
    }
}
