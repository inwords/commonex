package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

internal class DebtCalculatorTest {

    // --- Shared fixtures ---
    private object Fixtures {
        val USD = Currency(id = 100, serverId = null, code = "USD", name = "US Dollar")

        val alice = Person(id = 1, serverId = "p1", name = "Alice")
        val bob = Person(id = 2, serverId = "p2", name = "Bob")
        val charlie = Person(id = 3, serverId = "p3", name = "Charlie")
        val diana = Person(id = 4, serverId = "p4", name = "Diana")

        val timestamp = Instant.fromEpochSeconds(1640995200) // 2022-01-01T00:00:00Z

        fun createExpense(
            id: Long,
            payer: Person,
            splits: List<Pair<Person, String>>, // Person to amount mapping
            description: String = "Test expense",
            currency: Currency = USD,
            expenseType: ExpenseType = ExpenseType.Spending
        ): Expense {
            val splitWithPersons = splits.mapIndexed { index, (person, amount) ->
                ExpenseSplitWithPerson(
                    expenseSplitId = id * 100 + index,
                    expenseId = id,
                    person = person,
                    originalAmount = BigDecimal.parseString(amount),
                    exchangedAmount = BigDecimal.parseString(amount)
                )
            }

            return Expense(
                expenseId = id,
                serverId = "exp-$id",
                currency = currency,
                expenseType = expenseType,
                person = payer,
                subjectExpenseSplitWithPersons = splitWithPersons,
                timestamp = timestamp,
                description = description
            )
        }
    }

    @Test
    fun `empty expenses list produces no debts`() {
        // Arrange
        val calculator = DebtCalculator(
            expenses = emptyList(),
            primaryCurrency = Fixtures.USD
        )

        // Act & Assert
        assertTrue(calculator.accumulatedDebts.isEmpty())
        assertTrue(calculator.barterAccumulatedDebts.isEmpty())
    }

    @Test
    fun `single expense where payer is only subject produces no debts`() {
        // Arrange - Alice pays $10 for herself only
        val expenses = listOf(
            Fixtures.createExpense(
                id = 1,
                payer = Fixtures.alice,
                splits = listOf(Fixtures.alice to "10.00")
            )
        )
        val calculator = DebtCalculator(expenses, Fixtures.USD)

        // Act & Assert
        assertTrue(calculator.accumulatedDebts.isEmpty())
        assertTrue(calculator.barterAccumulatedDebts.isEmpty())
    }

    @Test
    fun `simple two-person debt calculation`() {
        // Arrange - Alice pays $20, split equally between Alice and Bob
        val expenses = listOf(
            Fixtures.createExpense(
                id = 1,
                payer = Fixtures.alice,
                splits = listOf(
                    Fixtures.alice to "10.00",
                    Fixtures.bob to "10.00"
                )
            )
        )
        val calculator = DebtCalculator(expenses, Fixtures.USD)

        // Act
        val accumulatedDebts = calculator.accumulatedDebts

        // Assert
        assertEquals(1, accumulatedDebts.size)
        assertTrue(accumulatedDebts.containsKey(Fixtures.bob))

        val bobDebts = accumulatedDebts[Fixtures.bob]!!
        assertEquals(1, bobDebts.size)
        assertTrue(bobDebts.containsKey(Fixtures.alice))

        val bobToAliceDebt = bobDebts[Fixtures.alice]!!
        assertEquals(Fixtures.alice, bobToAliceDebt.creditor)
        assertEquals(Fixtures.bob, bobToAliceDebt.debtor)
        assertEquals(BigDecimal.parseString("10.00"), bobToAliceDebt.amount)
        assertEquals(Fixtures.USD, bobToAliceDebt.currency)
        assertEquals(1, bobToAliceDebt.debts.size)
    }

    @Test
    fun `multiple expenses accumulate debts correctly`() {
        // Arrange - Two expenses:
        // 1. Alice pays $30, split: Alice $10, Bob $10, Charlie $10
        // 2. Alice pays $20, split: Alice $5, Bob $15
        val expenses = listOf(
            Fixtures.createExpense(
                id = 1,
                payer = Fixtures.alice,
                splits = listOf(
                    Fixtures.alice to "10.00",
                    Fixtures.bob to "10.00",
                    Fixtures.charlie to "10.00"
                )
            ),
            Fixtures.createExpense(
                id = 2,
                payer = Fixtures.alice,
                splits = listOf(
                    Fixtures.alice to "5.00",
                    Fixtures.bob to "15.00"
                )
            )
        )
        val calculator = DebtCalculator(expenses, Fixtures.USD)

        // Act
        val accumulatedDebts = calculator.accumulatedDebts

        // Assert
        assertEquals(2, accumulatedDebts.size)

        // Bob owes Alice $25 total ($10 + $15)
        val bobToAliceDebt = accumulatedDebts[Fixtures.bob]!![Fixtures.alice]!!
        assertEquals(BigDecimal.parseString("25.00"), bobToAliceDebt.amount)
        assertEquals(2, bobToAliceDebt.debts.size)

        // Charlie owes Alice $10
        val charlieToAliceDebt = accumulatedDebts[Fixtures.charlie]!![Fixtures.alice]!!
        assertEquals(BigDecimal.parseString("10.00"), charlieToAliceDebt.amount)
        assertEquals(1, charlieToAliceDebt.debts.size)
    }

