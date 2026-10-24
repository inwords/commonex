package ru.commonex.screens

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneTags
import expenses.shared.feature.events.generated.resources.events_info_identity
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_all_balances
import expenses.shared.feature.expenses.generated.resources.expenses_first_identity
import expenses.shared.feature.expenses.generated.resources.expenses_revert_description
import expenses.shared.feature.expenses.generated.resources.expenses_total_spent
import expenses.shared.feature.expenses.generated.resources.expenses_you_owe_nothing
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import kotlin.math.abs
import expenses.shared.feature.events.generated.resources.Res as EventsRes

internal class ExpensesScreen : BaseScreen() {

    context(rule: ComposeTestRule)
    fun waitUntilLoadedEmpty(): ExpensesScreen {
        waitForElementWithTag(ExpensesPaneTags.EMPTY_STATE)
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun verifyCurrentPerson(eventName: String, personName: String): ExpensesScreen {
        val identityText = getString(EventsRes.string.events_info_identity, personName)
        val matcher = hasTestTag(ExpensesPaneTags.EVENT_INFO) and
            hasAnyDescendant(hasText(eventName)) and hasAnyDescendant(hasText(identityText))
        waitForElement(matcher)
        rule.onNode(matcher).assertIsDisplayed()
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun verifyCurrentPersonInEmptyEvent(eventName: String, personName: String): ExpensesScreen {
        val identityText = getString(Res.string.expenses_first_identity, personName)
        val matcher = hasTestTag(ExpensesPaneTags.EMPTY_STATE) and
            hasAnyDescendant(hasText(eventName)) and hasAnyDescendant(hasText(identityText))
        waitForElement(matcher)
        rule.onNode(matcher).assertIsDisplayed()
        return this
    }

    context(rule: ComposeTestRule)
    fun clickAddExpense(): AddExpenseScreen {
        waitForElementWithTag(ExpensesPaneTags.ADD_EXPENSE_BUTTON)
        rule.onNodeWithTag(ExpensesPaneTags.ADD_EXPENSE_BUTTON).performClick()
        return AddExpenseScreen()
    }

    context(rule: ComposeTestRule)
    fun clickInvite(): InviteEventScreen {
        rule.onNodeWithTag(ExpensesPaneTags.INVITE_BUTTON).performScrollTo().performClick()
        return InviteEventScreen()
    }

    context(rule: ComposeTestRule)
    fun verifyTimelineReplacesEmptyState(): ExpensesScreen {
        waitForElementWithTagDoesNotExist(ExpensesPaneTags.EMPTY_STATE)
        waitForElementWithTag(ExpensesPaneTags.TIMELINE_LIST)
        rule.onNodeWithTag(ExpensesPaneTags.TIMELINE_LIST).assertIsDisplayed()
        return this
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
        val detailsLabel = getString(Res.string.expenses_all_balances)
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

    @OptIn(ExperimentalTestApi::class)
    context(rule: ComposeTestRule)
    fun refreshAndWaitUntilFinished(): ExpensesScreen {
        val refreshing = SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)
        rule.onNodeWithTag(ExpensesPaneTags.TIMELINE_LIST)
            .performScrollToIndex(0)
            .performTouchInput { swipeDown(startY = height * 0.5f, endY = height * 0.9f, durationMillis = 600) }
        waitForElement(refreshing)
        rule.onNode(refreshing).assertIsDisplayed()
        rule.waitUntilDoesNotExist(refreshing, timeoutMillis = 15_000)
        return this
    }

    context(rule: ComposeTestRule)
    fun verifyPersonalDebt(personName: String, amount: String): ExpensesScreen {
        waitForElementWithText(amount)
        rule.onNode(hasText(personName) and hasText(amount) and hasAnyAncestor(hasTestTag(ExpensesPaneTags.PERSONAL_DEBTS)))
            .assertIsDisplayed()
        return this
    }

    context(rule: ComposeTestRule)
    fun repayPersonalDebt(personName: String): AddExpenseScreen {
        rule.onNode(hasText(personName) and hasAnyAncestor(hasTestTag(ExpensesPaneTags.PERSONAL_DEBTS)))
            .performClick()
        return AddExpenseScreen()
    }

    context(rule: ComposeTestRule)
    suspend fun verifyNoPersonalDebts(): ExpensesScreen {
        val text = getString(Res.string.expenses_you_owe_nothing)
        waitForElementWithText(text)
        rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(ExpensesPaneTags.PERSONAL_DEBTS))).assertIsDisplayed()
        return this
    }

    context(rule: ComposeTestRule)
    suspend fun verifyRevertedExpenseExists(originalExpenseDescription: String): ExpensesScreen {
        val description = getString(Res.string.expenses_revert_description, originalExpenseDescription)
        return verifyExpenseExists(description)
    }

    context(rule: ComposeTestRule)
    suspend fun verifyTotalSpending(totalSpending: String): ExpensesScreen {
        val expectedLabel = getString(Res.string.expenses_total_spent, totalSpending)
        val totalLabelText = rule.onNodeWithTag(ExpensesPaneTags.TOTAL_SPENDING_VALUE)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString(separator = " ") { annotatedString -> annotatedString.text }
        assertEquals(expectedLabel, totalLabelText)
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
            .assertIsSelected()
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

    private companion object {

        private const val FIRST_DAY_HEADER_INDEX = 4
    }

}
