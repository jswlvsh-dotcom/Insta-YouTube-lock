import DeviceActivity
import FamilyControls
import Foundation
import ManagedSettings

extension DeviceActivityName {
    static let daily = Self("gowork.daily")
    static let emergency = Self("gowork.emergency")
    static let sprint = Self("gowork.sprint")

    static func focus(weekday: Int) -> Self { Self("gowork.focus.\(weekday)") }

    var isFocus: Bool { rawValue.hasPrefix("gowork.focus.") }
}

extension DeviceActivityEvent.Name {
    static let limit = Self("gowork.limit")
    static let emergencyUsed = Self("gowork.emergency.used")

    static func nudge(minutes: Int) -> Self { Self("gowork.nudge.\(minutes)") }

    var nudgeMinutes: Int? {
        let prefix = "gowork.nudge."
        guard rawValue.hasPrefix(prefix) else { return nil }
        return Int(rawValue.dropFirst(prefix.count))
    }
}

extension ManagedSettingsStore.Name {
    /// Reminders and the mindful pause.
    static let nudge = Self("gowork.nudge")
    static let limit = Self("gowork.limit")
    static let focus = Self("gowork.focus")
    static let sprint = Self("gowork.sprint")
}

/// Puts the system "shield" (the lock screen) over the chosen apps.
enum Shields {
    static func apply(_ name: ManagedSettingsStore.Name, selection: FamilyActivitySelection = SelectionStore.load()) {
        let store = ManagedSettingsStore(named: name)
        store.shield.applications = selection.applicationTokens.isEmpty ? nil : selection.applicationTokens
        store.shield.applicationCategories = selection.categoryTokens.isEmpty
            ? nil
            : .specific(selection.categoryTokens)
        store.shield.webDomains = selection.webDomainTokens.isEmpty ? nil : selection.webDomainTokens
        store.shield.webDomainCategories = selection.categoryTokens.isEmpty
            ? nil
            : .specific(selection.categoryTokens)
    }

    static func clear(_ name: ManagedSettingsStore.Name) {
        ManagedSettingsStore(named: name).clearAllSettings()
    }

    static func clearAll() {
        [ManagedSettingsStore.Name.nudge, .limit, .focus, .sprint].forEach { clear($0) }
    }

    /// Re-applies limit / focus / sprint shields from the saved state.
    static func refreshHardShields(settings: GoSettings = .load(), now: Date = Date()) {
        guard settings.protectionEnabled else {
            clearAll()
            return
        }
        let selection = SelectionStore.load()
        if ShieldState.emergencyActive(now) {
            clear(.limit)
            clear(.focus)
            clear(.sprint)
            return
        }
        if StatsStore.load(now).limitHit { apply(.limit, selection: selection) } else { clear(.limit) }
        if FocusWindow.isActive(settings, at: now) { apply(.focus, selection: selection) } else { clear(.focus) }
        if ShieldState.sprintActive(now) { apply(.sprint, selection: selection) } else { clear(.sprint) }
    }
}
