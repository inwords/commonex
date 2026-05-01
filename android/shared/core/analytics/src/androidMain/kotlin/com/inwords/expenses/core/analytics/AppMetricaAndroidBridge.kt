package com.inwords.expenses.core.analytics

import android.app.Application
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

class AppMetricaAndroidBridge(
    private val application: Application,
) : AnalyticsBridge {

    override fun setupAnalytics(config: AppMetricaRuntimeConfig) {
        val appMetricaConfigBuilder = AppMetricaConfig.newConfigBuilder(config.apiKey)
            .withDataSendingEnabled(config.dataSendingEnabled)
            .withAppOpenTrackingEnabled(config.appOpenTrackingEnabled)
            .withLocationTracking(config.locationTracking)
            .withCrashReporting(false)
            .withNativeCrashReporting(false)
            .withAnrMonitoring(false)
            .withSessionsAutoTrackingEnabled(config.sessionsAutoTrackingEnabled)
            .withRevenueAutoTrackingEnabled(config.revenueAutoTrackingEnabled)
        if (config.logsEnabled) {
            appMetricaConfigBuilder.withLogs()
        }
        val appMetricaConfig = appMetricaConfigBuilder.build()

        AppMetrica.activate(application, appMetricaConfig)
        if (config.sessionsAutoTrackingEnabled) {
            AppMetrica.enableActivityAutoTracking(application)
        }
    }
}
