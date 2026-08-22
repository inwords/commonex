package com.inwords.expenses.core.observability

import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryOptions

fun initializeSentry(production: Boolean) {
    Sentry.init { options ->
        configureSentryOptions(options, production)
    }
}

internal fun configureSentryOptions(options: SentryOptions, production: Boolean) {
    options.dsn = "https://b0246893378b693eb484df8c63be12c4@o4509536090783751.ingest.de.sentry.io/4509536110510160"
    options.environment = if (production) "production" else "development"
    options.tracesSampleRate = if (production) 0.2 else 1.0
    options.enableUnhandledCppExceptionMonitoring = false
}
