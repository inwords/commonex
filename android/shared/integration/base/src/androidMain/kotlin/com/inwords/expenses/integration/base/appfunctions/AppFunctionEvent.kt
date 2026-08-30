package com.inwords.expenses.integration.base.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * A lightweight event summary exposed to AppFunctions callers.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
internal data class AppFunctionEvent(
    /** The local event identifier. */
    val id: Long,
    /** The event name. */
    val name: String,
    /** The known participant count when available. */
    val participantCount: Int?,
    /** The event primary currency code when available. */
    val primaryCurrencyCode: String?,
)
