package com.inwords.expenses.integration.base.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * A currency that can be used when creating an event.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
internal data class AppFunctionCurrency(
    /** The ISO-like currency code used by the app. */
    val code: String,
    /** The human-readable currency name. */
    val name: String,
)
