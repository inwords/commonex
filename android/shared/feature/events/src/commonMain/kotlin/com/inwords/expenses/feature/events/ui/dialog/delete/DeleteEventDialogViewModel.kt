package com.inwords.expenses.feature.events.ui.dialog.delete

import androidx.lifecycle.ViewModel
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.feature.events.api.EventDeletionStateManager
import com.inwords.expenses.feature.events.api.EventDeletionStateManager.EventDeletionState
import com.inwords.expenses.feature.events.domain.DeleteEventUseCase
import com.inwords.expenses.feature.events.domain.DeleteEventUseCase.DeleteEventResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class DeleteEventDialogViewModel(
    private val navigationController: NavigationController,
    private val eventDeletionStateManager: EventDeletionStateManager,
    private val deleteEventUseCase: DeleteEventUseCase,
    private val eventId: Long,
    private val scope: CoroutineScope = GlobalScope,
    private val workDispatcher: CoroutineDispatcher = IO,
) : ViewModel() {

    private var deleteJob: Job? = null

    fun onConfirmDelete() {
        if (deleteJob != null) return // prevent multiple clicks for deletion
        deleteJob = scope.launch(workDispatcher) {
            eventDeletionStateManager.setEventDeletionState(eventId, EventDeletionState.Loading)
            navigationController.popBackStack()

            val result = try {
                deleteEventUseCase.deleteRemoteAndLocalEvent(eventId)
            } catch (e: CancellationException) {
                eventDeletionStateManager.setEventDeletionState(eventId, EventDeletionState.RemoteDeletionFailed)
                throw e
            }
            when (result) {
                DeleteEventResult.Deleted -> {
                    // Deleted from DB, so will be reactively updated everywhere
                    eventDeletionStateManager.clearEventDeletionState(eventId)
                }

                DeleteEventResult.RemoteFailed -> {
                    // Remote deletion failed, local is untouched
                    eventDeletionStateManager.setEventDeletionState(eventId, EventDeletionState.RemoteDeletionFailed)
                }
            }
        }
    }

    fun onDismiss() {
        navigationController.popBackStack()
    }
}
