package com.inwords.expenses.feature.events.domain

import com.inwords.expenses.feature.events.domain.model.Currency
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class EventCreationStateHolderTest {

    private val currencyEur = Currency(id = 1L, serverId = null, code = "EUR", name = "Euro", rate = BigDecimal.ONE)
    private val currencyUsd = Currency(id = 2L, serverId = null, code = "USD", name = "US Dollar", rate = BigDecimal.ONE)

    @Test
    fun initialState_allFieldsEmpty() {
        val holder = EventCreationStateHolder()
        assertEquals("", holder.getDraftEventName())
        assertEquals(0L, holder.getDraftPrimaryCurrencyId())
        assertEquals("", holder.getDraftOwner())
        assertTrue(holder.getDraftOtherPersons().isEmpty())
    }

    @Test
    fun draftEventName_trimsAndStores() {
        val holder = EventCreationStateHolder()
        holder.draftEventName("  My Event  ")
        assertEquals("My Event", holder.getDraftEventName())
    }

    @Test
    fun draftEventPrimaryCurrency_storesCurrencyId() {
        val holder = EventCreationStateHolder()
        holder.draftEventPrimaryCurrency(currencyEur)
        assertEquals(1L, holder.getDraftPrimaryCurrencyId())
        holder.draftEventPrimaryCurrency(currencyUsd)
        assertEquals(2L, holder.getDraftPrimaryCurrencyId())
    }

    @Test
    fun draftOwner_trimsAndStores() {
        val holder = EventCreationStateHolder()
        holder.draftOwner("  Alice  ")
        assertEquals("Alice", holder.getDraftOwner())
    }

    @Test
    fun draftOtherPersons_trimsAndFiltersEmpty() {
        val holder = EventCreationStateHolder()
        holder.draftOtherPersons(listOf("  Bob  ", "", "  Charlie  ", "  "))
        assertEquals(listOf("Bob", "Charlie"), holder.getDraftOtherPersons())
    }

    @Test
    fun clear_resetsAllFields() {
        val holder = EventCreationStateHolder()
        holder.draftEventName("Event")
        holder.draftEventPrimaryCurrency(currencyEur)
        holder.draftOwner("Alice")
        holder.draftOtherPersons(listOf("Bob"))
        holder.clear()
        assertEquals("", holder.getDraftEventName())
        assertEquals(0L, holder.getDraftPrimaryCurrencyId())
        assertEquals("", holder.getDraftOwner())
        assertTrue(holder.getDraftOtherPersons().isEmpty())
    }

    @Test
    fun draftOtherPersons_emptyList_storesEmpty() {
        val holder = EventCreationStateHolder()
        holder.draftOtherPersons(emptyList())
        assertTrue(holder.getDraftOtherPersons().isEmpty())
    }
}
