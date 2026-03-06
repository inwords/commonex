package com.inwords.expenses.integration.base.appfunctions

import androidx.appfunctions.service.AppFunctionConfiguration

/**
 * Provides [AppFunctionConfiguration] for the CommonEx AppFunctions service.
 * Used by [AppFunctionConfiguration.Provider] on the Application class.
 */
fun createAppFunctionConfiguration(): AppFunctionConfiguration =
    AppFunctionConfiguration.Builder()
        .addEnclosingClassFactory(CommonExAppFunctions::class.java) { CommonExAppFunctions() }
        .build()
