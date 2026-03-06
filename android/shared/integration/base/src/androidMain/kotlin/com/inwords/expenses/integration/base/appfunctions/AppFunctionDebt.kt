package com.inwords.expenses.integration.base.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * A single net debt line for an event.
 *
 * @property debtorName The participant who owes money.
 * @property creditorName The participant who should receive money.
 * @property amount The debt amount in decimal string form.
 * @property currencyCode The currency code of the debt amount.
 */
@AppFunctionSerializable(isDescribedByKdoc = true)
internal data class AppFunctionDebt(
    val debtorName: String,
    val creditorName: String,
    val amount: String,
    val currencyCode: String,
)
