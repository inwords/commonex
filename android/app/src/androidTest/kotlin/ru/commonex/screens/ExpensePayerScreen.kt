package ru.commonex.screens

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack

internal class ExpensePayerScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    fun selectPerson(personName: String): AddExpenseScreen {
        rule.onNode(hasText(personName) and hasAnyAncestor(hasTestTag("add_expense_payer_sheet")))
            .performScrollTo().performClick()
        waitForElementWithTagDoesNotExist("add_expense_payer_sheet")
        return AddExpenseScreen()
    }

    context(rule: ComposeTestRule)
    fun goBack(): AddExpenseScreen {
        pressBack()
        waitForElementWithTagDoesNotExist("add_expense_payer_sheet")
        return AddExpenseScreen()
    }
}
