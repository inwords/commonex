package com.inwords.expenses.core.observability

import io.sentry.kotlin.multiplatform.Scope
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryLevel

object Observability {

    fun captureMessage(
        message: String,
        scopeCallback: ObservabilityMessageScope.() -> Unit = {},
    ) {
        val observabilityScope = MessageObservabilityScope().apply(scopeCallback)

        Sentry.captureMessage(message) { scope ->
            observabilityScope.applyTo(scope)
        }
    }

    fun captureException(
        throwable: Throwable,
        scopeCallback: ObservabilityExceptionScope.() -> Unit = {},
    ) {
        val observabilityScope = ExceptionObservabilityScope().apply(scopeCallback)

        Sentry.captureException(throwable) { scope ->
            observabilityScope.applyTo(scope)
        }
    }
}

private abstract class BaseObservabilityScope : ObservabilityMessageScope {

    override var level = ObservabilityLevel.ERROR

    protected val context: MutableMap<String, String> = hashMapOf()

    final override fun setContext(key: String, value: String) {
        context[key] = value
    }
}

private class MessageObservabilityScope : BaseObservabilityScope() {

    fun applyTo(scope: Scope) {
        scope.level = level.toSentryLevel()

        if (context.isNotEmpty()) {
            scope.setContext(DETAILS_CONTEXT_KEY, context)
        }
    }
}

private class ExceptionObservabilityScope : BaseObservabilityScope(), ObservabilityExceptionScope {

    private var message: String? = null

    override fun setMessage(message: String) {
        this.message = message
    }

    fun applyTo(scope: Scope) {
        scope.level = level.toSentryLevel()

        val message = message
        val reportContext = if (message != null) {
            buildMap {
                put(MESSAGE_CONTEXT_FIELD, message)
                putAll(context)
            }
        } else {
            context
        }
        if (reportContext.isNotEmpty()) {
            scope.setContext(DETAILS_CONTEXT_KEY, reportContext)
        }
    }
}

private fun ObservabilityLevel.toSentryLevel(): SentryLevel {
    return when (this) {
        ObservabilityLevel.DEBUG -> SentryLevel.DEBUG
        ObservabilityLevel.INFO -> SentryLevel.INFO
        ObservabilityLevel.WARNING -> SentryLevel.WARNING
        ObservabilityLevel.ERROR -> SentryLevel.ERROR
        ObservabilityLevel.FATAL -> SentryLevel.FATAL
    }
}

private const val DETAILS_CONTEXT_KEY = "details"
private const val MESSAGE_CONTEXT_FIELD = "message"
