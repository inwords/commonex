package ru.commonex.screens

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextReplacement

internal class ExpenseSplitScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    fun togglePerson(personName: String): ExpenseSplitScreen {
        rule.onNode(hasText(personName) and hasAnyAncestor(hasTestTag("add_expense_split_editor")))
            .performScrollTo().performClick()
        return this
    }

    context(rule: ComposeTestRule)
    fun replaceAmount(personName: String, amount: String): ExpenseSplitScreen {
        rule.onNode(hasContentDescription(personName) and hasAnyAncestor(hasTestTag("add_expense_split_editor")))
            .performScrollTo().performTextReplacement(amount)
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyAmount(personName: String, amount: String): ExpenseSplitScreen {
        rule.onNode(hasContentDescription(personName) and hasAnyAncestor(hasTestTag("add_expense_split_editor")))
            .performScrollTo().assert(hasText(amount))
        return this
    }

    context(rule: ComposeTestRule)
    fun selectCustomSplit(): ExpenseSplitScreen {
        rule.onNodeWithTag("split_custom_button").performScrollTo().performClick()
        return this
    }

    context(rule: ComposeTestRule)
    fun clickDone(): AddExpenseScreen {
        rule.onNodeWithTag("add_expense_split_editor").performScrollToKey("done")
        rule.onNodeWithTag("split_done_button").performClick()
        waitForElementWithTagDoesNotExist("add_expense_split_editor")
        return AddExpenseScreen()
    }
}
