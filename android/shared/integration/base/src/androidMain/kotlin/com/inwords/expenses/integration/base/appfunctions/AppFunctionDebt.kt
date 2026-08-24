package com.inwords.expenses.integration.base.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * A single net debt line for an event.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
internal data class AppFunctionDebt(
    /** The participant who owes money. */
    val debtorName: String,
    /** The participant who should receive money. */
    val creditorName: String,
    /** The debt amount in decimal string form. */
    val amount: String,
    /** The currency code of the debt amount. */
    val currencyCode: String,
)
