package com.inwords.expenses.integration.base

import com.inwords.expenses.core.observability.initializeSentry as initializeSentryCore

fun initializeSentry(production: Boolean) {
    initializeSentryCore(production)
}
