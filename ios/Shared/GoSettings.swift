import Foundation

/// Everything the user can configure. Stored as JSON in the App Group.
struct GoSettings: Codable, Equatable {
    var onboarded = false
    var protectionEnabled = true
    var userName = ""
    var goal = ""
    /// Shield + "Go do your work" every N minutes of use on the chosen apps.
    var nudgeIntervalMinutes = 2
    /// Seconds between the two taps on "Continue anyway".
    var continueWaitSeconds = 10
    /// Daily limit for the chosen apps together; 0 = no limit.
    var dailyLimitMinutes = 60
    /// Keep apps shielded after "I'm going to work", so every open needs a decision.
    var mindfulOpen = true
    var notifyOnNudge = true
    var strictMode = false
    var focusEnabled = false
    var focusStartMinute = 10 * 60
    var focusEndMinute = 18 * 60
    /// Calendar weekdays: 1 = Sunday … 7 = Saturday.
    var focusWeekdays: Set<Int> = [2, 3, 4, 5, 6, 7]

    private static let key = "gowork.settings"

    static func load(from defaults: UserDefaults = AppGroup.defaults) -> GoSettings {
        guard let data = defaults.data(forKey: key),
              let settings = try? JSONDecoder().decode(GoSettings.self, from: data) else {
            return GoSettings()
        }
        return settings
    }

    func save(to defaults: UserDefaults = AppGroup.defaults) {
        if let data = try? JSONEncoder().encode(self) {
            defaults.set(data, forKey: Self.key)
        }
    }

    var displayName: String { userName.trimmingCharacters(in: .whitespacesAndNewlines) }
}

extension GoSettings {
    /// Tolerates missing keys, so adding settings later never wipes the saved ones.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = GoSettings()
        onboarded = try c.decodeIfPresent(Bool.self, forKey: .onboarded) ?? d.onboarded
        protectionEnabled = try c.decodeIfPresent(Bool.self, forKey: .protectionEnabled) ?? d.protectionEnabled
        userName = try c.decodeIfPresent(String.self, forKey: .userName) ?? d.userName
        goal = try c.decodeIfPresent(String.self, forKey: .goal) ?? d.goal
        nudgeIntervalMinutes = try c.decodeIfPresent(Int.self, forKey: .nudgeIntervalMinutes) ?? d.nudgeIntervalMinutes
        continueWaitSeconds = try c.decodeIfPresent(Int.self, forKey: .continueWaitSeconds) ?? d.continueWaitSeconds
        dailyLimitMinutes = try c.decodeIfPresent(Int.self, forKey: .dailyLimitMinutes) ?? d.dailyLimitMinutes
        mindfulOpen = try c.decodeIfPresent(Bool.self, forKey: .mindfulOpen) ?? d.mindfulOpen
        notifyOnNudge = try c.decodeIfPresent(Bool.self, forKey: .notifyOnNudge) ?? d.notifyOnNudge
        strictMode = try c.decodeIfPresent(Bool.self, forKey: .strictMode) ?? d.strictMode
        focusEnabled = try c.decodeIfPresent(Bool.self, forKey: .focusEnabled) ?? d.focusEnabled
        focusStartMinute = try c.decodeIfPresent(Int.self, forKey: .focusStartMinute) ?? d.focusStartMinute
        focusEndMinute = try c.decodeIfPresent(Int.self, forKey: .focusEndMinute) ?? d.focusEndMinute
        focusWeekdays = try c.decodeIfPresent(Set<Int>.self, forKey: .focusWeekdays) ?? d.focusWeekdays
    }
}

/// Focus hours, same rules as the Android version.
enum FocusWindow {
    static func isActive(_ settings: GoSettings, at date: Date = Date(), calendar: Calendar = .current) -> Bool {
        guard settings.focusEnabled, !settings.focusWeekdays.isEmpty else { return false }
        let start = settings.focusStartMinute
        let end = settings.focusEndMinute
        guard start != end else { return false }
        let parts = calendar.dateComponents([.hour, .minute, .weekday], from: date)
        let minute = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
        let weekday = parts.weekday ?? 1
        let yesterday = weekday == 1 ? 7 : weekday - 1
        if start < end {
            return settings.focusWeekdays.contains(weekday) && minute >= start && minute < end
        }
        return (minute >= start && settings.focusWeekdays.contains(weekday)) ||
            (minute < end && settings.focusWeekdays.contains(yesterday))
    }

    static func label(minute: Int) -> String {
        var parts = DateComponents()
        parts.hour = minute / 60
        parts.minute = minute % 60
        let date = Calendar.current.date(from: parts) ?? Date()
        return date.formatted(date: .omitted, time: .shortened)
    }
}
