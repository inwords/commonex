package com.inwords.expenses.feature.events.data.db.store

import com.inwords.expenses.feature.events.data.db.dao.PersonsDao
import com.inwords.expenses.feature.events.data.db.entity.PersonEntity
import com.inwords.expenses.feature.events.domain.model.Person
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal class PersonsLocalStoreImplTest {

    @Test
    fun `mixed person inserts and updates preserve stable identity and match returned ids by position`() = runTest {
        val dao = mockk<PersonsDao>()
        val store = PersonsLocalStoreImpl(lazy { dao })
        val fresh = Person(0L, null, "Alex", "alex-client")
        val existing = Person(12L, "sam-server", "Sam renamed", "sam-client")
        val another = Person(0L, "jo-server", "Jo", "jo-client")
        coEvery { dao.insert(any()) } returns listOf(101L, -1L, 103L)

        val result = store.insertWithoutCrossRefs(listOf(fresh, existing, another))

        assertEquals(listOf(fresh.copy(id = 101L), existing, another.copy(id = 103L)), result)
        coVerify(exactly = 1) {
            dao.insert(
                listOf(
                    PersonEntity(0L, null, "alex-client", "Alex"),
                    PersonEntity(12L, "sam-server", "sam-client", "Sam renamed"),
                    PersonEntity(0L, "jo-server", "jo-client", "Jo"),
                )
            )
        }
    }
}
