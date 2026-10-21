import SwiftUI

@main
struct GoWorkApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .preferredColorScheme(.dark)
                .tint(.goVolt)
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack {
            Color.goInk.ignoresSafeArea()
            if model.settings.onboarded && model.isAuthorized {
                MainTabView()
            } else {
                OnboardingView()
            }
        }
        .onAppear { model.refresh() }
        .onChange(of: scenePhase) { phase in
            if phase == .active { model.refresh() }
        }
    }
}

struct MainTabView: View {
    var body: some View {
        TabView {
            DashboardView()
                .tabItem { Label("Today", systemImage: "bolt.fill") }
            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
        }
    }
}