    @Test
    fun `different payers create separate debt relationships`() {
        // Arrange - Two expenses with different payers:
        // 1. Alice pays $20, split: Alice $10, Bob $10
        // 2. Bob pays $30, split: Bob $10, Charlie $20
        val expenses = listOf(
            Fixtures.createExpense(
                id = 1,
                payer = Fixtures.alice,
                splits = listOf(
                    Fixtures.alice to "10.00",
                    Fixtures.bob to "10.00"
                )
            ),
            Fixtures.createExpense(
                id = 2,
                payer = Fixtures.bob,
                splits = listOf(
                    Fixtures.bob to "10.00",
                    Fixtures.charlie to "20.00"
                )
            )
        )
        val calculator = DebtCalculator(expenses, Fixtures.USD)

        // Act
        val accumulatedDebts = calculator.accumulatedDebts

        // Assert
        assertEquals(2, accumulatedDebts.size)

        // Bob owes Alice $10
        val bobToAliceDebt = accumulatedDebts[Fixtures.bob]!![Fixtures.alice]!!
        assertEquals(BigDecimal.parseString("10.00"), bobToAliceDebt.amount)

        // Charlie owes Bob $20
        val charlieToBobDebt = accumulatedDebts[Fixtures.charlie]!![Fixtures.bob]!!
        assertEquals(BigDecimal.parseString("20.00"), charlieToBobDebt.amount)
    }

    @Test
    fun `barter accumulated debts filters small amounts below threshold`() {
        // Arrange - Create a very small debt (below 0.01 threshold)
        val expenses = listOf(
            Fixtures.createExpense(
                id = 1,
                payer = Fixtures.alice,
                splits = listOf(
                    Fixtures.alice to "0.999",
                    Fixtures.bob to "0.001"
                )
            )
        )
        val calculator = DebtCalculator(expenses, Fixtures.USD)

        // Act
        val barterDebts = calculator.barterAccumulatedDebts

        // Assert - Should be empty because debt is below threshold
        assertTrue(barterDebts.isEmpty())
    }

    @Test
    fun `barter accumulated debts include amounts above threshold`() {
        // Arrange - Create a debt above the 0.01 threshold
        val expenses = listOf(
            Fixtures.createExpense(
                id = 1,
                payer = Fixtures.alice,
                splits = listOf(
                    Fixtures.alice to "9.98",
                    Fixtures.bob to "0.02"
                )
            )
        )
        val calculator = DebtCalculator(expenses, Fixtures.USD)

        // Act
        val barterDebts = calculator.barterAccumulatedDebts

        // Assert
        assertEquals(1, barterDebts.size)
        val bobBarterDebt = barterDebts[Fixtures.bob]!![Fixtures.alice]!!
        assertEquals(BigDecimal.parseString("0.02"), bobBarterDebt.barterAmount)
        assertNull(bobBarterDebt.creditorToDebtorDebt)
    }

    @Test
    fun `barter accumulated debts calculate net amounts correctly`() {
        // Arrange - Create cross debts:
        // 1. Alice pays $30, Bob owes $10
        // 2. Bob pays $24, Alice owes $4
        // Net: Bob owes Alice $6
        val expenses = listOf(
            Fixtures.createExpense(
                id = 1,
                payer = Fixtures.alice,
                splits = listOf(
                    Fixtures.alice to "20.00",
                    Fixtures.bob to "10.00"
                )
            ),
            Fixtures.createExpense(
                id = 2,
                payer = Fixtures.bob,
                splits = listOf(
                    Fixtures.bob to "20.00",
                    Fixtures.alice to "4.00"
                )
            )
        )
        val calculator = DebtCalculator(expenses, Fixtures.USD)

        // Act
        val barterDebts = calculator.barterAccumulatedDebts

        // Assert
        assertEquals(1, barterDebts.size)
        val bobBarterDebt = barterDebts[Fixtures.bob]!![Fixtures.alice]!!
        assertEquals(BigDecimal.parseString("6.00"), bobBarterDebt.barterAmount)
        assertEquals(BigDecimal.parseString("10.00"), bobBarterDebt.debtorToCreditorDebt.amount)
        assertNotNull(bobBarterDebt.creditorToDebtorDebt)
        assertEquals(BigDecimal.parseString("4.00"), bobBarterDebt.creditorToDebtorDebt.amount)
    }

