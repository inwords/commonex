package com.inwords.expenses.integration.base.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * Result returned after adding a participant to an event.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
internal data class AppFunctionParticipantMutation(
    /** The updated event summary. */
    val event: AppFunctionEvent,
    /** The participant that was added. */
    val participantName: String,
)
