package com.inwords.expenses.feature.sync.data

import android.content.Context
import androidx.concurrent.futures.await
import androidx.work.WorkManager
import com.inwords.expenses.core.utils.IO
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

actual class EventsSyncManager internal constructor(
    private val context: Context
) : EventsSyncManagerObserverDelegate() {

    @OptIn(DelicateCoroutinesApi::class)
    private val scope = GlobalScope + IO

    private val mutex = Mutex()

    actual override fun pushAllEventInfo(eventId: Long) {
        scope.launch {
            mutex.withLock {
                val workManager = WorkManager.getInstance(context)

                val workInfos = workManager.getWorkInfosByTagFlow(getTagForEvent(eventId)).first()

                if (workInfos.any { !it.state.isFinished }) {
                    return@launch
                }

                WorkManager.getInstance(context)
                    .beginWith(CurrenciesPullWorker.buildCurrenciesPullRequest(eventId))
                    .then(EventPushWorker.buildEventPushRequest(eventId))
                    .then(EventPersonsPushWorker.buildEventPersonsPushRequest(eventId))
                    .then(EventPullPersonsWorker.buildEventPullPersonsRequest(eventId))
                    .then(EventExpensesPushWorker.buildEventExpensesPushRequest(eventId))
                    .then(EventExpensesPullWorker.buildEventExpensesPullRequest(eventId))
                    .enqueue()
            }
        }
    }

    actual suspend fun cancelEventSync(eventId: Long) {
        withContext(IO) {
            mutex.withLock {
                val workManager = WorkManager.getInstance(context)
                workManager.cancelAllWorkByTag(getTagForEvent(eventId)).result.await()
            }
        }
    }

    actual override fun getSyncState(): Flow<Set<Long>> {
        val eventTagPrefix = "$EVENTS_SYNC_WORKER_GROUP:"
        return WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(EVENTS_SYNC_WORKER_GROUP)
            .map { workInfos ->
                workInfos.asSequence()
                    .filter { !it.state.isFinished }
                    .mapNotNullTo(HashSet()) { workInfo ->
                        workInfo.tags.firstOrNull { tag -> tag.startsWith(eventTagPrefix) }
                            ?.removePrefix(eventTagPrefix)
                            ?.toLongOrNull()
                    }
            }
            .distinctUntilChanged()
    }

}

internal actual class EventsSyncManagerFactory(private val context: Context) {

    actual fun create(): EventsSyncManager {
        return EventsSyncManager(context)
    }
}
