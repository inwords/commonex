package com.inwords.expenses.core.navigation

import android.content.Intent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

actual class DeeplinkProvider {

    private val latestDeeplink = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    actual fun latestDeeplink(): Flow<String> {
        return latestDeeplink
    }

    fun supplyIntent(intent: Intent) {
        val data = intent.data?.toString() ?: return
        latestDeeplink.tryEmit(data)
    }
}
