package com.inwords.expenses.core.observability

import io.sentry.kotlin.multiplatform.SentryOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SentryOptionsConfigurationTest {
    @Test
    fun `configures Apple C++ monitoring off while preserving shared options`() {
        val options = SentryOptions()

        configureSentryOptions(options, production = true)

        assertEquals("production", options.environment)
        assertEquals(0.2, options.tracesSampleRate)
        assertFalse(options.enableUnhandledCppExceptionMonitoring)
    }
}
