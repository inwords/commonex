package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.feature.events.domain.model.Event
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class ExpensesRefreshRequestsHolder internal constructor() {

    val refreshExpensesRequests: Flow<Event>
        field = MutableSharedFlow<Event>(
            extraBufferCapacity = 2,
            onBufferOverflow = BufferOverflow.SUSPEND
        )

    suspend fun enqueueAsyncSync(event: Event) {
        refreshExpensesRequests.emit(event)
    }
}
