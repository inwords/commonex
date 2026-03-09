package ru.commonex

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import ru.commonex.screens.ChoosePersonScreen
import ru.commonex.screens.ExpensesScreen
import ru.commonex.screens.LocalEventsScreen
import ru.commonex.screens.MenuDialogScreen
import ru.commonex.ui.MainActivity
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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
     * - Add expense with equal split (default)
     * - Add expense with custom split
     * - Open expense details pane and verify key sections/values
     * - Cancel an expense and verify revert entry
     * - Add converted-currency expense and verify exchange-rate section in details pane
     * - Verify debt calculations
     * - Switch to a different person and verify view updates
     */
    @Test
    fun testBasicNewEventAndExpensesFlow() = composeRule.runTest {
        // Create event and add participants
        val eventName = "UI Test Event"
        val expensesScreen = LocalEventsScreen()
            .clickCreateEvent()
            .enterEventName(eventName)
            .selectCurrency("Euro")
            .clickContinueButton()
            .enterOwnerName("Test User 1")
            .addParticipant("Test User 2")
            .addParticipant("Test User 3")
            .clickContinueButton()
            .waitUntilLoadedEmpty()
            .verifyCurrentPerson(eventName, "Test User 1")

        // Add first expense (equal split - default)
        expensesScreen
            .clickAddExpense()
            .enterDescription("Булка")
            .enterAmount("120")
            .clickConfirm()
            .verifyExpenseAmount("-120")

        // Add second expense with non-equal split
        expensesScreen
            .clickAddExpense()
            .enterDescription("Хот-дог")
            .enterAmount("180")
            .clickEqualSplitSwitch()
            .clickConfirm()
            .verifyExpenseAmount("-180")

        // Open first expense details pane and verify details content
        expensesScreen
            .clickOnExpense("Булка")
            .waitUntilLoaded()
            .verifyDescription("Булка")
            .verifyTotalAmount("-120 EUR")
            .verifyPaidBy("Test User 1")
            .verifyOriginalCurrency("EUR (Euro)")
            .verifyExchangeRateHidden()
            .confirmRevertExpense()
            .verifyRevertedExpenseExists("Булка")

        // Verify debts details and go back
        expensesScreen
            .clickDebtDetails()
            .waitUntilLoaded(eventName)
            .verifyDebtAmount("60", "Test User 1", count = 2)
            .goBack()

        // Add converted-currency expense and verify exchange-rate section in details pane
        expensesScreen
            .clickAddExpense()
            .selectCurrency("US Dollar")
            .enterDescription("FX coffee")
            .enterAmount("10")
            .clickConfirm()
            .verifyExpenseExists("FX coffee")

        expensesScreen
            .clickOnExpense("FX coffee")
            .waitUntilLoaded()
            .verifyDescription("FX coffee")
            .verifyPaidBy("Test User 1")
            .verifyOriginalCurrency("USD (US Dollar)")
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
    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testJoinExistingEvent() = composeRule.runTest {
        LocalEventsScreen()
            .clickJoinEvent()
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
            .verifyCurrentPerson(eventName, "Test User 1")
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
            .verifyCurrentPerson(eventName, "Test User 1")
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
            .assertCreateJoinDescriptionVisible()
    }

    /**
     * Tests keeping events via both methods:
     * - Keep event using "Keep event" button
     * - Keep event by swiping back
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
            .assertCreateJoinDescriptionVisible()
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
     * - Open menu and navigate to "Add participant" screen
     * - Add multiple participants
     * - Verify participants can be selected via Choose Person menu
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
            .addParticipant()
            .addParticipant("New Test User 1")
            .addParticipant("New Test User 2")
            .clickContinueButton()
            .waitUntilLoadedEmpty()
            .verifyCurrentPerson(eventName, "Test User 1")

        // Verify new participants can be selected
        ExpensesScreen()
            .openMenu()
            .chooseParticipant()
            .waitUntilLoaded("Test User 1")
            .selectPerson("New Test User 1")
            .waitUntilLoadedEmpty()
            .verifyCurrentPerson(eventName, "New Test User 1")

        ExpensesScreen()
            .openMenu()
            .chooseParticipant()
            .waitUntilLoaded("New Test User 1")
            .selectPerson("New Test User 2")
            .waitUntilLoadedEmpty()
            .verifyCurrentPerson(eventName, "New Test User 2")

        // Verify that an equal expense can be added successfully after new participants are added
        ExpensesScreen()
            .clickAddExpense()
            .enterDescription("Test Expense")
            .enterAmount("300")
            .clickConfirm()
            .verifyExpenseAmount("-300")
    }

    context(rule: ComposeTestRule)
    private suspend fun createLocalEvent(eventName: String): ExpensesScreen {
        return LocalEventsScreen()
            .clickCreateEvent()
            .enterEventName(eventName)
            .selectCurrency("Euro")
            .clickContinueButton()
            .enterOwnerName("Test User 1")
            .clickContinueButton()
            .waitUntilLoadedEmpty()
    }

    context(rule: ComposeTestRule)
    private suspend fun addExpensesForCurrencies(currencies: List<CurrencyTestModel>) {
        currencies.forEachIndexed { currencyIndex, currency ->
            val description = "Expense ${currency.name} ${currencyIndex + 1}"
            val amount = (currencyIndex + 1) * 10

            ExpensesScreen()
                .clickAddExpense()
                .selectCurrencyByCode(currency.code)
                .enterDescription(description)
                .enterAmount(amount.toString())
                .clickConfirm()
                .verifyExpenseExists(description)
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

private data class CurrencyTestModel(
    val code: String,
    val name: String,
)
