package ru.commonex.screens

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import expenses.shared.core.ui_design.generated.resources.common_close
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_add_exchange_rate_invalid
import expenses.shared.feature.expenses.generated.resources.expenses_add_repayment_title
import expenses.shared.feature.expenses.generated.resources.expenses_add_share_each
import expenses.shared.feature.expenses.generated.resources.expenses_amount_unavailable
import expenses.shared.feature.expenses.generated.resources.expenses_repayment
import org.jetbrains.compose.resources.getString
import expenses.shared.core.ui_design.generated.resources.Res as CoreRes

internal class AddExpenseScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    fun enterDescription(description: String): AddExpenseScreen {
        rule.onNodeWithTag("add_expense_description_input").performScrollTo().performTextInput(description)
        return this
    }

    context(rule: ComposeTestRule)
    fun enterAmount(amount: String): AddExpenseScreen {
        rule.onNodeWithTag("add_expense_amount_input").performScrollTo().performTextInput(amount)
        return this
    }

    context(rule: ComposeTestRule)
    fun openCurrencyPicker(): ExpenseCurrencyPickerScreen {
        rule.onNodeWithTag("add_expense_currency_picker_button").performScrollTo().performClick()
        return ExpenseCurrencyPickerScreen()
    }

    context(rule: ComposeTestRule)
    fun waitUntilExchangeRateVisible(): AddExpenseScreen {
        waitForElementWithTag(EXCHANGE_RATE_INPUT_TAG)
        return this
    }

    context(rule: ComposeTestRule)
    fun replaceExchangeRate(rate: String): AddExpenseScreen {
        rule.onNodeWithTag(EXCHANGE_RATE_INPUT_TAG).performScrollTo().performTextReplacement(rate)
        return this
    }

    context(rule: ComposeTestRule)
    fun openSplitEditor(): ExpenseSplitScreen {
        rule.onNodeWithTag("add_expense_split_button").performScrollTo().performClick()
        waitForElementWithTag("add_expense_split_editor")
        return ExpenseSplitScreen()
    }

    context(rule: ComposeTestRule)
    fun openPayerPicker(): ExpensePayerScreen {
        rule.onNodeWithTag("add_expense_payer_button").performScrollTo().performClick()
        return ExpensePayerScreen()
    }

    context(rule: ComposeTestRule)
    fun verifyPayer(personName: String): AddExpenseScreen {
        rule.onNodeWithTag("add_expense_payer_button").performScrollTo().assert(hasText(personName))
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyAmount(amount: String): AddExpenseScreen {
        rule.onNodeWithTag("add_expense_amount_input").performScrollTo().assertTextEquals(amount)
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyDescription(description: String): AddExpenseScreen {
        rule.onNodeWithTag("add_expense_description_input").performScrollTo().assert(hasText(description))
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun verifyRepayment(amount: String, fromPerson: String, toPerson: String): AddExpenseScreen {
        assertElementWithTextExists(getString(Res.string.expenses_add_repayment_title))
        verifyAmount(amount)
        verifyPayer(fromPerson)
        rule.onNodeWithTag("add_expense_split_button").performScrollTo().assert(hasText(toPerson))
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun verifyAmountUnavailable(): AddExpenseScreen {
        return verifyAmount(getString(Res.string.expenses_amount_unavailable))
    }

    context(rule: ComposeTestRule)
    suspend fun verifyEqualShare(amount: String, currencyCode: String): AddExpenseScreen {
        val shareText = getString(Res.string.expenses_add_share_each, amount, currencyCode)
        rule.onNodeWithTag("add_expense_split_button").performScrollTo().assert(hasText(shareText))
        return this
    }

    context(rule: ComposeTestRule)
    fun verifySaveDisabled(): AddExpenseScreen {
        rule.onNodeWithTag("add_expense_save_button").assertIsNotEnabled()
        return this
    }

    context(rule: ComposeTestRule)
    fun verifySaveEnabled(): AddExpenseScreen {
        rule.onNodeWithTag("add_expense_save_button").assertIsEnabled()
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun verifyExchangeRateError(): AddExpenseScreen {
        rule.onNodeWithTag(EXCHANGE_RATE_INPUT_TAG).performScrollTo()
            .assert(hasText(getString(Res.string.expenses_add_exchange_rate_invalid)))
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyExchangeRateHidden(): AddExpenseScreen {
        waitForElementWithTagDoesNotExist(EXCHANGE_RATE_INPUT_TAG)
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun close(): ExpensesScreen {
        rule.onNodeWithContentDescription(getString(CoreRes.string.common_close)).performClick()
        waitForElementWithTagDoesNotExist("add_expense_save_button")
        return ExpensesScreen()
    }

    context(rule: ComposeTestRule)
    suspend fun closeToBalances(): DebtsListScreen {
        rule.onNodeWithContentDescription(getString(CoreRes.string.common_close)).performClick()
        waitForElementWithTagDoesNotExist("add_expense_save_button")
        return DebtsListScreen()
    }

    context(rule: ComposeTestRule)
    suspend fun selectReplenishmentType(): AddExpenseScreen {
        val replenishmentLabel = getString(Res.string.expenses_repayment)
        rule.onNodeWithText(replenishmentLabel).performScrollTo().performClick()
        return this
    }

    context(rule: ComposeTestRule)
    fun clickConfirm(): ExpensesScreen {
        rule.onNodeWithTag("add_expense_save_button").performClick()
        waitForElementWithTagDoesNotExist("add_expense_save_button")
        return ExpensesScreen()
    }

    context(rule: ComposeTestRule)
    fun clickConfirmToBalances(): DebtsListScreen {
        rule.onNodeWithTag("add_expense_save_button").performClick()
        waitForElementWithTagDoesNotExist("add_expense_save_button")
        return DebtsListScreen()
    }
}

private const val EXCHANGE_RATE_INPUT_TAG = "add_expense_exchange_rate_input"
