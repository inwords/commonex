package com.inwords.expenses.feature.sync.data

import androidx.concurrent.futures.await
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import com.inwords.expenses.core.locator.ComponentsMap
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.expenses.api.ExpensesComponent
import com.inwords.expenses.feature.expenses.domain.tasks.EventExpensesPullTask
import com.inwords.expenses.feature.expenses.domain.tasks.EventExpensesPushTask
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
internal class EventExpensesWorkerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pushTask = mockk<EventExpensesPushTask>()
    private val pullTask = mockk<EventExpensesPullTask>()

    private lateinit var workManager: WorkManager
    private lateinit var testDriver: TestDriver

    @Before
    fun setUp() {
        val component = mockk<ExpensesComponent>()
        every { component.eventExpensesPushTask } returns lazyOf(pushTask)
        every { component.eventExpensesPullTask } returns lazyOf(pullTask)
        ComponentsMap.registerComponent(ExpensesComponent::class, lazyOf(component))

        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
        testDriver = requireNotNull(WorkManagerTestInitHelper.getTestDriver(context))
    }

    @After
    fun tearDown() {
        if (::workManager.isInitialized) {
            workManager.cancelAllWork().result.get(10, TimeUnit.SECONDS)
            WorkManagerTestInitHelper.closeWorkDatabase()
        }
    }

    @Test
    fun retryablePush_isRescheduledBeforeDependentPullRuns() = runTest {
        coEvery { pushTask.pushEventExpenses(EVENT_ID) } returnsMany listOf(IoResult.Error.Retry, IoResult.Success(Unit))
        coEvery { pullTask.pullEventExpenses(EVENT_ID) } returns IoResult.Success(Unit)
        val push = EventExpensesPushWorker.buildEventExpensesPushRequest(EVENT_ID)
        val pull = EventExpensesPullWorker.buildEventExpensesPullRequest(EVENT_ID)
        workManager.beginWith(push).then(pull).enqueue().result.await()

        testDriver.setAllConstraintsMet(push.id)
        awaitWork(push.id) { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount == 1 }
        assertEquals(WorkInfo.State.BLOCKED, workManager.getWorkInfoById(pull.id).await()?.state)
        coVerify(exactly = 1) { pushTask.pushEventExpenses(EVENT_ID) }
        coVerify(exactly = 0) { pullTask.pullEventExpenses(any()) }

        testDriver.setInitialDelayMet(push.id)
        testDriver.setAllConstraintsMet(push.id)
        awaitWork(push.id) { it.state == WorkInfo.State.SUCCEEDED }
        awaitWork(pull.id) { it.state == WorkInfo.State.ENQUEUED }
        testDriver.setAllConstraintsMet(pull.id)
        awaitWork(pull.id) { it.state == WorkInfo.State.SUCCEEDED }

        coVerify(exactly = 2) { pushTask.pushEventExpenses(EVENT_ID) }
        coVerify(exactly = 1) { pullTask.pullEventExpenses(EVENT_ID) }
    }

    @Test
    fun permanentPushFailure_failsDependentPullWithoutRetry() = runTest {
        coEvery { pushTask.pushEventExpenses(EVENT_ID) } returns IoResult.Error.Failure
        val push = EventExpensesPushWorker.buildEventExpensesPushRequest(EVENT_ID)
        val pull = EventExpensesPullWorker.buildEventExpensesPullRequest(EVENT_ID)
        workManager.beginWith(push).then(pull).enqueue().result.await()

        testDriver.setAllConstraintsMet(push.id)
        awaitWork(push.id) { it.state == WorkInfo.State.FAILED }
        awaitWork(pull.id) { it.state == WorkInfo.State.FAILED }

        coVerify(exactly = 1) { pushTask.pushEventExpenses(EVENT_ID) }
        coVerify(exactly = 0) { pullTask.pullEventExpenses(any()) }
    }

    @Test
    fun repeatedRetryableFailures_stopAtWorkerAttemptLimit() = runTest {
        coEvery { pushTask.pushEventExpenses(EVENT_ID) } returns IoResult.Error.Retry
        val push = EventExpensesPushWorker.buildEventExpensesPushRequest(EVENT_ID)
        workManager.enqueue(push).result.await()

        testDriver.setAllConstraintsMet(push.id)
        for (attempt in 1..4) {
            awaitWork(push.id) { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount == attempt }
            coVerify(exactly = attempt) { pushTask.pushEventExpenses(EVENT_ID) }
            testDriver.setInitialDelayMet(push.id)
            testDriver.setAllConstraintsMet(push.id)
        }
        awaitWork(push.id) { it.state == WorkInfo.State.FAILED }
        coVerify(exactly = 4) { pushTask.pushEventExpenses(EVENT_ID) }
    }

    private suspend fun awaitWork(id: UUID, predicate: (WorkInfo) -> Boolean): WorkInfo = withContext(Dispatchers.Default) {
        withTimeout(10.seconds) {
            workManager.getWorkInfoByIdFlow(id).filterNotNull().first(predicate)
        }
    }

    private companion object {

        private const val EVENT_ID = 17L
    }
}
