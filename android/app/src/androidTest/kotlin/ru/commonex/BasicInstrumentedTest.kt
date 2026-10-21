package ru.commonex

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inwords.expenses.feature.expenses.domain.ExpenseTimeBackdoor
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import expenses.shared.feature.expenses.generated.resources.expenses_exchange_rate_value
import expenses.shared.feature.expenses.generated.resources.expenses_repayment_from
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import ru.commonex.screens.AddExpenseScreen
import ru.commonex.screens.ChoosePersonScreen
import ru.commonex.screens.CreateEventScreen
import ru.commonex.screens.ExpensesScreen
import ru.commonex.screens.LocalEventsScreen
import ru.commonex.screens.MenuDialogScreen
import ru.commonex.ui.MainActivity
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import expenses.shared.feature.expenses.generated.resources.Res as ExpensesRes

// TODO bring back JUnit5 when it is able to work with Marathon
// .\gradlew :app:connectedAutotestAndroidTest -Dcom.android.tools.r8.disableApiModeling
@RunWith(AndroidJUnit4::class)
class BasicInstrumentedTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val connectivityRule = ConnectivityRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(connectivityRule)
        .around(composeRule)

    /**
     * Tests the complete event creation and expenses flow:
     * - Create event with multiple participants
     * - Open the invitation from the empty group and return
     * - Reach the first-expense prompt and use its action to add an equal-split expense
     * - Verify the expense timeline replaces the first-expense prompt
     * - Add expense with custom split
     * - Open expense details, verify split amounts, and expand/collapse operation details
     * - Cancel an expense and verify revert entry
     * - Add converted-currency expense and verify exchange-rate section in details pane
     * - Verify both debtor-creditor pairs in balances
     * - Discard a prefilled repayment, then settle both debts and verify all-settled without changing spending
     * - Switch to a different person and verify view updates
     */
    @Test
    fun testBasicNewEventAndExpensesFlow() = composeRule.runTest {
        // Create event and add participants
        val eventName = "UI Test Event"
        val expensesScreen = LocalEventsScreen()
            .clickCreateEvent()
            .enterEventName(eventName)
            .openCurrencyPicker()
            .selectCurrency("Euro")
            .enterOwnerName("Test User 1")
            .addParticipant(index = 0, name = "Test User 2")
            .addParticipant(index = 1, name = "Test User 3")
            .createEvent()
            .waitUntilLoadedEmpty()
            .verifyCurrentPersonInEmptyEvent(eventName, "Test User 1")

        expensesScreen
            .clickInvite()
            .waitUntilLoaded(eventName)
            .goBack()
            .waitUntilLoadedEmpty()

        // Add first expense (equal split - default)
        expensesScreen
            .clickAddExpense()
            .enterDescription("Булка")
            .enterAmount("120")
            .clickConfirm()
            .verifyExpenseAmount("-120")
            .verifyTimelineReplacesEmptyState()

        // Add second expense with non-equal split
        expensesScreen
            .clickAddExpense()
            .enterDescription("Хот-дог")
            .enterAmount("180")
            .openSplitEditor()
            .selectCustomSplit()
            .clickDone()
            .clickConfirm()
            .verifyExpenseAmount("-180")

        // Open first expense details pane and verify details content
        expensesScreen
            .clickOnExpense("Булка")
            .waitUntilLoaded()
            .verifyDescription("Булка")
            .verifyTotalAmount("-120 EUR")
            .verifySplitAmount("Test User 1", "-40 EUR")
            .verifyOperationDetailsHidden()
            .toggleOperationDetails()
            .verifyOperationDetailsVisible()
            .verifyPaidBy("Test User 1")
            .verifyExchangeRateHidden()
            .toggleOperationDetails()
            .verifyOperationDetailsHidden()
            .confirmRevertExpense()
            .verifyRevertedExpenseExists("Булка")

        // Verify balances and settle both debts
        expensesScreen
            .clickDebtDetails()
            .waitUntilLoaded(eventName)
            .verifyDebt("Test User 2", "Test User 1", "60", "EUR")
            .verifyDebt("Test User 3", "Test User 1", "60", "EUR")
            .repayDebt("Test User 2", "Test User 1", "60", "EUR")
            .verifyRepayment("60", "Test User 2", "Test User 1")
            .closeToBalances()
            .verifyDebt("Test User 2", "Test User 1", "60", "EUR")
            .repayDebt("Test User 2", "Test User 1", "60", "EUR")
            .clickConfirmToBalances()
            .verifyDebt("Test User 3", "Test User 1", "60", "EUR")
            .repayDebt("Test User 3", "Test User 1", "60", "EUR")
            .verifyRepayment("60", "Test User 3", "Test User 1")
            .clickConfirmToBalances()
            .verifyAllSettled()
            .goBack()
            .verifyTotalSpending("300 EUR")

        // Add converted-currency expense and verify exchange-rate section in details pane
        expensesScreen
            .clickAddExpense()
            .openCurrencyPicker()
            .selectCurrency("US Dollar")
            .enterDescription("FX coffee")
            .enterAmount("10")
            .clickConfirm()
            .verifyExpenseExists("FX coffee")

        expensesScreen
            .clickOnExpense("FX coffee")
            .waitUntilLoaded()
            .verifyDescription("FX coffee")
            .verifySplitAmount("Test User 1", "-3.33 USD")
            .toggleOperationDetails()
            .verifyPaidBy("Test User 1")
            .verifyExchangeRateVisible()
            .dismissPane()

        // Switch to a different person via menu and verify title updates
        ExpensesScreen()
            .openMenu()
            .chooseParticipant()
            .selectPerson("Test User 2")
            .verifyCurrentPerson(eventName, "Test User 2")
    }

    /**
     * Tests exchange-rate validation and recovery:
     * - Enter a foreign-currency expense and reject a zero or missing exchange rate
     * - Recover with a valid custom rate and return to the primary currency
     * - Select the foreign currency again, save with a custom rate, and verify persisted conversion
     */
    @Test
    fun testCustomExchangeRateOverrideFlow() = composeRule.runTest {
        val eventName = "Test event - FX override"
        val description = "FX override"
        val customRate = "1.2000"
        val expectedDisplayedRate = "1.20"
        val exchangeRateText = getString(
            ExpensesRes.string.expenses_exchange_rate_value,
            "USD",
            expectedDisplayedRate,
            "EUR",
        )

        createLocalEvent(eventName)

        ExpensesScreen()
            .clickAddExpense()
            .openCurrencyPicker()
            .selectCurrencyByCode("USD")
            .waitUntilExchangeRateVisible()
            .enterAmount("10")
            .replaceExchangeRate("0")
            .verifyExchangeRateError()
            .verifySaveDisabled()
            .replaceExchangeRate("")
            .verifyExchangeRateError()
            .verifySaveDisabled()
            .replaceExchangeRate(customRate)
            .verifySaveEnabled()
            .openCurrencyPicker()
            .selectCurrencyByCode("EUR")
            .verifyExchangeRateHidden()
            .verifySaveEnabled()
            .openCurrencyPicker()
            .selectCurrencyByCode("USD")
            .waitUntilExchangeRateVisible()
            .replaceExchangeRate(customRate)
            .enterDescription(description)
            .clickConfirm()
            .verifyExpenseExists(description)

        ExpensesScreen()
            .clickOnExpense(description)
            .waitUntilLoaded()
            .verifyDescription(description)
            .verifyTotalAmount("-12 EUR")
            .verifySplitAmount("Test User 1", "-10 USD")
            .toggleOperationDetails()
            .verifyPaidBy("Test User 1")
            .verifyExchangeRateVisible()
            .verifyExchangeRate(exchangeRateText)
    }

    /**
     * Tests custom expense entry and repayment from the personal debt summary:
     * - Dismiss the payer picker, reopen it, and choose another payer
     * - Deselect all participants and verify Save is disabled
     * - Select two participants and verify their equal share
     * - Enter an incomplete custom split and verify the unavailable total and disabled Save
     * - Complete distinct amounts, recreate the activity, and verify the draft and split are retained
     * - Save and verify the payer, total, and resulting personal debt
     * - Open and discard a prefilled repayment, then save it and verify the debt clears without changing spending
     * - Open the repayment details and verify its recipient share and payer
     */
    @Test
    fun testCustomSplitDraftAndPersonalDebtRepayment() = composeRule.runTest {
        val description = "Custom dinner"
        val repaymentDescription = getString(ExpensesRes.string.expenses_repayment_from, "Test User 1")
        LocalEventsScreen()
            .clickCreateEvent()
            .enterEventName("Custom split and repayment")
            .openCurrencyPicker()
            .selectCurrency("Euro")
            .enterOwnerName("Test User 1")
            .addParticipant(index = 0, name = "Test User 2")
            .addParticipant(index = 1, name = "Test User 3")
            .createEvent()
            .waitUntilLoadedEmpty()
            .clickAddExpense()
            .verifySaveDisabled()
            .enterDescription(description)
            .enterAmount("90")
            .openPayerPicker()
            .goBack()
            .verifyPayer("Test User 1")
            .openPayerPicker()
            .selectPerson("Test User 2")
            .verifyPayer("Test User 2")
            .openSplitEditor()
            .togglePerson("Test User 1")
            .togglePerson("Test User 2")
            .togglePerson("Test User 3")
            .clickDone()
            .verifySaveDisabled()
            .openSplitEditor()
            .togglePerson("Test User 1")
            .togglePerson("Test User 2")
            .clickDone()
            .verifyEqualShare("45", "EUR")
            .openSplitEditor()
            .selectCustomSplit()
            .replaceAmount("Test User 1", "")
            .replaceAmount("Test User 2", "20")
            .clickDone()
            .verifyAmountUnavailable()
            .verifySaveDisabled()
            .openSplitEditor()
            .replaceAmount("Test User 1", "10")
            .clickDone()
            .verifyAmount("30")
            .verifySaveEnabled()

        recreateActivity()

        AddExpenseScreen()
            .verifyDescription(description)
            .verifyPayer("Test User 2")
            .verifyAmount("30")
            .openSplitEditor()
            .verifyAmount("Test User 1", "10")
            .verifyAmount("Test User 2", "20")
            .clickDone()
            .clickConfirm()
            .verifyExpenseExists(description)
            .clickOnExpense(description)
            .waitUntilLoaded()
            .verifyTotalAmount("-30 EUR")
            .verifySplitContainsPerson("Test User 1")
            .verifySplitContainsPerson("Test User 2")
            .verifySplitAmount("Test User 1", "-10 EUR")
            .verifySplitAmount("Test User 2", "-20 EUR")
            .toggleOperationDetails()
            .verifyPaidBy("Test User 2")
            .dismissPane()
            .verifyPersonalDebt("Test User 2", "10 EUR")
            .repayPersonalDebt("Test User 2")
            .verifyRepayment("10", "Test User 1", "Test User 2")
            .close()
            .verifyPersonalDebt("Test User 2", "10 EUR")
            .repayPersonalDebt("Test User 2")
            .clickConfirm()
            .verifyNoPersonalDebts()
            .verifyTotalSpending("30 EUR")
            .clickOnExpense(repaymentDescription)
            .waitUntilLoaded()
            .verifyDescription(repaymentDescription)
            .verifyTotalAmount("+10 EUR")
            .verifySplitAmount("Test User 2", "+10 EUR")
            .toggleOperationDetails()
            .verifyPaidBy("Test User 1")
    }

    /**
     * Tests adding expenses for all supported currencies in a single event:
     * - Create event
     * - Add 1 expense for each supported currency
     * - Wait until event is synced
     * - Delete event everywhere
     */
    @Test
    fun testAllSupportedCurrenciesExpenses() = composeRule.runTest {
        val eventName = "Test event"

        createLocalEvent(eventName)

        addExpensesForCurrencies(supportedCurrencies)

        ExpensesScreen()
            .openMenu()
            .waitUntilCopyEnabled()
            .openEventsList()
            .swipeToRevealActions(eventName)
            .clickDeleteEverywhere()
            .confirmDeletion()
            .assertEventNotExists(eventName)
    }

    /**
     * Tests joining an existing remote event:
     * - Enter event ID and access code
     * - Select person from participants list
     * - Verify expenses screen loads
     */
    @Test
    fun testJoinExistingEvent() = composeRule.runTest {
        LocalEventsScreen()
            .clickJoinEvent()
            .openManualEntry()
            .joinEvent("01JYC8BX30EKQYWBRTPKVX6S26", Base64.decode("NTc=").decodeToString() + Base64.decode("NTQ=").decodeToString()) // FIXME: costyl
            .waitUntilLoaded("Test User 2")
            .selectPerson("Test User 2")
            .waitUntilLoadedEmpty()
    }

    /**
     * Tests deeplink auto-join with secure token:
     * - Create event
     * - Copy share link (token) from menu
     * - Remove local event copy
     * - Open deeplink and verify auto-join flow
     */
    @Test
    fun testDeeplinkJoinWithShareToken() = composeRule.runTest {
        val eventName = "Deeplink token event"

        createLocalEvent(eventName)

        val menu = ExpensesScreen().openMenu()
            .waitUntilCopyEnabled()
            .clickCopyShareLink() // wait for event to be synced
        val shareUrl = waitForShareUrl(expectedParam = "token")

        menu
            .openEventsList()
            .swipeToRevealActions(eventName)
            .clickDeleteLocalOnly()
            .assertEventNotExists(eventName)

        triggerActivityOnNewIntent(shareUrl)

        ChoosePersonScreen()
            .waitUntilLoaded("Test User 1")
            .selectPerson("Test User 1")
            .waitUntilLoadedEmpty()
            .verifyCurrentPersonInEmptyEvent(eventName, "Test User 1")
    }

    /**
     * Tests deeplink auto-join with PIN code fallback:
     * - Create event
     * - Disable network and copy share link (pinCode)
     * - Re-enable network and remove local event copy
     * - Open deeplink and verify auto-join flow
     */
    @Test
    fun testDeeplinkJoinWithPinCodeFallback() = composeRule.runTest {
        val eventName = "Deeplink pin event"

        createLocalEvent(eventName)

        val menu = ExpensesScreen().openMenu()
            .waitUntilCopyEnabled() // wait for event to be synced

        val shareUrl = try {
            ConnectivityManager.turnOffDataAndWifi()

            menu.clickCopyShareLink()

            waitForShareUrl(expectedParam = "pinCode")
        } finally {
            ConnectivityManager.turnOnDataAndWifi()
        }

        MenuDialogScreen()
            .openEventsList()
            .swipeToRevealActions(eventName)
            .clickDeleteLocalOnly()
            .assertEventNotExists(eventName)

        triggerActivityOnNewIntent(shareUrl)

        ChoosePersonScreen()
            .waitUntilLoaded("Test User 1")
            .selectPerson("Test User 1")
            .waitUntilLoadedEmpty()
            .verifyCurrentPersonInEmptyEvent(eventName, "Test User 1")
    }

    /**
     * Tests the complete local event flow:
     * - Create multiple events
     * - Switch between events from the events list (with person selection)
     * - Delete them one by one using "Remove local copy"
     * - Verify snackbar appears after deletion
     * - Verify empty state appears after all events deleted
     */
    @Test
    fun testLocalEventsDeletionFlow() = composeRule.runTest {
        val event1 = "Delete test event 1"
        val event2 = "Delete test event 2"

        createLocalEvent(event1)
        ExpensesScreen().openMenu().openEventsList()
        createLocalEvent(event2)

        // Test switching between events
        ExpensesScreen()
            .openMenu()
            .openEventsList()
            .assertEventExists(event1)
            .assertEventExists(event2)
            // Switch to event1
            .clickEvent(event1)
            .selectPerson("Test User 1")
            .waitUntilLoadedEmpty()
            // Go back and switch to event2
            .openMenu()
            .openEventsList()
            .clickEvent(event2)
            .selectPerson("Test User 1")
            .waitUntilLoadedEmpty()

        // Now test deletion
        ExpensesScreen()
            .openMenu()
            .openEventsList()
            // Delete first event
            .swipeToRevealActions(event1)
            .clickDeleteLocalOnly()
            .assertEventNotExists(event1)
            .assertEventDeletedSnackbar(event1)
            .assertEventExists(event2)
            // Delete second event
            .swipeToRevealActions(event2)
            .clickDeleteLocalOnly()
            .assertEventNotExists(event2)
            // Verify empty state after all events deleted
            .assertWelcomeVisible()
    }

    /**
     * Tests local group cards and the existing swipe actions:
     * - Verify both locally created groups show Not synced yet
     * - Open a group, choose a name, and return to the matching group
     * - Keep event using "Keep event" button
     * - Keep event by swiping back
     * - Remove both local groups and verify the welcome screen returns
     *
     * @Offline prevents sync to isolate local-only behavior
     */
    @Offline
    @Test
    fun testLocalEventsKeepEvent() = composeRule.runTest {
        val event1 = "Keep via button"
        val event2 = "Keep via swipe back"

        createLocalEvent(event1)
        ExpensesScreen().openMenu().openEventsList()
        createLocalEvent(event2)

        ExpensesScreen()
            .openMenu()
            .openEventsList()
            .assertEventNotSynced(event1)
            .assertEventNotSynced(event2)
            .clickEvent(event1)
            .waitUntilLoaded("Test User 1")
            .selectPerson("Test User 1")
            .waitUntilLoadedEmpty()
            .verifyCurrentPersonInEmptyEvent(event1, "Test User 1")
            .openMenu()
            .openEventsList()
            // Test keeping via button
            .swipeToRevealActions(event1)
            .clickKeepEvent()
            .assertEventExists(event1)
            // Test keeping via swipe back
            .swipeToRevealActions(event2)
            .swipeBack(event2)
            .assertEventExists(event2)
            // Both events should still exist
            .assertEventExists(event1)
            .assertEventExists(event2)
            .swipeToRevealActions(event1)
            .clickDeleteLocalOnly()
            .assertEventNotExists(event1)
            .assertEventExists(event2)
            .swipeToRevealActions(event2)
            .clickDeleteLocalOnly()
            .assertEventNotExists(event2)
            .assertWelcomeVisible()
    }

    /**
     * Tests "Delete everywhere" flow for synced events:
     * - Create event (automatically syncs)
     * - Delete using "Delete everywhere" option
     * - Confirm in dialog
     */
    @Test
    fun testSyncedEventDeleteEverywhere() = composeRule.runTest {
        val eventName = "Synced event to delete"

        createLocalEvent(eventName)

        ExpensesScreen()
            .openMenu()
            .openEventsList()
            .swipeToRevealActions(eventName)
            .clickDeleteEverywhere()
            // Confirm deletion in dialog
            .confirmDeletion()
            .assertEventNotExists(eventName)
            .assertWelcomeVisible()
    }

    /**
     * Tests canceling "Delete everywhere" via dialog:
     * - Create synced event
     * - Start delete but cancel in dialog
     * - Verify event still exists
     */
    @Test
    fun testSyncedEventDeleteEverywhereCanceled() = composeRule.runTest {
        val eventName = "Synced event keep"

        createLocalEvent(eventName)

        ExpensesScreen()
            .openMenu()
            .openEventsList()
            .swipeToRevealActions(eventName)
            .clickDeleteEverywhere()
            // Cancel deletion in dialog
            .keepEvent()
            // Event should still exist
            .assertEventExists(eventName)
    }

    /**
     * Tests adding participants to an existing event:
     * - Create event with one participant
     * - Verify the group options event name and navigate to Add people
     * - Verify empty and whitespace-only drafts cannot be confirmed
     * - Fill the initial name field, append another field, and save both participants
     * - Verify choosing each new name immediately returns to the matching identity
     * - Verify that an equal expense can be added successfully after new participants are added
     */
    @Test
    fun testAddParticipantsToExistingEvent() = composeRule.runTest {
        val eventName = "Test Event"

        // Create event with one participant
        createLocalEvent(eventName)

        // Add participants via menu
        ExpensesScreen()
            .openMenu()
            .verifyEventName(eventName)
            .addParticipant()
            .verifyConfirmDisabled()
            .enterParticipantName(0, "   ")
            .verifyConfirmDisabled()
            .enterParticipantName(0, "New Test User 1")
            .verifyConfirmEnabled()
            .appendParticipant()
            .verifyConfirmEnabled()
            .enterParticipantName(1, "New Test User 2")
            .clickContinueButton()
            .waitUntilLoadedEmpty()
            .verifyCurrentPersonInEmptyEvent(eventName, "Test User 1")

        // Verify new participants can be selected
        ExpensesScreen()
            .openMenu()
            .chooseParticipant()
            .waitUntilLoaded("Test User 1")
            .selectPerson("New Test User 1")
            .waitUntilLoadedEmpty()
            .verifyCurrentPersonInEmptyEvent(eventName, "New Test User 1")

        ExpensesScreen()
            .openMenu()
            .chooseParticipant()
            .waitUntilLoaded("New Test User 1")
            .selectPerson("New Test User 2")
            .waitUntilLoadedEmpty()
            .verifyCurrentPersonInEmptyEvent(eventName, "New Test User 2")

        // Verify that an equal expense can be added successfully after new participants are added
        ExpensesScreen()
            .clickAddExpense()
            .enterDescription("Test Expense")
            .enterAmount("300")
            .clickConfirm()
            .verifyExpenseAmount("-300")
    }

    /**
     * Tests create-event state restoration as one coherent flow:
     * - Open create event
     * - Enter event name and choose a non-default primary currency
     * - Enter the creator and another participant on the same setup form
     * - Recreate the activity and verify the complete draft is retained
     * - Finish creating the event
     * - Add an expense and verify it inherits the originally selected event currency
     */
    @Test
    fun testCreateEventSurvivesActivityRecreationWithCompleteDraft() = composeRule.runTest {
        val eventName = "Recreated Event"
        val expenseDescription = "Restored currency expense"

        LocalEventsScreen()
            .clickCreateEvent()
            .enterEventName(eventName)
            .enterOwnerName("Test User 1")
            .addParticipant(index = 0, name = "Test User 2")
            .openCurrencyPicker()
            .selectCurrency("Japanese Yen")

        recreateActivity()

        CreateEventScreen()
            .waitUntilLoaded()
            .verifyDraft(eventName, "Test User 1", "Test User 2")
            .verifyCurrency("JPY")
            .createEvent()
            .waitUntilLoadedEmpty()
            .verifyCurrentPersonInEmptyEvent(eventName, "Test User 1")
            .clickAddExpense()
            .enterDescription(expenseDescription)
            .enterAmount("10")
            .clickConfirm()
            .clickOnExpense(expenseDescription)
            .waitUntilLoaded()
            .verifyDescription(expenseDescription)
            .verifySplitAmount("Test User 1", "-5 JPY")
            .toggleOperationDetails()
            .verifyExchangeRateHidden()
    }

    /**
     * Tests timeline day chips as one coherent flow:
     * - Create event
     * - Add operations across multiple calendar days so the day chips row overflows horizontally
     * - Verify total spending summary
     * - Scroll the timeline to a later day and verify the corresponding chip becomes selected before the chips row turns sticky
     * - Tap a replenishment-only day chip and verify its chip becomes selected and its header is shown below the sticky chips without a spending total
     * - Tap an overflowed day chip and verify its chip becomes selected and chip-driven navigation still lands on the requested day
     */
    @Test
    fun testExpensesTimelineDayChipsSupportStickyNavigationAcrossOverflowedDays() = composeRule.runTest {
        val eventName = "Test Timeline Event"
        createLocalEvent(eventName)

        val timelineDayKeys = createTimelineDayChipsScenario()

        ExpensesScreen()
            .verifyTotalSpending("180 EUR")
            .waitUntilDayHeaderVisible(timelineDayKeys.firstDayKey)
            .scrollTimelineToDayHeader(timelineDayKeys.secondDayKey)
            .verifyDayChipSelected(timelineDayKeys.secondDayKey)
            .stickDayChipsToTopAppBar()
            .clickDayChip(timelineDayKeys.replenishmentOnlyDayKey)
            .waitUntilDayHeaderVisibleBelowDayChips(timelineDayKeys.replenishmentOnlyDayKey)
            .verifyDayChipSelected(timelineDayKeys.replenishmentOnlyDayKey)
            .verifyDayHeaderTotalHidden(timelineDayKeys.replenishmentOnlyDayKey)
            .clickDayChip(timelineDayKeys.overflowTargetDayKey)
            .waitUntilDayHeaderVisibleBelowDayChips(timelineDayKeys.overflowTargetDayKey)
            .verifyDayChipSelected(timelineDayKeys.overflowTargetDayKey)
            .openMenu()
    }

    context(rule: ComposeTestRule)
    private suspend fun createLocalEvent(eventName: String): ExpensesScreen {
        return LocalEventsScreen()
            .clickCreateEvent()
            .enterEventName(eventName)
            .openCurrencyPicker()
            .selectCurrency("Euro")
            .enterOwnerName("Test User 1")
            .createEvent()
            .waitUntilLoadedEmpty()
    }

    context(rule: ComposeTestRule)
    private suspend fun addExpensesForCurrencies(currencies: List<CurrencyTestModel>) {
        currencies.forEachIndexed { currencyIndex, currency ->
            val description = "Expense ${currency.name} ${currencyIndex + 1}"
            val amount = (currencyIndex + 1) * 10

            ExpensesScreen()
                .clickAddExpense()
                .openCurrencyPicker()
                .selectCurrencyByCode(currency.code)
                .enterDescription(description)
                .enterAmount(amount.toString())
                .clickConfirm()
                .verifyExpenseExists(description)
        }
    }

    context(rule: ComposeTestRule)
    private suspend fun createTimelineDayChipsScenario(): TimelineDayKeys {
        val firstDayInstant = Instant.parse("2026-03-28T12:00:00Z")
        val secondDayInstant = Instant.parse("2026-03-27T12:00:00Z")
        val replenishmentOnlyDayInstant = Instant.parse("2026-03-26T12:00:00Z")
        val overflowTargetDayInstant = replenishmentOnlyDayInstant - 5.days

        val operations = listOf(
            TimelineOperationInput(
                timestamp = overflowTargetDayInstant,
                expenseType = ExpenseType.Replenishment,
                amount = 11,
                description = "Timeline overflow refund 1",
            ),
            TimelineOperationInput(
                timestamp = replenishmentOnlyDayInstant - 4.days,
                expenseType = ExpenseType.Replenishment,
                amount = 12,
                description = "Timeline overflow refund 2",
            ),
            TimelineOperationInput(
                timestamp = replenishmentOnlyDayInstant - 3.days,
                expenseType = ExpenseType.Replenishment,
                amount = 13,
                description = "Timeline overflow refund 3",
            ),
            TimelineOperationInput(
                timestamp = replenishmentOnlyDayInstant - 2.days,
                expenseType = ExpenseType.Replenishment,
                amount = 14,
                description = "Timeline overflow refund 4",
            ),
            TimelineOperationInput(
                timestamp = replenishmentOnlyDayInstant - 1.days,
                expenseType = ExpenseType.Replenishment,
                amount = 15,
                description = "Timeline overflow refund 5",
            ),
            TimelineOperationInput(
                timestamp = replenishmentOnlyDayInstant,
                expenseType = ExpenseType.Replenishment,
                amount = 15,
                description = "Timeline refund only day",
            ),
            TimelineOperationInput(
                timestamp = secondDayInstant,
                expenseType = ExpenseType.Spending,
                amount = 60,
                description = "Timeline museum",
            ),
            TimelineOperationInput(
                timestamp = firstDayInstant,
                expenseType = ExpenseType.Spending,
                amount = 120,
                description = "Timeline spending",
            ),
        )

        operations.forEach { operation ->
            createTimelineOperation(operation)
        }

        return TimelineDayKeys(
            firstDayKey = firstDayInstant.toTimelineDayKey(),
            secondDayKey = secondDayInstant.toTimelineDayKey(),
            replenishmentOnlyDayKey = replenishmentOnlyDayInstant.toTimelineDayKey(),
            overflowTargetDayKey = overflowTargetDayInstant.toTimelineDayKey(),
        )
    }

    context(rule: ComposeTestRule)
    private suspend fun createTimelineOperation(operation: TimelineOperationInput) {
        withExpensesMutationTime(operation.timestamp) {
            val addExpenseScreen = ExpensesScreen().clickAddExpense()
            if (operation.expenseType == ExpenseType.Replenishment) {
                addExpenseScreen.selectReplenishmentType()
            }
            addExpenseScreen
                .enterDescription(operation.description)
                .enterAmount(operation.amount.toString())
                .clickConfirm()
                .verifyExpenseExists(operation.description)
        }
    }

    private suspend fun withExpensesMutationTime(
        timestamp: Instant,
        block: suspend () -> Unit,
    ) {
        ExpenseTimeBackdoor.overrideForTests(timestamp)
        try {
            block()
        } finally {
            ExpenseTimeBackdoor.overrideForTests(null)
        }
    }

    context(rule: ComposeTestRule)
    private fun waitForShareUrl(expectedParam: String, timeoutMs: Long = 10000): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(ClipboardManager::class.java)

        var shareUrl: String? = null
        rule.waitUntil(timeoutMillis = timeoutMs) {
            val text = clipboard.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?: return@waitUntil false

            val match = shareUrlRegex.find(text)
            if (match != null && match.groupValues[1] == expectedParam) {
                shareUrl = match.value
                return@waitUntil true
            } else {
                return@waitUntil false
            }
        }

        return requireNotNull(shareUrl) {
            "Clipboard did not contain share URL with $expectedParam within ${timeoutMs}ms"
        }
    }

    private fun triggerActivityOnNewIntent(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        composeRule.activityRule.scenario.onActivity {
            it.onNewIntent(intent)
        }
    }

    private fun recreateActivity() {
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    private companion object {

        private val supportedCurrencies = listOf(
            CurrencyTestModel(code = "EUR", name = "Euro"),
            CurrencyTestModel(code = "USD", name = "US Dollar"),
            CurrencyTestModel(code = "RUB", name = "Russian Ruble"),
            CurrencyTestModel(code = "JPY", name = "Japanese Yen"),
            CurrencyTestModel(code = "TRY", name = "Turkish Lira"),
            CurrencyTestModel(code = "AED", name = "UAE Dirham"),
        )

        private val shareUrlRegex = Regex("https://commonex\\.ru/event/[A-Za-z0-9]+\\?(token|pinCode)=[A-Za-z0-9]+")
    }

}

private data class TimelineDayKeys(
    val firstDayKey: String,
    val secondDayKey: String,
    val replenishmentOnlyDayKey: String,
    val overflowTargetDayKey: String,
)

private data class TimelineOperationInput(
    val timestamp: Instant,
    val expenseType: ExpenseType,
    val amount: Int,
    val description: String,
)

private data class CurrencyTestModel(
    val code: String,
    val name: String,
)

private fun Instant.toTimelineDayKey(): String {
    return toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
}
