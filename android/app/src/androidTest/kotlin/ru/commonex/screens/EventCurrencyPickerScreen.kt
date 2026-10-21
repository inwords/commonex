package ru.commonex.screens

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

internal class EventCurrencyPickerScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    fun selectCurrency(currencyName: String): CreateEventScreen {
        rule.onNode(hasText(currencyName, substring = true) and hasAnyAncestor(hasTestTag("events_currency_picker")))
            .performScrollTo().performClick()
        waitForElementWithTagDoesNotExist("events_currency_picker")
        return CreateEventScreen()
    }
}
