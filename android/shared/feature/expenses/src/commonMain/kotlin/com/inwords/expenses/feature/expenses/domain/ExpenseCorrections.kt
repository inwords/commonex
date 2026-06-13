package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.feature.expenses.domain.model.Expense

internal val Expense.correctedExpenseId: Long?
    get() = revertsExpenseId ?: replacesExpenseId

internal fun List<Expense>.hasCorrectionFor(expense: Expense): Boolean {
    return any { candidate ->
        candidate.correctedExpenseId == expense.expenseId
    }
}

internal fun List<Expense>.filterActiveExpenses(): List<Expense> {
    val replacedExpenseIds = mapNotNullTo(HashSet()) { it.replacesExpenseId }
    return filter { it.expenseId !in replacedExpenseIds }
}

internal data class ExpenseTimelineProjection(
    val expense: Expense,
    val showInList: Boolean,
    val countsTowardSpending: Boolean,
)

internal fun List<Expense>.toTimelineProjections(): List<ExpenseTimelineProjection> {
    val replacedExpenseIds = mapNotNullTo(HashSet()) { it.replacesExpenseId }
    return map { expense ->
        ExpenseTimelineProjection(
            expense = expense,
            showInList = expense.correctedExpenseId == null,
            countsTowardSpending = expense.expenseId !in replacedExpenseIds,
        )
    }
}

internal fun List<Expense>.pendingCorrectionConflictIds(remoteCorrectionTargets: Set<Long>): List<Long> {
    val pendingCorrectionsByTarget = filter { it.serverId == null && it.correctedExpenseId != null }
        .groupBy { it.correctedExpenseId }
    val targetsToVisit = remoteCorrectionTargets.toMutableList()
    val conflictingExpenseIds = LinkedHashSet<Long>()
    var targetIndex = 0

    while (targetIndex < targetsToVisit.size) {
        pendingCorrectionsByTarget[targetsToVisit[targetIndex]].orEmpty().forEach { correction ->
            if (conflictingExpenseIds.add(correction.expenseId)) {
                targetsToVisit += correction.expenseId
            }
        }
        targetIndex += 1
    }

    return conflictingExpenseIds.toList()
}