    @Test
    fun `calculateBarterAccumulatedDebtSummaries returns netted debt lines`() {
        val expenses = listOf(
            Fixtures.createExpense(
                id = 1,
                payer = Fixtures.alice,
                splits = listOf(
                    Fixtures.alice to "20.00",
                    Fixtures.bob to "10.00"
                )
            ),
            Fixtures.createExpense(
                id = 2,
                payer = Fixtures.bob,
                splits = listOf(
                    Fixtures.bob to "20.00",
                    Fixtures.alice to "4.00"
                )
            )
        )

        val barterAccumulatedDebtSummaries = calculateBarterAccumulatedDebtSummaries(
            expenses = expenses,
            primaryCurrency = Fixtures.USD,
        )

        assertEquals(1, barterAccumulatedDebtSummaries.size)
        assertEquals(Fixtures.bob, barterAccumulatedDebtSummaries.single().debtor)
        assertEquals(Fixtures.alice, barterAccumulatedDebtSummaries.single().creditor)
        assertEquals(BigDecimal.parseString("6.00"), barterAccumulatedDebtSummaries.single().amount)
        assertEquals(Fixtures.USD, barterAccumulatedDebtSummaries.single().currency)
    }

    @Test
    fun `getBarterAccumulatedDebtForPerson returns correct debts`() {
        // Arrange
        val expenses = listOf(
            Fixtures.createExpense(
                id = 1,
                payer = Fixtures.alice,
                splits = listOf(
                    Fixtures.alice to "10.00",
                    Fixtures.bob to "15.00",
                    Fixtures.charlie to "5.00"
                )
            )
        )
        val calculator = DebtCalculator(expenses, Fixtures.USD)

        // Act
        val bobDebts = calculator.getBarterAccumulatedDebtForPerson(Fixtures.bob)
        val aliceDebts = calculator.getBarterAccumulatedDebtForPerson(Fixtures.alice)

        // Assert
        assertEquals(1, bobDebts.size)
        assertTrue(bobDebts.containsKey(Fixtures.alice))
        assertEquals(BigDecimal.parseString("15.00"), bobDebts[Fixtures.alice]!!.barterAmount)

        assertTrue(aliceDebts.isEmpty())
    }

    @Test
    fun `getBarterAccumulatedDebtForPerson returns empty map for person with no debts`() {
        // Arrange
        val expenses = listOf(
            Fixtures.createExpense(
                id = 1,
                payer = Fixtures.alice,
                splits = listOf(
                    Fixtures.alice to "20.00",
                    Fixtures.bob to "10.00"
                )
            )
        )
        val calculator = DebtCalculator(expenses, Fixtures.USD)

        // Act
        val dianaDebts = calculator.getBarterAccumulatedDebtForPerson(Fixtures.diana)

        // Assert
        assertTrue(dianaDebts.isEmpty())
    }

    @Test
    fun `complex multi-person scenario with various debt relationships`() {
        // Arrange - Complex scenario:
        // 1. Alice pays $60, split equally among all 4 people ($15 each)
        // 2. Bob pays $40, split: Bob $20, Charlie $20
        // 3. Charlie pays $30, split: Alice $10, Diana $20
        // 4. Diana pays $20, split: Diana $10, Alice $10
        val expenses = listOf(
            Fixtures.createExpense(
                id = 1,
                payer = Fixtures.alice,
                splits = listOf(
                    Fixtures.alice to "15.00",
                    Fixtures.bob to "15.00",
                    Fixtures.charlie to "15.00",
                    Fixtures.diana to "15.00"
                )
            ),
            Fixtures.createExpense(
                id = 2,
                payer = Fixtures.bob,
                splits = listOf(
                    Fixtures.bob to "20.00",
                    Fixtures.charlie to "20.00"
                )
            ),
            Fixtures.createExpense(
                id = 3,
                payer = Fixtures.charlie,
                splits = listOf(
                    Fixtures.alice to "10.00",
                    Fixtures.diana to "20.00"
                )
            ),
            Fixtures.createExpense(
                id = 4,
                payer = Fixtures.diana,
                splits = listOf(
                    Fixtures.diana to "10.00",
                    Fixtures.alice to "10.00"
                )
            )
        )
        val calculator = DebtCalculator(expenses, Fixtures.USD)

        // Act
        val accumulatedDebts = calculator.accumulatedDebts
        val barterDebts = calculator.barterAccumulatedDebts

        // Assert accumulated debts
        assertEquals(4, accumulatedDebts.size)

        // Bob owes Alice $15
        assertEquals(BigDecimal.parseString("15.00"), accumulatedDebts[Fixtures.bob]!![Fixtures.alice]!!.amount)

        // Charlie owes Alice $15, Bob $20
        assertEquals(BigDecimal.parseString("15.00"), accumulatedDebts[Fixtures.charlie]!![Fixtures.alice]!!.amount)
        assertEquals(BigDecimal.parseString("20.00"), accumulatedDebts[Fixtures.charlie]!![Fixtures.bob]!!.amount)

        // Diana owes Alice $15
        assertEquals(BigDecimal.parseString("15.00"), accumulatedDebts[Fixtures.diana]!![Fixtures.alice]!!.amount)

        // Alice owes Charlie $10, Diana $10
        assertEquals(BigDecimal.parseString("10.00"), accumulatedDebts[Fixtures.alice]!![Fixtures.charlie]!!.amount)
        assertEquals(BigDecimal.parseString("10.00"), accumulatedDebts[Fixtures.alice]!![Fixtures.diana]!!.amount)

        // Assert barter debts (net amounts)
        // Bob owes Alice net $15
        assertEquals(BigDecimal.parseString("15.00"), barterDebts[Fixtures.bob]!![Fixtures.alice]!!.barterAmount)

        // Charlie owes Alice net $5 (15 - 10)
        assertEquals(BigDecimal.parseString("5.00"), barterDebts[Fixtures.charlie]!![Fixtures.alice]!!.barterAmount)

        // Charlie owes Bob net $20
        assertEquals(BigDecimal.parseString("20.00"), barterDebts[Fixtures.charlie]!![Fixtures.bob]!!.barterAmount)

        // Diana owes Alice net $5 (15 - 10)
        assertEquals(BigDecimal.parseString("5.00"), barterDebts[Fixtures.diana]!![Fixtures.alice]!!.barterAmount)
    }

