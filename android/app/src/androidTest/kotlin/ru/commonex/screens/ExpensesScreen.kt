package ru.commonex.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneTags
import expenses.shared.feature.events.generated.resources.events_info_with_person
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_details
import expenses.shared.feature.expenses.generated.resources.expenses_none
import expenses.shared.feature.expenses.generated.resources.expenses_operation
import expenses.shared.feature.expenses.generated.resources.expenses_revert_description
import expenses.shared.feature.expenses.generated.resources.expenses_status_edited
import expenses.shared.feature.expenses.generated.resources.expenses_status_reverted
import org.jetbrains.compose.resources.getString
import kotlin.math.abs
import expenses.shared.feature.events.generated.resources.Res as EventsRes

@OptIn(ExperimentalTestApi::class)
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
        waitForElementWithTag(ExpensesPaneTags.MENU_BUTTON)
        rule.onNodeWithTag(ExpensesPaneTags.MENU_BUTTON).performClick()
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
    fun verifyExpenseDoesNotExist(description: String): ExpensesScreen {
        waitForElementWithTextDoesNotExist(description)
        rule.onAllNodesWithText(description).assertCountEquals(0)
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun verifyRevertedExpenseDoesNotExist(originalExpenseDescription: String): ExpensesScreen {
        val description = getString(Res.string.expenses_revert_description, originalExpenseDescription)
        return verifyExpenseDoesNotExist(description)
    }

    context(rule: ComposeTestRule)
    suspend fun verifyEditedStatusForToday(): ExpensesScreen {
        return verifyExpenseStatusForToday(ExpenseStatus.Edited)
    }

    context(rule: ComposeTestRule)
    suspend fun verifyRevertedStatusForToday(): ExpensesScreen {
        return verifyExpenseStatusForToday(ExpenseStatus.Reverted)
    }

    context(rule: ComposeTestRule)
    fun verifyTotalSpending(totalSpending: String): ExpensesScreen {
        waitUntilTotalSpending(totalSpending)
        return this
    }

    context(rule: ComposeTestRule)
    fun waitUntilTotalSpending(
        totalSpending: String,
        timeout: Long = 10_000,
    ): ExpensesScreen {
        val amountPart = totalSpending.substringBefore(' ').trim()
        val currencyPart = totalSpending.substringAfterLast(' ').trim()
        rule.waitUntil(timeoutMillis = timeout) {
            val totalLabelText = rule.onNodeWithTag(ExpensesPaneTags.TOTAL_SPENDING_VALUE)
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .joinToString(separator = " ") { annotatedString -> annotatedString.text }
            totalLabelText.contains(amountPart) && totalLabelText.contains(currencyPart)
        }
        return this
    }

    context(rule: ComposeTestRule)
    fun waitUntilDayHeaderVisible(dayKey: String): ExpensesScreen {
        waitForElementWithTag(ExpensesPaneTags.dayHeader(dayKey))
        return this
    }

    context(rule: ComposeTestRule)
    fun waitUntilDayHeaderVisibleBelowDayChips(dayKey: String): ExpensesScreen {
        val tag = ExpensesPaneTags.dayHeader(dayKey)
        rule.waitUntil(timeoutMillis = 10_000) {
            val dayHeaderTop = dayHeaderTop(dayKey) ?: return@waitUntil false
            val chipsBottom = chipsRowBottom() ?: return@waitUntil false
            dayHeaderTop >= chipsBottom - 4f
        }
        rule.onNodeWithTag(tag).assertIsDisplayed()
        return this
    }

    context(rule: ComposeTestRule)
    fun scrollTimelineToDayHeader(dayKey: String): ExpensesScreen {
        rule.onNodeWithTag(ExpensesPaneTags.TIMELINE_LIST)
            .performScrollToKey(ExpensesPaneTags.timelineDayHeaderKey(dayKey))
            .performClick()
        rule.onNodeWithTag(ExpensesPaneTags.dayHeader(dayKey)).assertIsDisplayed()
        return this
    }

    context(rule: ComposeTestRule)
    fun clickDayChip(dayKey: String): ExpensesScreen {
        rule.onNodeWithTag(ExpensesPaneTags.DAY_CHIPS_ROW)
            .performScrollToKey(dayKey)
            .performClick()
        rule.onNodeWithTag(ExpensesPaneTags.dayChip(dayKey)).performClick()
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyDayChipSelected(dayKey: String): ExpensesScreen {
        rule.onNodeWithTag(ExpensesPaneTags.dayChip(dayKey))
            .fetchSemanticsNode()
            .config[SemanticsProperties.Selected]
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyDayHeaderTotalHidden(dayKey: String): ExpensesScreen {
        waitForElementWithTagDoesNotExist(ExpensesPaneTags.dayHeaderTotal(dayKey))
        return this
    }

    context(rule: ComposeTestRule)
    fun stickDayChipsToTopAppBar(): ExpensesScreen {
        rule.onNodeWithTag(ExpensesPaneTags.DAY_CHIPS_ROW).assertIsDisplayed()

        if (areDayChipsAttachedToTopAppBar()) return this

        swipeTimelineUp()
        assertDayChipsAttachedToTopAppBar()

        return this
    }

    context(rule: ComposeTestRule)
    private fun chipsRowTop(): Float? {
        return rule.onAllNodesWithTag(ExpensesPaneTags.DAY_CHIPS_ROW)
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInRoot
            ?.top
    }

    context(rule: ComposeTestRule)
    private fun chipsRowBottom(): Float? {
        return rule.onAllNodesWithTag(ExpensesPaneTags.DAY_CHIPS_ROW)
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInRoot
            ?.bottom
    }

    context(rule: ComposeTestRule)
    private fun dayHeaderTop(dayKey: String): Float? {
        return rule.onAllNodesWithTag(ExpensesPaneTags.dayHeader(dayKey))
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInRoot
            ?.top
    }

    context(rule: ComposeTestRule)
    private fun topAppBarBottom(): Float? {
        return rule.onAllNodesWithTag(ExpensesPaneTags.TOP_APP_BAR)
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
        rule.onNodeWithTag(ExpensesPaneTags.TIMELINE_LIST).performScrollToIndex(FIRST_DAY_HEADER_INDEX)
        rule.waitForIdle()
    }

    context(rule: ComposeTestRule)
    private suspend fun verifyExpenseStatusForToday(status: ExpenseStatus): ExpensesScreen {
        waitForElementWithTag(ExpensesPaneTags.EXPENSE_STATUS_TEXT)
        rule.onNodeWithTag(ExpensesPaneTags.EXPENSE_STATUS_TEXT)
            .assertIsDisplayed()
            .assertTextContains(
                when (status) {
                    ExpenseStatus.Edited -> getString(Res.string.expenses_status_edited)
                    ExpenseStatus.Reverted -> getString(Res.string.expenses_status_reverted)
                },
                substring = true,
            )
        return this
    }

    private enum class ExpenseStatus {
        Edited,
        Reverted,
    }

    private companion object {

        private const val FIRST_DAY_HEADER_INDEX = 4
    }

}
