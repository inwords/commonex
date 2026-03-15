package com.inwords.expenses.integration.base.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * A currency that can be used when creating an event.
 *
 * @property code The ISO-like currency code used by the app.
 * @property name The human-readable currency name.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
internal data class AppFunctionCurrency(
    val code: String,
    val name: String,
)
