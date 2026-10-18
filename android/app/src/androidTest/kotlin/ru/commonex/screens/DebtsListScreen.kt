package ru.commonex.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.common_back
import expenses.shared.feature.expenses.generated.resources.expenses_debtor_owes
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class)
internal class DebtsListScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    suspend fun waitUntilLoaded(eventName: String): DebtsListScreen {
        val backLabel = getString(Res.string.common_back)
        rule.waitUntilAtLeastOneExists(hasContentDescription(backLabel), timeoutMillis = 10000)

        waitForElementWithText(eventName)
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun verifyDebt(debtor: String, creditor: String, amount: String, currencyCode: String): DebtsListScreen {
        val matcher = debtMatcher(debtor, creditor, amount, currencyCode)
        rule.waitUntilAtLeastOneExists(matcher, timeoutMillis = 10000)
        rule.onNode(matcher).assertIsDisplayed()
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun repayDebt(debtor: String, creditor: String, amount: String, currencyCode: String): AddExpenseScreen {
        rule.onNode(debtMatcher(debtor, creditor, amount, currencyCode)).performClick()
        return AddExpenseScreen()
    }

    context(rule: ComposeTestRule)
    fun verifyAllSettled(): DebtsListScreen {
        waitForElementWithTag("debts_list_settled")
        rule.onNodeWithTag("debts_list_settled").assertIsDisplayed()
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun goBack(): ExpensesScreen {
        val backLabel = getString(Res.string.common_back)
        rule.onNodeWithContentDescription(backLabel).performClick()
        return ExpensesScreen()
    }

}

private suspend fun debtMatcher(debtor: String, creditor: String, amount: String, currencyCode: String): SemanticsMatcher {
    val debtorHeader = getString(Res.string.expenses_debtor_owes, debtor)
    return hasTestTag("debts_list_debt_button") and
        hasText(creditor) and hasText(amount) and hasText(currencyCode) and
        hasAnyAncestor(hasTestTag("debts_list_debtor") and hasAnyDescendant(hasText(debtorHeader)))
}
