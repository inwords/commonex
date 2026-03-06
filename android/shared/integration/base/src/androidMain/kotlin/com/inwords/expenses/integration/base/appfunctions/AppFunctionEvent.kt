package com.inwords.expenses.integration.base.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * A lightweight event summary exposed to AppFunctions callers.
 *
 * @property id The local event identifier.
 * @property name The event name.
 * @property participantCount The known participant count when available.
 * @property primaryCurrencyCode The event primary currency code when available.
 */
@AppFunctionSerializable(isDescribedByKdoc = true)
internal data class AppFunctionEvent(
    val id: Long,
    val name: String,
    val participantCount: Int?,
    val primaryCurrencyCode: String?,
)
