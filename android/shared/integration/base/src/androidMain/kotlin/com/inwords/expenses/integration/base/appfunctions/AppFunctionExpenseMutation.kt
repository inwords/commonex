package com.inwords.expenses.integration.base.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * Result returned after adding an expense to an event.
 *
 * @property event The event that received the expense.
 * @property payerName The participant who paid the expense.
 * @property description The expense description.
 * @property amount The total expense amount as a decimal string.
 * @property currencyCode The event primary currency code.
 * @property splitBetweenParticipants The number of participants included in the equal split.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
internal data class AppFunctionExpenseMutation(
    val event: AppFunctionEvent,
    val payerName: String,
    val description: String,
    val amount: String,
    val currencyCode: String,
    val splitBetweenParticipants: Int,
)
