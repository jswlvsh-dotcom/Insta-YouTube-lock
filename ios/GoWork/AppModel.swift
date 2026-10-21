import FamilyControls
import Foundation
import SwiftUI
import UserNotifications

/// App state. Every change is saved to the App Group and pushed to the Screen Time schedules.
final class AppModel: ObservableObject {
    @Published var settings: GoSettings {
        didSet {
            guard settings != oldValue else { return }
            settings.save()
            settingsChanged(from: oldValue)
        }
    }

    @Published var selection: FamilyActivitySelection {
        didSet {
            SelectionStore.save(selection)
            reschedule()
        }
    }

    @Published private(set) var authorization: AuthorizationStatus
    @Published var scheduleError: String?
    @Published private(set) var today = DayStats()
    @Published var strictUnlockedUntil = Date.distantPast

    init() {
        settings = GoSettings.load()
        selection = SelectionStore.load()
        authorization = AuthorizationCenter.shared.authorizationStatus
        today = StatsStore.load()
        _ = ShieldState.firstDay
    }

    /// Any granted state counts (newer iOS versions add more "approved" variants).
    var isAuthorized: Bool { authorization != .notDetermined && authorization != .denied }

    @MainActor
    func requestAuthorization() async {
        do {
            try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
        } catch {
            scheduleError = "Screen Time access was not granted: \(error.localizedDescription)"
        }
        authorization = AuthorizationCenter.shared.authorizationStatus
        _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
    }

    func refresh() {
        authorization = AuthorizationCenter.shared.authorizationStatus
        today = StatsStore.load()
        if isAuthorized, settings.onboarded {
            Shields.refreshHardShields(settings: settings)
        }
        objectWillChange.send()
    }

    func reschedule() {
        guard isAuthorized, settings.onboarded else { return }
        scheduleError = Scheduler.apply(settings: settings, selection: selection)
    }

    func finishOnboarding() {
        settings.onboarded = true
        if settings.mindfulOpen {
            ShieldState.nudgeReason = .open
            Shields.apply(.nudge, selection: selection)
        }
        reschedule()
    }

    private func settingsChanged(from old: GoSettings) {
        let new = settings
        if old.mindfulOpen != new.mindfulOpen, new.protectionEnabled {
            if new.mindfulOpen {
                ShieldState.nudgeReason = .open
                Shields.apply(.nudge, selection: selection)
            } else if ShieldState.nudgeReason == .open {
                Shields.clear(.nudge)
            }
        }
        let scheduleChanged = old.protectionEnabled != new.protectionEnabled
            || old.nudgeIntervalMinutes != new.nudgeIntervalMinutes
            || old.dailyLimitMinutes != new.dailyLimitMinutes
            || old.focusEnabled != new.focusEnabled
            || old.focusStartMinute != new.focusStartMinute
            || old.focusEndMinute != new.focusEndMinute
            || old.focusWeekdays != new.focusWeekdays
        if scheduleChanged { reschedule() }
    }

    // MARK: Sprint

    var sprintEndsAt: Date? { ShieldState.sprintActive() ? ShieldState.sprintEndsAt : nil }

    func startSprint(minutes: Int) {
        do {
            try Scheduler.startSprint(minutes: minutes, settings: settings)
            scheduleError = nil
        } catch {
            scheduleError = "Couldn't start the sprint: \(error.localizedDescription)"
        }
        objectWillChange.send()
    }

    func endSprint() {
        Scheduler.endSprint(settings: settings)
        objectWillChange.send()
    }

    // MARK: Stats & strict mode

    var streak: Int { StatsStore.limitStreak(since: ShieldState.firstDay) }

    func canEdit(now: Date = Date()) -> Bool { !settings.strictMode || now < strictUnlockedUntil }
}
