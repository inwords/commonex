package com.inwords.expenses.core.observability

interface ObservabilityMessageScope {

    var level: ObservabilityLevel

    fun setContext(key: String, value: String)
}
