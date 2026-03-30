package com.inwords.expenses.feature.expenses.domain

import androidx.annotation.VisibleForTesting
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Instant

object ExpenseTimeBackdoor {

    @Volatile
    private var overriddenNow: Instant? = null

    fun now(): Instant = overriddenNow ?: Clock.System.now()

    @VisibleForTesting
    fun overrideForTests(now: Instant?) {
        overriddenNow = now
    }
}
