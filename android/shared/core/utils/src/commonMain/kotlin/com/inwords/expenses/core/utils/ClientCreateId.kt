package com.inwords.expenses.core.utils

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface ClientCreateIdGenerator {

    fun generate(): String
}

class UuidClientCreateIdGenerator : ClientCreateIdGenerator {

    @OptIn(ExperimentalUuidApi::class)
    override fun generate(): String = Uuid.random().toString()
}

object ClientCreateId {

    fun fromServerId(serverId: String): String = "server:$serverId"
}
