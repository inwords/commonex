package com.inwords.expenses.core.analytics

fun initializeAppMetrica(
    production: Boolean,
    analyticsBridge: AnalyticsBridge,
) {
    val config = AppMetricaRuntimeConfig(
        apiKey = "607d7507-03da-4359-9a17-6d6269826c7c",
        dataSendingEnabled = production,
        appOpenTrackingEnabled = false,
        locationTracking = false,
        sessionsAutoTrackingEnabled = true,
        revenueAutoTrackingEnabled = false,
        logsEnabled = !production,
    )

    analyticsBridge.setupAnalytics(config)
}
