import SwiftUI
import sharedIntegrationBase

@main
struct iOSApp: App {

    init() {
        #if DEBUG
        let production = false
        #else
        let production = true
        #endif

        InitializeSentryKt.initializeSentry(production: production)
        InitializeAppMetricaKt.initializeAppMetrica(
            production: production,
            analyticsBridge: IOSAppMetricaBridge()
        )
        RegisterComponentsKt.registerComponents()
        EnableSyncKt.enableSync()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea()
                .onOpenURL { url in
                    MainViewControllerKt.supplyDeeplink(deeplink: url.absoluteString)
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { userActivity in
                    guard let url = userActivity.webpageURL else { return }
                    MainViewControllerKt.supplyDeeplink(deeplink: url.absoluteString)
                }
        }
    }
}
