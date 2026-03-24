package com.inwords.expenses.feature.sync.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkRequest
import androidx.work.WorkRequest.Companion.MIN_BACKOFF_MILLIS
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.inwords.expenses.core.locator.ComponentsMap
import com.inwords.expenses.core.locator.inject
import com.inwords.expenses.core.observability.Observability
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.api.EventsComponent
import com.inwords.expenses.feature.expenses.api.ExpensesComponent
import java.util.concurrent.TimeUnit

internal class CurrenciesPullWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val eventsComponent by ComponentsMap.inject<EventsComponent>()

    override suspend fun doWork(): Result {
        if (shouldFailure()) return Result.failure()

        val ioResult = eventsComponent.currenciesPullTask.value.pullCurrencies()

        return ioResult.toResult()
    }

    companion object {

        fun buildCurrenciesPullRequest(eventId: Long): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<CurrenciesPullWorker>()
                .setCommonParameters()
                .addTag(getTagForEvent(eventId))
                .build()
        }
    }
}

internal class EventPushWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val eventsComponent by ComponentsMap.inject<EventsComponent>()

    override suspend fun doWork(): Result {
        val eventId = inputData.getEventId() ?: return Result.failure()

        if (shouldFailure()) return Result.failure()

        val ioResult = eventsComponent.eventPushTask.value.pushEvent(eventId)

        return ioResult.toResult()
    }

    companion object {

        fun buildEventPushRequest(eventId: Long): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<EventPushWorker>()
                .setCommonParameters()
                .setInputDataEventId(eventId)
                .addTag(getTagForEvent(eventId))
                .build()
        }
    }
}

internal class EventPersonsPushWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val eventsComponent by ComponentsMap.inject<EventsComponent>()

    override suspend fun doWork(): Result {
        val eventId = inputData.getEventId() ?: return Result.failure()

        if (shouldFailure()) return Result.failure()

        val ioResult = eventsComponent.eventPersonsPushTask.value.pushEventPersons(eventId)

        return ioResult.toResult()
    }

    companion object {

        fun buildEventPersonsPushRequest(eventId: Long): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<EventPersonsPushWorker>()
                .setCommonParameters()
                .setInputDataEventId(eventId)
                .addTag(getTagForEvent(eventId))
                .build()
        }
    }
}

internal class EventPullPersonsWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val eventsComponent by ComponentsMap.inject<EventsComponent>()

    override suspend fun doWork(): Result {
        val eventId = inputData.getEventId() ?: return Result.failure()

        if (shouldFailure()) return Result.failure()

        val ioResult = eventsComponent.eventPullPersonsTask.value.pullEventPersons(eventId)

        return ioResult.toResult()
    }

    companion object {

        fun buildEventPullPersonsRequest(eventId: Long): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<EventPullPersonsWorker>()
                .setCommonParameters()
                .setInputDataEventId(eventId)
                .addTag(getTagForEvent(eventId))
                .build()
        }
    }
}

internal class EventExpensesPushWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val expensesComponent by ComponentsMap.inject<ExpensesComponent>()

    override suspend fun doWork(): Result {
        val eventId = inputData.getEventId() ?: return Result.failure()

        if (shouldFailure()) return Result.failure()

        val ioResult = expensesComponent.eventExpensesPushTask.value.pushEventExpenses(eventId)

        return ioResult.toResult()
    }

    companion object {

        fun buildEventExpensesPushRequest(eventId: Long): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<EventExpensesPushWorker>()
                .setCommonParameters()
                .setInputDataEventId(eventId)
                .addTag(getTagForEvent(eventId))
                .build()
        }
    }
}

internal class EventExpensesPullWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val expensesComponent by ComponentsMap.inject<ExpensesComponent>()

    override suspend fun doWork(): Result {
        val eventId = inputData.getEventId() ?: return Result.failure()

        if (shouldFailure()) return Result.failure()

        val ioResult = expensesComponent.eventExpensesPullTask.value.pullEventExpenses(eventId)

        return ioResult.toResult()
    }

    companion object {

        fun buildEventExpensesPullRequest(eventId: Long): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<EventExpensesPullWorker>()
                .setCommonParameters()
                .setInputDataEventId(eventId)
                .addTag(getTagForEvent(eventId))
                .build()
        }
    }
}

internal fun getTagForEvent(eventId: Long): String {
    return "$EVENTS_SYNC_WORKER_GROUP:$eventId"
}

internal const val EVENTS_SYNC_WORKER_GROUP = "events_sync"

private const val KEY_EVENT_ID = "EVENT_ID"

private fun Data.getEventId(): Long? {
    val eventId = getLong(KEY_EVENT_ID, -1L)
    if (eventId == -1L) {
        Observability.captureMessage("Sync worker input data is missing a valid event id")
        return null
    }
    return eventId
}

private fun ListenableWorker.shouldFailure(): Boolean {
    return runAttemptCount > 3
}

private fun IoResult<*>.toResult(): ListenableWorker.Result {
    return when (this) {
        is IoResult.Success -> ListenableWorker.Result.success()
        is IoResult.Error.Retry -> ListenableWorker.Result.retry()
        is IoResult.Error.Failure -> ListenableWorker.Result.failure()
    }
}

private fun <B : WorkRequest.Builder<B, *>, W : WorkRequest> WorkRequest.Builder<B, W>.setInputDataEventId(eventId: Long): B {
    return setInputData(
        workDataOf(KEY_EVENT_ID to eventId)
    )
}

private fun <B : WorkRequest.Builder<B, *>, W : WorkRequest> WorkRequest.Builder<B, W>.setCommonParameters(): B {
    return setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
        .setBackoffCriteria(BackoffPolicy.LINEAR, MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .addTag(EVENTS_SYNC_WORKER_GROUP)
}
