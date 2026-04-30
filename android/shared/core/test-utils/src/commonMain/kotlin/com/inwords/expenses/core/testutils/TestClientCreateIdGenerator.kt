package com.inwords.expenses.core.testutils

import com.inwords.expenses.core.utils.ClientCreateIdGenerator

class TestClientCreateIdGenerator(
    vararg ids: String,
) : ClientCreateIdGenerator {

    private val iterator = ids.iterator()

    override fun generate(): String = iterator.next()
}
