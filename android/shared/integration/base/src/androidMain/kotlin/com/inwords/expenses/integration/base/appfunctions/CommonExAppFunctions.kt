package com.inwords.expenses.integration.base.appfunctions

import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionElementAlreadyExistsException
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.service.AppFunction
import com.inwords.expenses.core.locator.ComponentsMap
import com.inwords.expenses.core.locator.getComponent
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.core.utils.toPlainDecimalString
import com.inwords.expenses.feature.events.api.EventsComponent
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.api.ExpensesComponent
import com.inwords.expenses.feature.expenses.domain.calculateBarterAccumulatedDebtSummaries
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal class CommonExAppFunctions {

    private val eventsComponent: EventsComponent
        get() = ComponentsMap.getComponent()

    private val expensesComponent: ExpensesComponent
        get() = ComponentsMap.getComponent()

    /**
     * Lists currencies that can be used when creating a new event.
     *
     * @return All available currencies, sorted by currency code.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listCurrencies(@Suppress("unused") appFunctionContext: AppFunctionContext): List<AppFunctionCurrency> = withContext(IO) {
        loadAvailableCurrencies().map { currency ->
            currency.toAppFunctionCurrency()
        }
    }

    /**
     * Creates a new expense sharing event with an explicit primary currency.
     *
     * @param name The event name to create.
     * @param primaryCurrencyCode The primary currency code for the event, for example `USD` or `EUR`.
     * @param ownerName The name or nickname that identifies the event owner inside the event.
     * @return A structured summary of the created event.
     * @throws AppFunctionInvalidArgumentException If the event name or owner name is invalid.
     * @throws AppFunctionElementNotFoundException If the requested currency is unavailable.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun createEvent(
        @Suppress("unused") appFunctionContext: AppFunctionContext,
        name: String,
        primaryCurrencyCode: String,
        ownerName: String,
    ): AppFunctionEvent = withContext(IO) {
        val normalizedName = name.requireTrimmedValue("Event name")
        val normalizedOwnerName = ownerName.requireTrimmedValue("Owner name")
        val currency = resolveCurrency(primaryCurrencyCode)

        val createdEvent = eventsComponent.createEventFromParametersUseCaseLazy.value.createEvent(
            name = normalizedName,
            owner = normalizedOwnerName,
            primaryCurrencyId = currency.id,
            otherPersons = emptyList(),
        )

        createdEvent.toAppFunctionEvent()
    }

    /**
     * Lists all locally available expense events.
     *
     * @return All local events with participant counts and primary currency codes when available.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listEvents(@Suppress("unused") appFunctionContext: AppFunctionContext): List<AppFunctionEvent> = withContext(IO) {
        val events = eventsComponent.getEventsUseCaseLazy.value.getEvents().first()
        coroutineScope {
            events.map { event ->
                async {
                    val details = eventsComponent.eventsLocalStore.value.getEventWithDetails(event.id)
                    details?.toAppFunctionEvent() ?: event.toAppFunctionEvent()
                }
            }.awaitAll()
        }
    }

    /**
     * Calculates net debts for a specific event.
     *
     * @param eventName The target event name.
     * @return The event debts. Returns an empty list when the event currently has no debts.
     * @throws AppFunctionInvalidArgumentException If the event name is blank.
     * @throws AppFunctionElementNotFoundException If the event is missing.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getDebts(
        @Suppress("unused") appFunctionContext: AppFunctionContext,
        eventName: String,
    ): List<AppFunctionDebt> = withContext(IO) {
        val eventDetails = findEventDetailsByName(eventName)
        val expenses = expensesComponent.getExpensesUseCaseLazy.value
            .getExpensesFlow(eventDetails.event.id)
            .first()

        val debts = calculateBarterAccumulatedDebtSummaries(
            expenses = expenses,
            primaryCurrency = eventDetails.primaryCurrency,
        )
        debts.map { debt ->
            AppFunctionDebt(
                debtorName = debt.debtor.name,
                creditorName = debt.creditor.name,
                amount = debt.amount.toPlainDecimalString(),
                currencyCode = debt.currency.code,
            )
        }
    }

    /**
     * Adds a participant to an existing event.
     *
     * @param eventName The target event name.
     * @param participantName The participant name to add.
     * @return A structured summary of the participant addition.
     * @throws AppFunctionInvalidArgumentException If either name is blank.
     * @throws AppFunctionElementNotFoundException If the event is missing.
     * @throws AppFunctionElementAlreadyExistsException If the participant already exists in the event.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addParticipant(
        @Suppress("unused") appFunctionContext: AppFunctionContext,
        eventName: String,
        participantName: String,
    ): AppFunctionParticipantMutation = withContext(IO) {
        val normalizedParticipantName = participantName.requireTrimmedValue("Participant name")
        val eventDetails = findEventDetailsByName(eventName)
        val event = eventDetails.event
        if (eventDetails.persons.any { person -> person.name.equals(normalizedParticipantName, ignoreCase = true) }) {
            throw AppFunctionElementAlreadyExistsException(
                "Participant '$normalizedParticipantName' already exists in event '${event.name}'.",
            )
        }

        val person = Person(
            id = 0L,
            serverId = null,
            name = normalizedParticipantName,
        )
        eventsComponent.eventsLocalStore.value.insertPersonsWithCrossRefs(
            eventId = event.id,
            persons = listOf(person),
            inTransaction = true,
        )

        expensesComponent.requestExpensesRefreshUseCaseLazy.value.requestRefresh(event)

        AppFunctionParticipantMutation(
            event = eventDetails.toAppFunctionEvent(participantCountOverride = eventDetails.persons.size + 1),
            participantName = normalizedParticipantName,
        )
    }

    /**
     * Adds an equal-split expense in the event primary currency.
     *
     * @param eventName The target event name.
     * @param amount The total expense amount in the event primary currency, as a decimal string (e.g. "12.50").
     * @param description The expense description.
     * @param payerName The participant who paid the expense.
     * @return A structured summary of the created expense.
     * @throws AppFunctionInvalidArgumentException If the names or description are blank or amount is not positive.
     * @throws AppFunctionElementNotFoundException If the event or payer cannot be found.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addExpense(
        @Suppress("unused") appFunctionContext: AppFunctionContext,
        eventName: String,
        amount: String,
        description: String,
        payerName: String,
    ): AppFunctionExpenseMutation = withContext(IO) {
        val normalizedDescription = description.requireTrimmedValue("Expense description")
        val normalizedPayerName = payerName.requireTrimmedValue("Payer name")
        val amountBigDecimal = parsePositiveAmount(amount)

        val eventDetails = findEventDetailsByName(eventName)
        val payer = eventDetails.requirePersonByName(
            roleName = "Payer",
            personName = normalizedPayerName,
        )

        expensesComponent.addEqualSplitExpenseUseCaseLazy.value.addExpense(
            event = eventDetails.event,
            wholeAmount = amountBigDecimal,
            expenseType = ExpenseType.Spending,
            description = normalizedDescription,
            selectedSubjectPersons = eventDetails.persons,
            selectedCurrency = eventDetails.primaryCurrency,
            selectedPerson = payer,
        )
        expensesComponent.requestExpensesRefreshUseCaseLazy.value.requestRefresh(eventDetails.event)

        AppFunctionExpenseMutation(
            event = eventDetails.toAppFunctionEvent(),
            payerName = payer.name,
            description = normalizedDescription,
            amount = amountBigDecimal.toPlainDecimalString(),
            currencyCode = eventDetails.primaryCurrency.code,
            splitBetweenParticipants = eventDetails.persons.size,
        )
    }

    private suspend fun loadAvailableCurrencies(): List<Currency> {
        return eventsComponent.getCurrenciesUseCaseLazy.value.getCurrencies().first()
            .sortedBy { currency -> currency.code }
    }

    private suspend fun resolveCurrency(primaryCurrencyCode: String): Currency {
        val normalizedCurrencyCode = primaryCurrencyCode
            .requireTrimmedValue("Primary currency code")
            .uppercase()
        val currencies = loadAvailableCurrencies()
        if (currencies.isEmpty()) {
            throw AppFunctionElementNotFoundException("No currencies are available to create a new event.")
        }

        return currencies.firstOrNull { currency ->
            currency.code.equals(normalizedCurrencyCode, ignoreCase = true)
        } ?: throw AppFunctionElementNotFoundException(
            "Currency '$normalizedCurrencyCode' is not available. Supported currency codes: " +
                currencies.joinToString(", ") { currency -> currency.code },
        )
    }

    private suspend fun findEventDetailsByName(eventName: String): EventDetails {
        val normalizedEventName = eventName.requireTrimmedValue("Event name")
        val allEvents = eventsComponent.eventsLocalStore.value.getEventsFlow().first()
        val matches = allEvents.filter { it.name.equals(normalizedEventName, ignoreCase = true) }
        when {
            matches.isEmpty() -> throw AppFunctionElementNotFoundException("Event '$normalizedEventName' was not found.")
            matches.size > 1 -> throw AppFunctionInvalidArgumentException(
                "Multiple events named '$normalizedEventName' exist (IDs: ${matches.joinToString { it.id.toString() }}). " +
                    "Delete duplicates in the app to disambiguate.",
            )
        }
        val event = matches.single()
        return eventsComponent.eventsLocalStore.value.getEventWithDetails(event.id)
            ?: throw AppFunctionAppUnknownException(
                "Event '${event.name}' details could not be loaded after resolving the event.",
            )
    }

    private fun EventDetails.requirePersonByName(
        roleName: String,
        personName: String,
    ): Person {
        val matches = persons.filter { person ->
            person.name.equals(personName, ignoreCase = true)
        }
        return when {
            matches.isEmpty() -> throw AppFunctionElementNotFoundException(
                "$roleName '$personName' was not found in event '${event.name}'. Available participants: " +
                    persons.joinToString(", ") { person -> person.name },
            )

            matches.size > 1 -> throw AppFunctionInvalidArgumentException(
                "$roleName '$personName' matches multiple participants in event '${event.name}'. " +
                    "Use distinct participant names to disambiguate.",
            )

            else -> matches.single()
        }
    }

    private fun EventDetails.toAppFunctionEvent(
        participantCountOverride: Int = persons.size,
    ): AppFunctionEvent = event.toAppFunctionEvent(
        participantCount = participantCountOverride,
        primaryCurrencyCode = primaryCurrency.code,
    )

    private fun Event.toAppFunctionEvent(
        participantCount: Int? = null,
        primaryCurrencyCode: String? = null,
    ): AppFunctionEvent = AppFunctionEvent(
        id = id,
        name = name,
        participantCount = participantCount,
        primaryCurrencyCode = primaryCurrencyCode,
    )

    private fun Currency.toAppFunctionCurrency(): AppFunctionCurrency = AppFunctionCurrency(
        code = code,
        name = name,
    )

    private fun String.requireTrimmedValue(fieldName: String): String {
        val trimmedValue = trim()
        if (trimmedValue.isEmpty()) {
            throw AppFunctionInvalidArgumentException("$fieldName cannot be empty.")
        }
        return trimmedValue
    }

    private fun parsePositiveAmount(amount: String): BigDecimal {
        val trimmed = amount.trim()
        if (trimmed.isEmpty()) {
            throw AppFunctionInvalidArgumentException("Expense amount cannot be empty.")
        }
        return try {
            val parsed = BigDecimal.parseString(trimmed)
            if (parsed <= BigDecimal.ZERO) {
                throw AppFunctionInvalidArgumentException("Expense amount must be greater than zero.")
            }
            parsed
        } catch (e: AppFunctionInvalidArgumentException) {
            throw e
        } catch (_: NumberFormatException) {
            throw AppFunctionInvalidArgumentException(
                "Expense amount must be a valid positive decimal (e.g. \"12.50\").",
            )
        } catch (_: ArithmeticException) {
            throw AppFunctionInvalidArgumentException(
                "Expense amount must be a valid positive decimal (e.g. \"12.50\").",
            )
        } catch (_: IndexOutOfBoundsException) {
            throw AppFunctionInvalidArgumentException(
                "Expense amount must be a valid positive decimal (e.g. \"12.50\").",
            )
        }
    }
}
