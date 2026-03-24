package com.inwords.expenses.core.observability

interface ObservabilityExceptionScope : ObservabilityMessageScope {

    fun setMessage(message: String)
}
