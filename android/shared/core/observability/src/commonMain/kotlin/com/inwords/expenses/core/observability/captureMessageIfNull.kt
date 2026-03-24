package com.inwords.expenses.core.observability

fun <T> T?.captureMessageIfNull(
    message: String,
    scopeCallback: ObservabilityMessageScope.() -> Unit = {},
): T? {
    if (this == null) {
        Observability.captureMessage(message, scopeCallback)
    }
    return this
}
