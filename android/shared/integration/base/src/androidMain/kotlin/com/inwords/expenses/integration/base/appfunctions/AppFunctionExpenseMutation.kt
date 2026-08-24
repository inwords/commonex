package com.inwords.expenses.integration.base.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * Result returned after adding an expense to an event.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
internal data class AppFunctionExpenseMutation(
    /** The event that received the expense. */
    val event: AppFunctionEvent,
    /** The participant who paid the expense. */
    val payerName: String,
    /** The expense description. */
    val description: String,
    /** The total expense amount as a decimal string. */
    val amount: String,
    /** The event primary currency code. */
    val currencyCode: String,
    /** The number of participants included in the equal split. */
    val splitBetweenParticipants: Int,
)
