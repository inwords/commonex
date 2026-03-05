package ru.commonex.screens

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_description
import expenses.shared.feature.expenses.generated.resources.expenses_save
import expenses.shared.feature.expenses.generated.resources.expenses_total_amount
import org.jetbrains.compose.resources.getString

internal class AddExpenseScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    suspend fun enterDescription(description: String): AddExpenseScreen {
        val descriptionLabel = getString(Res.string.expenses_description)
        rule.onNodeWithText(descriptionLabel).performTextInput(description)
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun enterAmount(amount: String): AddExpenseScreen {
        val totalAmountLabel = getString(Res.string.expenses_total_amount)
        rule.onNodeWithText(totalAmountLabel).performTextInput(amount)
        return this
    }

    context(rule: ComposeTestRule)
    fun selectCurrency(currencyName: String): AddExpenseScreen {
        rule.onNodeWithText(currencyName).performScrollTo().performClick()
        return this
    }

    context(rule: ComposeTestRule)
    fun selectCurrencyByCode(currencyCode: String): AddExpenseScreen {
        rule.onNodeWithTag(currencyOptionTag(currencyCode)).performScrollTo().performClick()
        return this
    }

    /**
     * Clicks the equal split switch to toggle its state.
     * Default state is ON (equal split), so calling this will turn it OFF.
     */
    context(rule: ComposeTestRule)
    fun clickEqualSplitSwitch(): AddExpenseScreen {
        rule.onNodeWithTag("equal_split_switch").performClick()
        rule.waitForIdle()
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun clickConfirm(): ExpensesScreen {
        val saveLabel = getString(Res.string.expenses_save)
        rule.onNodeWithText(saveLabel).performScrollTo().performClick()
        return ExpensesScreen()
    }
}

private fun currencyOptionTag(currencyCode: String): String {
    return "add_expense_currency_option_$currencyCode"
}
