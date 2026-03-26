package com.inwords.expenses.feature.expenses.ui.add

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.SimpleScreenState
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.AddCustomSplitExpenseUseCase
import com.inwords.expenses.feature.expenses.domain.AddEqualSplitExpenseUseCase
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneUiModel.CurrencyInfoUiModel
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneUiModel.ExpenseSplitWithPersonUiModel
import com.inwords.expenses.feature.expenses.ui.add.AddExpensePaneUiModel.PersonInfoUiModel
import com.inwords.expenses.feature.settings.api.SettingsRepository
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.StringResource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class AddExpenseViewModelTest {

    // region Test Fixtures
    private object TestFixtures {
        val USD = Currency(id = 100, serverId = null, code = "USD", name = "US Dollar", rate = BigDecimal.ONE)
        val EUR = Currency(id = 101, serverId = null, code = "EUR", name = "Euro", rate = BigDecimal.parseString("0.85"))
        val EUR_SMALL = Currency(id = 102, serverId = null, code = "EUR", name = "Euro", rate = BigDecimal.parseString("0.8677"))
        val JPY = Currency(id = 103, serverId = null, code = "JPY", name = "Japanese Yen", rate = BigDecimal.parseString("158.4072"))

        val person1 = Person(id = 1, serverId = "s1", name = "Vasilii")
        val person2 = Person(id = 2, serverId = "s2", name = "Dania")
        val person3 = Person(id = 3, serverId = "s3", name = "Angela")

        val event = Event(
            id = 10L,
            serverId = "ev-10",
            name = "Trip",
            pinCode = "1234",
            primaryCurrencyId = USD.id
        )

        val eventDetails = EventDetails(
            event = event,
            persons = listOf(person1, person2, person3),
            currencies = listOf(USD, EUR),
            primaryCurrency = USD
        )
    }
    // endregion

    // region Test Setup
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // Controlled backing flows
    private val currentEventFlow = MutableStateFlow<EventDetails?>(null)
    private val currentPersonIdFlow = MutableStateFlow<Long?>(TestFixtures.person1.id)

    // Mocks
    private val navigationController = mockk<NavigationController>(relaxed = true) {
        justRun { popBackStack() }
    }
    private val getCurrentEventStateUseCase = mockk<GetCurrentEventStateUseCase>(relaxed = true) {
        every { this@mockk.currentEvent } returns currentEventFlow
    }
    private val addEqualSplitExpenseUseCase = mockk<AddEqualSplitExpenseUseCase>(relaxed = true)
    private val addCustomSplitExpenseUseCase = mockk<AddCustomSplitExpenseUseCase>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true) {
        coEvery { getCurrentPersonId() } returns currentPersonIdFlow
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }
    // endregion

    // region State Initialization Tests

    @Test
    fun `should emit Loading then Success when event and current person are present`() = testScope.runTest {
        // Given
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()

            currentEventFlow.value = TestFixtures.eventDetails
            advanceUntilIdle()
            val uiModel = awaitSuccess()

            assertEquals("", uiModel.description)
            assertEquals(ExpenseType.Spending, uiModel.expenseType)
            assertTrue(uiModel.currencies.byCode("USD").selected)
            assertTrue(uiModel.persons.byId(TestFixtures.person1.id).selected)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should emit Error when event is null`() = testScope.runTest {
        // Given
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()

            // Drive combine to re-evaluate with null event
            advanceUntilIdle()
            currentPersonIdFlow.value = TestFixtures.person1.id
            currentEventFlow.value = null

            awaitErrorState()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should emit Error when current person ID is null`() = testScope.runTest {
        // Given
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()

            currentEventFlow.value = TestFixtures.eventDetails
            currentPersonIdFlow.value = null
            advanceUntilIdle()

            awaitErrorState()
            cancelAndIgnoreRemainingEvents()
        }
    }
    // endregion

    // region User Interaction Tests

    @Test
    fun `should select currency when onCurrencyClicked is invoked`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()
            val initial = awaitSuccess()
            assertTrue(initial.currencies.byCode("USD").selected)
            assertEquals(null, initial.exchangeRate)

            viewModel.onCurrencyClicked(
                CurrencyInfoUiModel(
                    currencyName = TestFixtures.EUR.name,
                    currencyCode = TestFixtures.EUR.code,
                    selected = false
                )
            )

            val updated = awaitSuccess()
            assertTrue(updated.currencies.byCode("EUR").selected)
            assertEquals("1.1765", updated.exchangeRate?.rateRaw)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should show exchange rate for foreign currency and clear it for primary currency`() = testScope.runTest {
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            val initial = awaitSuccess()
            assertEquals(null, initial.exchangeRate)

            viewModel.onCurrencyClicked(
                CurrencyInfoUiModel(
                    currencyName = TestFixtures.EUR.name,
                    currencyCode = TestFixtures.EUR.code,
                    selected = false
                )
            )

            val eurState = awaitSuccess()
            val exchangeRate = checkNotNull(eurState.exchangeRate)
            assertEquals("EUR", exchangeRate.originalCurrencyCode)
            assertEquals("USD", exchangeRate.primaryCurrencyCode)
            assertEquals("1.1765", exchangeRate.rateRaw)
            assertFalse(exchangeRate.isCustom)

            viewModel.onCurrencyClicked(
                CurrencyInfoUiModel(
                    currencyName = TestFixtures.USD.name,
                    currencyCode = TestFixtures.USD.code,
                    selected = false
                )
            )

            val usdState = awaitSuccess()
            assertEquals(null, usdState.exchangeRate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should only mark exchange rate as custom when it differs from fetched rate`() = testScope.runTest {
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            viewModel.onCurrencyClicked(
                CurrencyInfoUiModel(
                    currencyName = TestFixtures.EUR.name,
                    currencyCode = TestFixtures.EUR.code,
                    selected = false
                )
            )

            assertFalse(checkNotNull(awaitSuccess().exchangeRate).isCustom)

            viewModel.onExchangeRateChanged("1.25")
            val overridden = checkNotNull(awaitSuccess().exchangeRate)
            assertEquals("1.25", overridden.rateRaw)
            assertTrue(overridden.isCustom)

            viewModel.onExchangeRateChanged("1.1765")
            assertFalse(checkNotNull(awaitSuccess().exchangeRate).isCustom)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should keep exchange rate available for very small foreign currency ratio`() = testScope.runTest {
        currentEventFlow.value = TestFixtures.eventDetails.copy(
            event = TestFixtures.event.copy(primaryCurrencyId = TestFixtures.EUR_SMALL.id),
            currencies = listOf(TestFixtures.EUR_SMALL, TestFixtures.JPY),
            primaryCurrency = TestFixtures.EUR_SMALL,
        )
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            val initial = awaitSuccess()
            assertEquals(null, initial.exchangeRate)

            viewModel.onCurrencyClicked(
                CurrencyInfoUiModel(
                    currencyName = TestFixtures.JPY.name,
                    currencyCode = TestFixtures.JPY.code,
                    selected = false,
                )
            )
            advanceUntilIdle()

            val updated = awaitSuccess()
            val exchangeRate = checkNotNull(updated.exchangeRate)
            assertEquals("JPY", exchangeRate.originalCurrencyCode)
            assertEquals("EUR", exchangeRate.primaryCurrencyCode)
            assertEquals("0.0055", exchangeRate.rateRaw)
            assertFalse(exchangeRate.isCustom)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should disable save when exchange rate is not positive`() = testScope.runTest {
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            viewModel.onCurrencyClicked(
                CurrencyInfoUiModel(
                    currencyName = TestFixtures.EUR.name,
                    currencyCode = TestFixtures.EUR.code,
                    selected = false
                )
            )
            awaitSuccess()

            viewModel.onWholeAmountChanged("10")
            awaitSuccess()

            viewModel.onExchangeRateChanged("0")
            assertFalse(awaitSuccess().canSave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should select payer when onPersonClicked is invoked`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            viewModel.onPersonClicked(
                PersonInfoUiModel(
                    TestFixtures.person2.id,
                    TestFixtures.person2.name,
                    selected = false
                )
            )

            val updated = awaitSuccess()
            assertTrue(updated.persons.byId(TestFixtures.person2.id).selected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should toggle subject person selection when onSubjectPersonClicked is invoked`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()
            val initial = awaitSuccess()
            assertTrue(initial.subjectPersons.all { it.selected }) // default: all selected

            // Toggle off person3
            viewModel.onSubjectPersonClicked(
                PersonInfoUiModel(
                    TestFixtures.person3.id,
                    TestFixtures.person3.name,
                    selected = true
                )
            )
            val afterToggleOff = awaitSuccess()
            assertFalse(afterToggleOff.subjectPersons.byId(TestFixtures.person3.id).selected)

            // Toggle person3 back on
            viewModel.onSubjectPersonClicked(
                PersonInfoUiModel(
                    TestFixtures.person3.id,
                    TestFixtures.person3.name,
                    selected = false
                )
            )
            val afterToggleOn = awaitSuccess()
            assertTrue(afterToggleOn.subjectPersons.byId(TestFixtures.person3.id).selected)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should update description when onDescriptionChanged is invoked`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()
            val initial = awaitSuccess()
            assertEquals("", initial.description)

            viewModel.onDescriptionChanged("Test expense")

            val updated = awaitSuccess()
            assertEquals("Test expense", updated.description)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should update expense type when onExpenseTypeClicked is invoked`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()
            val initial = awaitSuccess()
            assertEquals(ExpenseType.Spending, initial.expenseType)

            viewModel.onExpenseTypeClicked(ExpenseType.Replenishment)

            val updated = awaitSuccess()
            assertEquals(ExpenseType.Replenishment, updated.expenseType)
            cancelAndIgnoreRemainingEvents()
        }
    }
    // endregion

    // region Split Functionality Tests

    @Test
    fun `should create explicit split rows when equal split is turned off`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            viewModel.onEqualSplitChange(false)
            viewModel.onWholeAmountChanged("10")

            val uiModel = awaitSuccess()
            assertFalse(uiModel.equalSplit)
            assertEquals(3, uiModel.split.size)
            assertTrue(uiModel.split.all { it.amount == "3.33" })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should update only targeted row when onSplitAmountChanged is invoked`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            viewModel.onEqualSplitChange(false)
            viewModel.onWholeAmountChanged("9")
            val beforeSplitChange = awaitSuccess()
            assertEquals(listOf("3", "3", "3"), beforeSplitChange.split.map { it.amount })

            val person2Row = beforeSplitChange.split.byPersonId(TestFixtures.person2.id)
            viewModel.onSplitAmountChanged(
                ExpenseSplitWithPersonUiModel(person = person2Row.person, amount = person2Row.amount),
                amount = "4.5"
            )

            val afterSplitChange = awaitSuccess()
            val splitAmounts = afterSplitChange.split
            assertEquals("4.5", splitAmounts.byPersonId(TestFixtures.person2.id).amount)
            assertEquals("3", splitAmounts.byPersonId(TestFixtures.person1.id).amount)
            assertEquals("3", splitAmounts.byPersonId(TestFixtures.person3.id).amount)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should preserve custom amounts when subject persons change`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            viewModel.onEqualSplitChange(false)
            viewModel.onWholeAmountChanged("9")
            val initialState = awaitSuccess()

            // Modify one split amount
            val person1Row = initialState.split.byPersonId(TestFixtures.person1.id)
            viewModel.onSplitAmountChanged(person1Row, "5")
            awaitSuccess()

            // Remove person2 from subjects
            viewModel.onSubjectPersonClicked(
                PersonInfoUiModel(
                    TestFixtures.person2.id,
                    TestFixtures.person2.name,
                    selected = true
                )
            )

            val updatedState = awaitSuccess()
            assertEquals(2, updatedState.split.size) // only person1 and person3
            assertEquals("5", updatedState.split.byPersonId(TestFixtures.person1.id).amount)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should recalculate equal split when whole amount changes`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            viewModel.onEqualSplitChange(false)
            viewModel.onWholeAmountChanged("15")
            val initialState = awaitSuccess()
            assertEquals(listOf("5", "5", "5"), initialState.split.map { it.amount })

            viewModel.onWholeAmountChanged("12")

            val updatedState = awaitSuccess()
            assertEquals(listOf("4", "4", "4"), updatedState.split.map { it.amount })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should preserve manual edits when whole amount changes in custom split`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            viewModel.onEqualSplitChange(false)
            viewModel.onWholeAmountChanged("15")
            val initialState = awaitSuccess()

            // Manually edit one person's amount
            val person1Row = initialState.split.byPersonId(TestFixtures.person1.id)
            viewModel.onSplitAmountChanged(person1Row, "10")
            awaitSuccess()

            // Change whole amount
            viewModel.onWholeAmountChanged("30")

            val updatedState = awaitSuccess()
            assertEquals("10", updatedState.split.byPersonId(TestFixtures.person1.id).amount)
            assertEquals("5", updatedState.split.byPersonId(TestFixtures.person2.id).amount)
            assertEquals("5", updatedState.split.byPersonId(TestFixtures.person3.id).amount)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should clear custom split when switching back to equal split`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            // Set up custom split with manual amounts
            viewModel.onEqualSplitChange(false)
            viewModel.onWholeAmountChanged("15")
            val customState = awaitSuccess()

            val person1Row = customState.split.byPersonId(TestFixtures.person1.id)
            viewModel.onSplitAmountChanged(person1Row, "10")
            awaitSuccess()

            // Switch back to equal split
            viewModel.onEqualSplitChange(true)

            val equalState = awaitSuccess()
            assertTrue(equalState.equalSplit)
            assertTrue(equalState.canSave) // Should still be saveable with valid whole amount

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should handle empty split when all subject persons are deselected`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            viewModel.onEqualSplitChange(false)

            // Deselect all subject persons
            viewModel.onSubjectPersonClicked(PersonInfoUiModel(TestFixtures.person1.id, TestFixtures.person1.name, true))
            viewModel.onSubjectPersonClicked(PersonInfoUiModel(TestFixtures.person2.id, TestFixtures.person2.name, true))
            viewModel.onSubjectPersonClicked(PersonInfoUiModel(TestFixtures.person3.id, TestFixtures.person3.name, true))

            val updatedState = awaitSuccess()
            assertEquals(0, updatedState.split.size)
            assertFalse(updatedState.canSave)

            cancelAndIgnoreRemainingEvents()
        }
    }
    // endregion

    // region Amount Validation Tests

    @Test
    fun `should confirm equal split expense creation and navigate back`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()
            viewModel.onEqualSplitChange(true)
            viewModel.onWholeAmountChanged("12.0")
            awaitSuccess()

            // When
            viewModel.onConfirmClicked()
            runCurrent()

            // Then
            coVerify(exactly = 1) {
                addEqualSplitExpenseUseCase.addExpense(
                    event = TestFixtures.event,
                    wholeAmount = "12.0".trim().toBigDecimal(),
                    expenseType = ExpenseType.Spending,
                    description = any(),
                    selectedSubjectPersons = match { it.size == 3 && it.containsAll(listOf(TestFixtures.person1, TestFixtures.person2, TestFixtures.person3)) },
                    selectedCurrency = TestFixtures.USD,
                    selectedPerson = TestFixtures.person1,
                    overrideRate = null,
                )
            }
            coVerify(exactly = 1) { navigationController.popBackStack() }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should confirm custom split expense creation and navigate back`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()
            viewModel.onEqualSplitChange(false)
            viewModel.onWholeAmountChanged("9")
            val uiModel = awaitSuccess()

            // Overwrite two rows
            val person1Row = uiModel.split.byPersonId(TestFixtures.person1.id)
            val person2Row = uiModel.split.byPersonId(TestFixtures.person2.id)
            viewModel.onSplitAmountChanged(person1Row, "4")
            viewModel.onSplitAmountChanged(person2Row, "5")
            awaitSuccess()

            // When
            viewModel.onConfirmClicked()
            runCurrent()

            // Then
            coVerify(exactly = 1) {
                addCustomSplitExpenseUseCase.addExpense(
                    event = TestFixtures.event,
                    expenseType = ExpenseType.Spending,
                    description = any(),
                    selectedCurrency = TestFixtures.USD,
                    selectedPerson = TestFixtures.person1,
                    personWithAmountSplit = match { list ->
                        val map = list.associate { it.person to it.amount }
                        map[TestFixtures.person1] == "4".toBigDecimal() &&
                            map[TestFixtures.person2] == "5".toBigDecimal() &&
                            map.containsKey(TestFixtures.person3)
                    },
                    overrideRate = null,
                )
            }
            coVerify(exactly = 1) { navigationController.popBackStack() }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should pass exchange rate override for equal split confirmation`() = testScope.runTest {
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()
            viewModel.onCurrencyClicked(
                CurrencyInfoUiModel(
                    currencyName = TestFixtures.EUR.name,
                    currencyCode = TestFixtures.EUR.code,
                    selected = false
                )
            )
            awaitSuccess()
            viewModel.onWholeAmountChanged("10")
            awaitSuccess()
            viewModel.onExchangeRateChanged("1.25")
            awaitSuccess()

            viewModel.onConfirmClicked()
            runCurrent()

            coVerify(exactly = 1) {
                addEqualSplitExpenseUseCase.addExpense(
                    event = TestFixtures.event,
                    wholeAmount = "10".toBigDecimal(),
                    expenseType = ExpenseType.Spending,
                    description = any(),
                    selectedSubjectPersons = any(),
                    selectedCurrency = TestFixtures.EUR,
                    selectedPerson = TestFixtures.person1,
                    overrideRate = BigDecimal.parseString("1.25"),
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should pass exchange rate override for custom split confirmation`() = testScope.runTest {
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()
            viewModel.onCurrencyClicked(
                CurrencyInfoUiModel(
                    currencyName = TestFixtures.EUR.name,
                    currencyCode = TestFixtures.EUR.code,
                    selected = false
                )
            )
            awaitSuccess()
            viewModel.onEqualSplitChange(false)
            viewModel.onWholeAmountChanged("9")
            val uiModel = awaitSuccess()
            val person1Row = uiModel.split.byPersonId(TestFixtures.person1.id)
            val person2Row = uiModel.split.byPersonId(TestFixtures.person2.id)
            viewModel.onSplitAmountChanged(person1Row, "4")
            viewModel.onSplitAmountChanged(person2Row, "5")
            awaitSuccess()
            viewModel.onExchangeRateChanged("1.25")
            awaitSuccess()

            viewModel.onConfirmClicked()
            runCurrent()

            coVerify(exactly = 1) {
                addCustomSplitExpenseUseCase.addExpense(
                    event = TestFixtures.event,
                    expenseType = ExpenseType.Spending,
                    description = any(),
                    selectedCurrency = TestFixtures.EUR,
                    selectedPerson = TestFixtures.person1,
                    personWithAmountSplit = any(),
                    overrideRate = BigDecimal.parseString("1.25"),
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should not confirm when required amounts are missing`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            viewModel.onEqualSplitChange(true)
            awaitSuccess()

            // When
            viewModel.onConfirmClicked()
            runCurrent()

            // Then
            coVerify(exactly = 0) { addEqualSplitExpenseUseCase.addExpense(any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { addCustomSplitExpenseUseCase.addExpense(any(), any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { navigationController.popBackStack() }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should pre-fill replenishment fields correctly`() = testScope.runTest {
        // Given - from person2 → to person3 in EUR amount 7.5
        val replenishmentDestination = AddExpensePaneDestination.Replenishment(
            fromPersonId = TestFixtures.person2.id,
            toPersonId = TestFixtures.person3.id,
            currencyCode = TestFixtures.EUR.code,
            amount = "7.5"
        )
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel(replenishmentDestination)
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()
            val uiModel = awaitSuccess()

            assertEquals(ExpenseType.Replenishment, uiModel.expenseType)
            assertTrue(uiModel.currencies.byCode("EUR").selected)
            assertTrue(uiModel.persons.byId(TestFixtures.person2.id).selected)
            assertEquals("expenses_repayment_from${TestFixtures.person2.name}", uiModel.description)
            assertFalse(uiModel.equalSplit)
            assertEquals(1, uiModel.split.size)
            assertEquals(TestFixtures.person3.id, uiModel.split.first().person.personId)
            assertEquals("7.5", uiModel.split.first().amount)
            assertEquals("7.5", uiModel.wholeAmount)
            assertEquals("1.1765", uiModel.exchangeRate?.rateRaw)
            assertTrue(uiModel.canSave)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should indicate save capability based on equal split amount entry`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            val initial = awaitSuccess()
            assertFalse(initial.canSave)

            // When
            viewModel.onWholeAmountChanged("10")

            // Then
            val updated = awaitSuccess()
            assertTrue(updated.canSave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should keep save disabled for whitespace-only amount`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            val initial = awaitSuccess()
            assertFalse(initial.canSave)

            // When
            viewModel.onWholeAmountChanged("    ")

            // Then
            expectNoEvents()
        }
    }

    @Test
    fun `should require all custom split amounts for save capability`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            // Switch to custom split and prefill amounts
            viewModel.onEqualSplitChange(false)
            viewModel.onWholeAmountChanged("9")
            val customSplit = awaitSuccess()
            assertTrue(customSplit.canSave)

            // When - clear one amount
            val firstPerson = customSplit.split.first()
            viewModel.onSplitAmountChanged(firstPerson, "   ")

            // Then - save should be disabled
            val updated = awaitSuccess()
            assertFalse(updated.canSave)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should handle invalid amount input gracefully`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            // When - set invalid amount
            viewModel.onWholeAmountChanged("invalid123")

            // Then - should store raw value
            val updated = awaitSuccess()
            assertEquals("invalid123", updated.wholeAmount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should handle negative amounts correctly`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            // When - set negative amount
            viewModel.onWholeAmountChanged("-5.50")

            // Then - should store the negative amount
            val updated = awaitSuccess()
            assertEquals("-5.50", updated.wholeAmount)
            assertTrue(updated.canSave) // negative amounts are valid for refunds

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should handle zero amounts correctly`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            // When - set zero amount
            viewModel.onWholeAmountChanged("0")

            // Then
            val updated = awaitSuccess()
            assertEquals("0", updated.wholeAmount)
            assertTrue(updated.canSave) // zero amounts should be valid

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should handle high precision decimal amounts correctly`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            // When - set high precision decimal
            viewModel.onWholeAmountChanged("10.123456789")

            // Then
            val updated = awaitSuccess()
            assertEquals("10.123456789", updated.wholeAmount)
            assertTrue(updated.canSave)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should use default description when empty description is confirmed`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()
            viewModel.onWholeAmountChanged("10")
            awaitSuccess()

            // When - confirm with empty description
            viewModel.onConfirmClicked()
            runCurrent()

            // Then - should use the default "no description" string
            coVerify(exactly = 1) {
                addEqualSplitExpenseUseCase.addExpense(
                    event = TestFixtures.event,
                    wholeAmount = "10".toBigDecimal(),
                    expenseType = ExpenseType.Spending,
                    description = "expenses_no_description", // default from StringProvider mock
                    selectedSubjectPersons = any(),
                    selectedCurrency = TestFixtures.USD,
                    selectedPerson = TestFixtures.person1,
                    overrideRate = null,
                )
            }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should use default description for whitespace-only description`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()
            viewModel.onDescriptionChanged("   \t\n   ")
            viewModel.onWholeAmountChanged("10")
            awaitSuccess()

            // When
            viewModel.onConfirmClicked()
            runCurrent()

            // Then
            coVerify(exactly = 1) {
                addEqualSplitExpenseUseCase.addExpense(
                    description = "expenses_no_description",
                    event = any(),
                    wholeAmount = any(),
                    expenseType = any(),
                    selectedSubjectPersons = any(),
                    selectedCurrency = any(),
                    selectedPerson = any(),
                    overrideRate = null,
                )
            }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should recalculate equal split amounts when whole amount changes`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()
            viewModel.onEqualSplitChange(false)
            viewModel.onWholeAmountChanged("15")
            val initial = awaitSuccess()
            assertEquals(listOf("5", "5", "5"), initial.split.map { it.amount })

            // When - change whole amount
            viewModel.onWholeAmountChanged("12")

            // Then - split should recalculate
            val updated = awaitSuccess()
            assertEquals(listOf("4", "4", "4"), updated.split.map { it.amount })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should preserve custom split manual edits when whole amount changes`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()
            viewModel.onEqualSplitChange(false)
            viewModel.onWholeAmountChanged("15")
            val initial = awaitSuccess()

            // Manually edit one person's amount
            val person1Row = initial.split.byPersonId(TestFixtures.person1.id)
            viewModel.onSplitAmountChanged(person1Row, "10")
            awaitSuccess()

            // When - change whole amount (should not affect manually edited amounts)
            viewModel.onWholeAmountChanged("30")

            // Then - manual edit should be preserved
            val updated = awaitSuccess()
            assertEquals("10", updated.split.byPersonId(TestFixtures.person1.id).amount)
            // Other amounts should remain as they were
            assertEquals("5", updated.split.byPersonId(TestFixtures.person2.id).amount)
            assertEquals("5", updated.split.byPersonId(TestFixtures.person3.id).amount)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should result in empty split when all subject persons are deselected`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()
            viewModel.onEqualSplitChange(false)

            // When - deselect all subject persons
            viewModel.onSubjectPersonClicked(PersonInfoUiModel(TestFixtures.person1.id, TestFixtures.person1.name, true))
            viewModel.onSubjectPersonClicked(PersonInfoUiModel(TestFixtures.person2.id, TestFixtures.person2.name, true))
            viewModel.onSubjectPersonClicked(PersonInfoUiModel(TestFixtures.person3.id, TestFixtures.person3.name, true))

            // Then - split should be empty and save should be disabled
            val updated = awaitSuccess()
            assertEquals(0, updated.split.size)
            assertFalse(updated.canSave)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should handle very large amounts correctly`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            // When - set very large amount
            viewModel.onWholeAmountChanged("999999999.99")

            // Then
            val updated = awaitSuccess()
            assertEquals("999999999.99", updated.wholeAmount)
            assertTrue(updated.canSave)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should fail gracefully when replenishment has invalid person ids`() = testScope.runTest {
        // Given - replenishment with non-existent persons
        val replenishmentDestination = AddExpensePaneDestination.Replenishment(
            fromPersonId = 999L, // Non-existent
            toPersonId = 888L,   // Non-existent
            currencyCode = TestFixtures.USD.code,
            amount = "10"
        )
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel(replenishmentDestination)
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()
            awaitErrorState()

            expectNoEvents()
        }
    }

    @Test
    fun `should fall back to primary currency when replenishment has invalid currency code`() = testScope.runTest {
        // Given - replenishment with non-existent currency
        val replenishmentDestination = AddExpensePaneDestination.Replenishment(
            fromPersonId = TestFixtures.person1.id,
            toPersonId = TestFixtures.person2.id,
            currencyCode = "XYZ", // Non-existent
            amount = "15"
        )
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel(replenishmentDestination)
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()
            val uiModel = awaitSuccess()

            // Should fall back to primary currency (USD)
            assertTrue(uiModel.currencies.byCode("USD").selected)
            assertFalse(uiModel.currencies.byCode("EUR").selected)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should clear custom amounts when switching from custom back to equal split`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            // Set up custom split
            viewModel.onEqualSplitChange(false)
            viewModel.onWholeAmountChanged("15")
            val customState = awaitSuccess()

            // Modify some custom amounts
            val person1Row = customState.split.byPersonId(TestFixtures.person1.id)
            viewModel.onSplitAmountChanged(person1Row, "10")
            awaitSuccess()

            // When - switch back to equal split
            viewModel.onEqualSplitChange(true)

            // Then - should be back to equal split mode
            val equalState = awaitSuccess()
            assertTrue(equalState.equalSplit)
            assertTrue(equalState.canSave) // Should still be saveable with valid whole amount

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should reflect changes when event details update dynamically`() = testScope.runTest {
        // Given
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()

            // Initial event
            currentEventFlow.value = TestFixtures.eventDetails
            val initial = awaitSuccess()
            assertEquals(3, initial.persons.size)
            assertEquals(2, initial.currencies.size)

            // When - update event with different details
            val newPerson = Person(id = 4, serverId = "s4", name = "Bob")
            val newCurrency = Currency(id = 102, serverId = null, code = "GBP", name = "British Pound", rate = BigDecimal.ONE)
            val updatedEventDetails = TestFixtures.eventDetails.copy(
                persons = TestFixtures.eventDetails.persons + newPerson,
                currencies = TestFixtures.eventDetails.currencies + newCurrency
            )
            currentEventFlow.value = updatedEventDetails
            advanceUntilIdle()

            // Then - state should reflect the new persons and currencies
            val updated = awaitSuccess()
            assertEquals(4, updated.persons.size)
            assertEquals(3, updated.currencies.size)
            assertTrue(updated.persons.any { it.personName == "Bob" })
            assertTrue(updated.currencies.any { it.currencyCode == "GBP" })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should validate all amounts are present when confirming custom split`() = testScope.runTest {
        // Given
        currentEventFlow.value = TestFixtures.eventDetails
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()

            viewModel.onEqualSplitChange(false)
            viewModel.onWholeAmountChanged("10")
            val customState = awaitSuccess()

            // Clear one person's amount to make it invalid
            val person1Row = customState.split.byPersonId(TestFixtures.person1.id)
            viewModel.onSplitAmountChanged(person1Row, "")
            val invalidState = awaitSuccess()
            assertFalse(invalidState.canSave)

            // When - try to confirm with invalid state
            viewModel.onConfirmClicked()
            runCurrent()

            // Then - should not create expense or navigate
            coVerify(exactly = 0) { addCustomSplitExpenseUseCase.addExpense(any(), any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { navigationController.popBackStack() }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should handle missing currency gracefully during confirmation`() = testScope.runTest {
        // Given - Create scenario where no currency is selected (edge case)
        currentEventFlow.value = TestFixtures.eventDetails.copy(currencies = emptyList())
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitLoading()
            awaitSuccess()
            viewModel.onWholeAmountChanged("10")
            awaitSuccess()

            // When
            viewModel.onConfirmClicked()
            runCurrent()

            // Then - should not call expense creation
            coVerify(exactly = 0) { addEqualSplitExpenseUseCase.addExpense(any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { navigationController.popBackStack() }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should handle missing selected person gracefully during confirmation`() = testScope.runTest {
        // Given - This is a defensive test - in normal flow there should always be a selected person
        currentEventFlow.value = TestFixtures.eventDetails
        currentPersonIdFlow.value = 999L // Non-existent person ID
        val viewModel = createViewModel()
        runCurrent()
        advanceUntilIdle()

        // When & Then
        viewModel.state.test {
            awaitLoading()
            awaitErrorState()

            expectNoEvents()
        }
    }
    // endregion

    // region Helper Methods
    private fun createViewModel(
        replenishment: AddExpensePaneDestination.Replenishment? = null
    ): AddExpenseViewModel {
        return AddExpenseViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            addEqualSplitExpenseUseCase = addEqualSplitExpenseUseCase,
            addCustomSplitExpenseUseCase = addCustomSplitExpenseUseCase,
            settingsRepository = settingsRepository,
            replenishment = replenishment,
            stringProvider = object : com.inwords.expenses.core.ui.utils.StringProvider {
                override suspend fun getString(stringResource: StringResource): String {
                    return stringResource.key
                }

                override suspend fun getString(stringResource: StringResource, vararg formatArgs: Any): String {
                    return stringResource.key + formatArgs.joinToString()
                }
            },
            viewModelScope = this.testScope.backgroundScope,
        )
    }

    private suspend fun ReceiveTurbine<SimpleScreenState<AddExpensePaneUiModel>>.awaitErrorState() {
        val _ = assertIs<SimpleScreenState.Error>(awaitItem())
    }

    private suspend fun ReceiveTurbine<SimpleScreenState<AddExpensePaneUiModel>>.awaitLoading() {
        val _ = assertIs<SimpleScreenState.Loading>(awaitItem())
    }

    private suspend fun ReceiveTurbine<SimpleScreenState<AddExpensePaneUiModel>>.awaitSuccess(): AddExpensePaneUiModel {
        val state = awaitItem()
        return assertIs<SimpleScreenState.Success<AddExpensePaneUiModel>>(state).data
    }

    private fun List<CurrencyInfoUiModel>.byCode(code: String) = first { it.currencyCode == code }

    private fun List<PersonInfoUiModel>.byId(id: Long) = first { it.personId == id }

    private fun List<ExpenseSplitWithPersonUiModel>.byPersonId(id: Long) = first { it.person.personId == id }
    // endregion
}
