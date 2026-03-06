package com.inwords.expenses.integration.base.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * Result returned after adding a participant to an event.
 *
 * @property event The updated event summary.
 * @property participantName The participant that was added.
 */
@AppFunctionSerializable(isDescribedByKdoc = true)
internal data class AppFunctionParticipantMutation(
    val event: AppFunctionEvent,
    val participantName: String,
)
