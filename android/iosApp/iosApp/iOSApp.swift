import SwiftUI
import sharedIntegrationBase

@main
struct iOSApp: App {

    init() {
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
