import AppMetricaCore
import Foundation
import sharedIntegrationBase

final class IOSAppMetricaBridge: NSObject, AnalyticsBridge {

    func setupAnalytics(config: AppMetricaRuntimeConfig) {
        guard let appMetricaConfig = AppMetricaConfiguration(apiKey: config.apiKey) else {
            fatalError("Failed to create AppMetricaConfiguration")
        }

        appMetricaConfig.dataSendingEnabled = config.dataSendingEnabled
        appMetricaConfig.appOpenTrackingEnabled = config.appOpenTrackingEnabled
        appMetricaConfig.locationTracking = config.locationTracking
        appMetricaConfig.accurateLocationTracking = false
        appMetricaConfig.allowsBackgroundLocationUpdates = false
        appMetricaConfig.sessionsAutoTracking = config.sessionsAutoTrackingEnabled
        appMetricaConfig.revenueAutoTrackingEnabled = config.revenueAutoTrackingEnabled
        appMetricaConfig.areLogsEnabled = config.logsEnabled

        AppMetrica.activate(with: appMetricaConfig)
    }
}
