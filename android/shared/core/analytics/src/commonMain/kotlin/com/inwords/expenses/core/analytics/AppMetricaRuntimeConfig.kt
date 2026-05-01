package com.inwords.expenses.core.analytics

data class AppMetricaRuntimeConfig(
    val apiKey: String,
    val dataSendingEnabled: Boolean,
    val appOpenTrackingEnabled: Boolean,
    val locationTracking: Boolean,
    val sessionsAutoTrackingEnabled: Boolean,
    val revenueAutoTrackingEnabled: Boolean,
    val logsEnabled: Boolean,
)
