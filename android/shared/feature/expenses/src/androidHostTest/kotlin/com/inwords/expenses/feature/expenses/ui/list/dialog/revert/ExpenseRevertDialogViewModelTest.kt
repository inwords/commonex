package com.inwords.expenses.feature.expenses.ui.list.dialog.revert

import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.feature.expenses.domain.RevertExpenseUseCase
import com.inwords.expenses.feature.expenses.ui.list.ExpensesPaneDestination
import io.mockk.coEvery
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.StringResource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExpenseRevertDialogViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val navigationController = mockk<NavigationController>(relaxed = true) {
        justRun { popBackStack() }
        justRun { popBackStack(any(), any()) }
    }
    private val revertExpenseUseCase = mockk<RevertExpenseUseCase>(relaxed = true)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onDismiss_popsBackStack() = runTest {
        val viewModel = ExpenseRevertDialogViewModel(
            navigationController = navigationController,
            revertExpenseUseCase = revertExpenseUseCase,
            expenseId = 10L,
            eventId = 1L,
            expenseDescription = "Lunch",
            stringProvider = object : StringProvider {
                override suspend fun getString(stringResource: StringResource): String = "Revert"
                override suspend fun getString(stringResource: StringResource, vararg formatArgs: Any): String = "Revert"
            },
            viewModelScope = backgroundScope,
        )

        viewModel.onDismiss()

        verify(exactly = 1) { navigationController.popBackStack() }
    }

    @Test
    fun onConfirmRevert_whenEventAndExpensePresent_revertsAndPopsToExpensesPane() = runTest {
        coEvery { revertExpenseUseCase.revertExpense(1L, 10L, "Revert") } returns true

        val viewModel = ExpenseRevertDialogViewModel(
            navigationController = navigationController,
            revertExpenseUseCase = revertExpenseUseCase,
            expenseId = 10L,
            eventId = 1L,
            expenseDescription = "Lunch",
            stringProvider = object : StringProvider {
                override suspend fun getString(stringResource: StringResource): String = "Revert"
                override suspend fun getString(stringResource: StringResource, vararg formatArgs: Any): String = "Revert"
            },
            viewModelScope = backgroundScope,
        )

        viewModel.onConfirmRevert()
        runCurrent()
        advanceUntilIdle()

        verify(exactly = 1) {
            navigationController.popBackStack(
                toDestination = ExpensesPaneDestination,
                inclusive = false
            )
        }
    }

    @Test
    fun onConfirmRevert_whenExpenseMissing_popsCurrentDialog() = runTest {
        coEvery { revertExpenseUseCase.revertExpense(1L, 10L, "Revert") } returns false

        val viewModel = ExpenseRevertDialogViewModel(
            navigationController = navigationController,
            revertExpenseUseCase = revertExpenseUseCase,
            expenseId = 10L,
            eventId = 1L,
            expenseDescription = "Lunch",
            stringProvider = object : StringProvider {
                override suspend fun getString(stringResource: StringResource): String = "Revert"
                override suspend fun getString(stringResource: StringResource, vararg formatArgs: Any): String = "Revert"
            },
            viewModelScope = backgroundScope,
        )

        viewModel.onConfirmRevert()
        runCurrent()
        advanceUntilIdle()

        verify(exactly = 1) { navigationController.popBackStack() }
    }
}
