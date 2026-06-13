package com.inwords.expenses.feature.expenses.ui.list

import androidx.compose.ui.text.intl.Locale
import com.inwords.expenses.core.ui.utils.DefaultStringProvider
import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.core.ui.utils.getFullDateFormat
import com.inwords.expenses.feature.expenses.domain.model.Expense
import expenses.shared.feature.expenses.generated.resources.Res
import expenses.shared.feature.expenses.generated.resources.expenses_status_edited_on
import expenses.shared.feature.expenses.generated.resources.expenses_status_reverted_on
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

internal class ExpenseCorrectionStatusTextFactory(
    private val stringProvider: StringProvider = DefaultStringProvider,
    private val timeZoneProvider: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val localeProvider: () -> Locale = { Locale.current },
) {

    suspend fun createStatusesByTargetExpenseId(expenses: List<Expense>): Map<Long, String> {
        val statuses = hashMapOf<Long, String>()
        expenses.forEach { expense ->
            expense.revertsExpenseId?.let { expenseId ->
                statuses[expenseId] = formatStatus(expense, CorrectionStatus.Reverted)
            }
            expense.replacesExpenseId?.let { expenseId ->
                statuses[expenseId] = formatStatus(expense, CorrectionStatus.Edited)
            }
        }
        return statuses
    }

    suspend fun createStatusFor(expense: Expense, expenses: List<Expense>): String? {
        val targetId = expense.expenseId
        val correction = expenses.firstOrNull {
            it.revertsExpenseId == targetId || it.replacesExpenseId == targetId
        } ?: return null

        return createStatusForCorrection(correction)
    }

    suspend fun createStatusForCorrection(correction: Expense): String? {
        return when {
            correction.replacesExpenseId != null -> formatStatus(correction, CorrectionStatus.Edited)
            correction.revertsExpenseId != null -> formatStatus(correction, CorrectionStatus.Reverted)
            else -> null
        }
    }

    private suspend fun formatStatus(expense: Expense, status: CorrectionStatus): String {
        val localDate = expense.timestamp.toLocalDateTime(timeZoneProvider.invoke()).date
        val formattedDate = localDate.format(getFullDateFormat(localeProvider.invoke()))
        return when (status) {
            CorrectionStatus.Edited -> stringProvider.getString(Res.string.expenses_status_edited_on, formattedDate)
            CorrectionStatus.Reverted -> stringProvider.getString(Res.string.expenses_status_reverted_on, formattedDate)
        }
    }

    private enum class CorrectionStatus {
        Edited,
        Reverted,
    }
}
