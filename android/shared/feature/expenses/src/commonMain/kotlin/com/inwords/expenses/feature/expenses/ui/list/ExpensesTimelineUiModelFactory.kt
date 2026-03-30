package com.inwords.expenses.feature.expenses.ui.list

import androidx.compose.ui.text.intl.Locale
import com.inwords.expenses.core.ui.utils.DefaultStringProvider
import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.core.ui.utils.formatRelativeShortDate
import com.inwords.expenses.core.ui.utils.getFullDateFormat
import com.inwords.expenses.core.utils.asImmutableListAdapter
import com.inwords.expenses.core.utils.sumOf
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.model.ExpensesDetails
import com.inwords.expenses.feature.expenses.ui.common.DebtShortUiModel
import com.inwords.expenses.feature.expenses.ui.converter.toUiModel
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.Expenses
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.Expenses.DayChipUiModel
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneUiModel.Expenses.DaySectionUiModel
import com.inwords.expenses.feature.expenses.ui.utils.toRoundedString
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_today
import expenses.shared.feature.expenses.generated.resources.expenses_yesterday
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

internal class ExpensesTimelineUiModelFactory(
    private val stringProvider: StringProvider = DefaultStringProvider,
    private val timeZoneProvider: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val localeProvider: () -> Locale = { Locale.current },
    private val nowProvider: () -> Instant = { Clock.System.now() },
) {

    suspend fun create(
        expensesDetails: ExpensesDetails,
        currentPersonId: Long,
        debts: List<DebtShortUiModel>,
    ): Expenses {
        val timeZone = timeZoneProvider.invoke()
        val locale = localeProvider.invoke()
        val currentLocalDate = nowProvider.invoke().toLocalDateTime(timeZone).date
        val primaryCurrencyName = expensesDetails.event.primaryCurrency.name
        val primaryCurrencyCode = expensesDetails.event.primaryCurrency.code

        val sections = buildSections(
            expenses = expensesDetails.expenses,
            currentPersonId = currentPersonId,
            primaryCurrencyName = primaryCurrencyName,
            primaryCurrencyCode = primaryCurrencyCode,
            timeZone = timeZone,
            locale = locale,
        )
        val chips = buildDayChips(
            sections = sections,
            currentLocalDate = currentLocalDate,
            locale = locale,
        )
        return Expenses(
            eventId = expensesDetails.event.event.id,
            eventName = expensesDetails.event.event.name,
            currentPersonId = currentPersonId,
            currentPersonName = expensesDetails.event.persons.first { it.id == currentPersonId }.name,
            debts = debts.asImmutableListAdapter(),
            totalSpending = formatAmount(
                amount = expensesDetails.expenses
                    .filter { it.expenseType == ExpenseType.Spending }
                    .sumOf { it.totalAmount },
                currencyCode = primaryCurrencyCode,
            ),
            dayChips = chips.asImmutableListAdapter(),
            daySections = sections.map { it.uiModel }.asImmutableListAdapter(),
            isRefreshing = false,
        )
    }

    private suspend fun buildDayChips(
        sections: List<DaySectionBuildResult>,
        currentLocalDate: LocalDate,
        locale: Locale,
    ): List<DayChipUiModel> {
        val todayLabel = stringProvider.getString(Res.string.expenses_today)
        val yesterdayLabel = stringProvider.getString(Res.string.expenses_yesterday)

        return sections.map { section ->
            DayChipUiModel(
                dayKey = section.dayKey,
                label = section.localDate.formatRelativeShortDate(
                    currentLocalDate = currentLocalDate,
                    todayLabel = todayLabel,
                    yesterdayLabel = yesterdayLabel,
                    locale = locale,
                ),
                isSelected = section == sections.firstOrNull(),
            )
        }
    }

    private fun buildSections(
        expenses: List<Expense>,
        currentPersonId: Long,
        primaryCurrencyName: String,
        primaryCurrencyCode: String,
        timeZone: TimeZone,
        locale: Locale,
    ): List<DaySectionBuildResult> {
        if (expenses.isEmpty()) {
            return emptyList()
        }

        val sections = mutableListOf<DaySectionBuildResult>()
        var currentSectionDate: LocalDate? = null
        val currentSectionExpenses = mutableListOf<Expenses.ExpenseUiModel>()
        var currentSectionSpendingTotal = BigDecimal.ZERO
        var currentSectionHasSpending = false

        fun flushSection() {
            val localDate = currentSectionDate ?: return
            sections += DaySectionBuildResult(
                dayKey = localDate.toString(),
                localDate = localDate,
                uiModel = DaySectionUiModel(
                    dayKey = localDate.toString(),
                    headerLabel = localDate.format(getFullDateFormat(locale)),
                    spendingTotal = currentSectionSpendingTotal
                        .takeIf { currentSectionHasSpending }
                        ?.let { formatAmount(it, primaryCurrencyCode) },
                    expenses = currentSectionExpenses.toList().asImmutableListAdapter(),
                ),
            )
            currentSectionExpenses.clear()
            currentSectionSpendingTotal = BigDecimal.ZERO
            currentSectionHasSpending = false
        }

        expenses.forEach { expense ->
            val expenseDate = expense.timestamp.toLocalDateTime(timeZone).date
            if (currentSectionDate != expenseDate) {
                flushSection()
                currentSectionDate = expenseDate
            }

            currentSectionExpenses += expense.toUiModel(
                primaryCurrencyName = primaryCurrencyName,
                currentPersonId = currentPersonId,
            )
            if (expense.expenseType == ExpenseType.Spending) {
                currentSectionHasSpending = true
                currentSectionSpendingTotal += expense.totalAmount
            }
        }
        flushSection()

        return sections
    }

    private fun formatAmount(amount: BigDecimal, currencyCode: String): String {
        return "${amount.toRoundedString()} $currencyCode"
    }

    private data class DaySectionBuildResult(
        val dayKey: String,
        val localDate: LocalDate,
        val uiModel: DaySectionUiModel,
    )
}
