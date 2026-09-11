package com.inwords.expenses.core.utils

import kotlin.uuid.Uuid

interface ClientCreateIdGenerator {

    fun generate(): String
}

class UuidClientCreateIdGenerator : ClientCreateIdGenerator {

    override fun generate(): String = Uuid.random().toString()
}

object ClientCreateId {

    fun fromServerId(serverId: String): String = "server:$serverId"
}
