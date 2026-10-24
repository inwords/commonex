package com.inwords.expenses.feature.expenses.data.db.type_converter

import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class ExpenseTypeConverterTest {

    @Test
    fun `expense types retain their persisted discriminators in both directions`() {
        assertEquals("spending", ExpenseTypeConverter.fromExpenseType(ExpenseType.Spending))
        assertEquals("replenishment", ExpenseTypeConverter.fromExpenseType(ExpenseType.Replenishment))
        assertEquals(ExpenseType.Spending, ExpenseTypeConverter.toExpenseType("spending"))
        assertEquals(ExpenseType.Replenishment, ExpenseTypeConverter.toExpenseType("replenishment"))
    }

    @Test
    fun `unknown stored expense types are rejected rather than reinterpreted`() {
        assertFailsWith<IllegalArgumentException> { ExpenseTypeConverter.toExpenseType("refund") }
    }
}
