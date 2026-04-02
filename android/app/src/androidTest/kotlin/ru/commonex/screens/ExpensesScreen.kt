package ru.commonex.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import expenses.shared.feature.events.generated.resources.events_info_with_person
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_details
import expenses.shared.feature.expenses.generated.resources.expenses_none
import expenses.shared.feature.expenses.generated.resources.expenses_operation
import expenses.shared.feature.expenses.generated.resources.expenses_revert_description
import org.jetbrains.compose.resources.getString
import kotlin.math.abs
import expenses.shared.feature.events.generated.resources.Res as EventsRes

@OptIn(ExperimentalTestApi::class)
internal class ExpensesScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    suspend fun waitUntilLoaded(): ExpensesScreen {
        waitForElementWithTag("expenses_timeline_list")
        return this
    }

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
        repeat(2) {
            swipeTimelineDown()
        }

        waitForElementWithTag("expenses_menu_button")
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
        scrollTimelineToText(description)
        assertElementWithTextExists(description)
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun verifyRevertedExpenseExists(originalExpenseDescription: String): ExpensesScreen {
        val description = getString(Res.string.expenses_revert_description, originalExpenseDescription)
        return verifyExpenseExists(description)
    }

    context(rule: ComposeTestRule)
    suspend fun verifyTotalSpending(totalSpending: String): ExpensesScreen {
        val amountPart = totalSpending.substringBefore(' ').trim()
        val currencyPart = totalSpending.substringAfterLast(' ').trim()
        scrollTimelineToTag("expenses_total_spending_value")
        val totalLabelText = rule.onNodeWithTag("expenses_total_spending_value")
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString(separator = " ") { annotatedString -> annotatedString.text }
        check(totalLabelText.contains(amountPart) && totalLabelText.contains(currencyPart)) {
            "Total spending label '$totalLabelText' does not contain expected '$totalSpending'"
        }
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun waitUntilDayHeaderVisible(dayKey: String): ExpensesScreen {
        val tag = "expenses_day_header_$dayKey"
        scrollTimelineToTag(tag)
        rule.onNodeWithTag(tag).assertIsDisplayed()
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun waitUntilDayHeaderVisibleBelowDayChips(dayKey: String): ExpensesScreen {
        val tag = dayHeaderTag(dayKey)
        scrollTimelineToTag(tag)
        rule.waitUntil(timeoutMillis = 5_000) {
            val dayHeaderTop = dayHeaderTop(dayKey) ?: return@waitUntil false
            val chipsBottom = chipsRowBottom() ?: return@waitUntil false
            dayHeaderTop >= chipsBottom - 4f
        }
        rule.onNodeWithTag(tag).assertIsDisplayed()
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun clickDayChip(dayKey: String): ExpensesScreen {
        val tag = "expenses_day_chip_$dayKey"
        scrollTimelineToTag(tag)
        rule.onNodeWithTag(tag).performClick()
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyDayHeaderTotalHidden(dayKey: String): ExpensesScreen {
        waitForElementWithTagDoesNotExist("expenses_day_header_total_$dayKey")
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun verifyDayChipsStickyAndSynced(dayKey: String): ExpensesScreen {
        val dayHeaderTag = dayHeaderTag(dayKey)
        scrollTimelineToTag(dayHeaderTag)

        rule.onNodeWithTag("expenses_day_chips_row").assertIsDisplayed()
        stickDayChipsToTopAppBar()

        swipeTimelineUp()

        rule.waitUntil(timeoutMillis = 5_000) {
            areDayChipsAttachedToTopAppBar()
        }

        return this
    }

    context(rule: ComposeTestRule)
    private fun scrollTimelineToText(text: String) {
        if (rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) {
            return
        }

        rule.waitUntil(timeoutMillis = 10_000) {
            try {
                rule.onNodeWithTag("expenses_timeline_list")
                    .performScrollToNode(hasText(text))
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    context(rule: ComposeTestRule)
    private fun scrollTimelineToTag(tag: String) {
        if (rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) {
            return
        }

        rule.onNodeWithTag("expenses_timeline_list")
            .performScrollToNode(hasTestTag(tag))
        waitForElementWithTag(tag)
    }

    private fun dayHeaderTag(dayKey: String): String = "expenses_day_header_$dayKey"

    private fun dayChipTag(dayKey: String): String = "expenses_day_chip_$dayKey"

    context(rule: ComposeTestRule)
    private fun chipsRowTop(): Float? {
        return rule.onAllNodesWithTag("expenses_day_chips_row")
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInRoot
            ?.top
    }

    context(rule: ComposeTestRule)
    private fun chipsRowBottom(): Float? {
        return rule.onAllNodesWithTag("expenses_day_chips_row")
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInRoot
            ?.bottom
    }

    context(rule: ComposeTestRule)
    private fun dayHeaderTop(dayKey: String): Float? {
        return rule.onAllNodesWithTag(dayHeaderTag(dayKey))
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInRoot
            ?.top
    }

    context(rule: ComposeTestRule)
    private fun topAppBarBottom(): Float? {
        return rule.onAllNodesWithTag("expenses_top_app_bar")
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInRoot
            ?.bottom
    }

    context(rule: ComposeTestRule)
    private fun areDayChipsAttachedToTopAppBar(): Boolean {
        val chipsTop = chipsRowTop() ?: return false
        val appBarBottom = topAppBarBottom() ?: return false
        return abs(chipsTop - appBarBottom) <= 4f
    }

    context(rule: ComposeTestRule)
    private fun stickDayChipsToTopAppBar() {
        repeat(MaxStickySearchSwipes) {
            if (areDayChipsAttachedToTopAppBar()) return
            swipeTimelineUp()
        }
        assertDayChipsAttachedToTopAppBar()
    }

    context(rule: ComposeTestRule)
    private fun assertDayChipsAttachedToTopAppBar() {
        val chipsTop = chipsRowTop()
        val appBarBottom = topAppBarBottom()
        check(
            chipsTop != null &&
                appBarBottom != null &&
                abs(chipsTop - appBarBottom) <= 4f
        ) {
            "Expected day chips row to stay attached to the top app bar bottom, " +
                "but chipsTop=$chipsTop and appBarBottom=$appBarBottom"
        }
    }

    context(rule: ComposeTestRule)
    private fun swipeTimelineUp() {
        rule.onNodeWithTag("expenses_timeline_list").performTouchInput { swipeUp() }
        rule.waitForIdle()
    }

    context(rule: ComposeTestRule)
    private fun swipeTimelineDown() {
        rule.onNodeWithTag("expenses_timeline_list").performTouchInput { swipeDown() }
        rule.waitForIdle()
    }

    private companion object {
        const val MaxStickySearchSwipes = 6
    }

}