    @Test
    fun `replenishment expenses are included in debt calculations`() {
        // Arrange - Mix of spending and replenishment:
        // 1. Alice pays $30 (spending), split: Alice $10, Bob $20
        // 2. Bob pays $15 (replenishment), split: Alice $15
        val expenses = listOf(
            Fixtures.createExpense(
                id = 1,
                payer = Fixtures.alice,
                splits = listOf(
                    Fixtures.alice to "10.00",
                    Fixtures.bob to "20.00"
                ),
                expenseType = ExpenseType.Spending
            ),
            Fixtures.createExpense(
                id = 2,
                payer = Fixtures.bob,
                splits = listOf(
                    Fixtures.alice to "15.00"
                ),
                expenseType = ExpenseType.Replenishment
            )
        )
        val calculator = DebtCalculator(expenses, Fixtures.USD)

        // Act
        val barterDebts = calculator.barterAccumulatedDebts

        // Assert - Net result: Bob owes Alice $5 (20 - 15)
        assertEquals(1, barterDebts.size)
        val bobBarterDebt = barterDebts[Fixtures.bob]!![Fixtures.alice]!!
        assertEquals(BigDecimal.parseString("5.00"), bobBarterDebt.barterAmount)
    }

    @Test
    fun `decimal precision is maintained throughout calculations`() {
        // Arrange - Use precise decimal amounts
        val expenses = listOf(
            Fixtures.createExpense(
                id = 1,
                payer = Fixtures.alice,
                splits = listOf(
                    Fixtures.alice to "33.333333",
                    Fixtures.bob to "33.333333",
                    Fixtures.charlie to "33.333334"
                )
            )
        )
        val calculator = DebtCalculator(expenses, Fixtures.USD)

        // Act
        val accumulatedDebts = calculator.accumulatedDebts

        // Assert
        assertEquals(BigDecimal.parseString("33.333333"), accumulatedDebts[Fixtures.bob]!![Fixtures.alice]!!.amount)
        assertEquals(BigDecimal.parseString("33.333334"), accumulatedDebts[Fixtures.charlie]!![Fixtures.alice]!!.amount)
    }

    @Test
    fun `zero amount splits are handled correctly`() {
        // Arrange - Include a person with zero amount
        val expenses = listOf(
            Fixtures.createExpense(
                id = 1,
                payer = Fixtures.alice,
                splits = listOf(
                    Fixtures.alice to "20.00",
                    Fixtures.bob to "0.00",
                    Fixtures.charlie to "10.00"
                )
            )
        )
        val calculator = DebtCalculator(expenses, Fixtures.USD)

        // Act
        val accumulatedDebts = calculator.accumulatedDebts

        // Assert - Bob should not appear in debts since amount is 0
        assertEquals(1, accumulatedDebts.size)
        assertTrue(accumulatedDebts.containsKey(Fixtures.charlie))
        assertEquals(BigDecimal.parseString("10.00"), accumulatedDebts[Fixtures.charlie]!![Fixtures.alice]!!.amount)
    }
}
