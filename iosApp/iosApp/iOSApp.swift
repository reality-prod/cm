// SPDX-License-Identifier: AGPL-3.0-or-later
import SwiftUI
import Shared

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ZStack {
                ComposeView().ignoresSafeArea(.all)
                // The app switcher snapshots the app while it is inactive: cover repo content and tokens' context.
                if scenePhase != .active {
                    Color(red: 0.043, green: 0.059, blue: 0.078).ignoresSafeArea(.all)
                }
            }
            .preferredColorScheme(.dark)
            .onChange(of: scenePhase) { phase in
                if phase == .background { PlatformKt.appDidBackground() }
                if phase == .active { PlatformKt.appDidForeground() }
            }
            // codebergmobile://codeberg.org/owner/repo ... validated in Kotlin; unknown links are ignored.
            .onOpenURL { url in _ = PlatformKt.handleDeepLink(raw: url.absoluteString) }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
