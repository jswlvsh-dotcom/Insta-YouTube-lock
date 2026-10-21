import FamilyControls
import Foundation

/// The apps / categories / websites picked in the FamilyActivityPicker (opaque tokens).
enum SelectionStore {
    private static let key = "gowork.selection"

    static func load() -> FamilyActivitySelection {
        guard let data = AppGroup.defaults.data(forKey: key),
              let selection = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data) else {
            return FamilyActivitySelection()
        }
        return selection
    }

    static func save(_ selection: FamilyActivitySelection) {
        if let data = try? JSONEncoder().encode(selection) {
            AppGroup.defaults.set(data, forKey: key)
        }
    }
}

extension FamilyActivitySelection {
    var isEmptySelection: Bool {
        applicationTokens.isEmpty && categoryTokens.isEmpty && webDomainTokens.isEmpty
    }

    var itemCount: Int {
        applicationTokens.count + categoryTokens.count + webDomainTokens.count
    }
}

/// What happened today.
struct DayStats: Codable, Equatable {
    var nudges = 0
    var wentToWork = 0
    var continues = 0
    var emergencies = 0
    var limitHit = false
    var lastNudgeMinutes = 0
}

enum StatsStore {
    private static func key(_ day: String) -> String { "gowork.day.\(day)" }

    static func load(_ date: Date = Date()) -> DayStats {
        load(day: DayKey.string(for: date))
    }

    static func load(day: String) -> DayStats {
        guard let data = AppGroup.defaults.data(forKey: key(day)),
              let stats = try? JSONDecoder().decode(DayStats.self, from: data) else {
            return DayStats()
        }
        return stats
    }

    static func update(_ date: Date = Date(), _ change: (inout DayStats) -> Void) {
        let day = DayKey.string(for: date)
        var stats = load(day: day)
        change(&stats)
        if let data = try? JSONEncoder().encode(stats) {
            AppGroup.defaults.set(data, forKey: key(day))
        }
    }

    /// Days in a row (ending today) without hitting the daily limit, since GoWork was set up.
    static func limitStreak(since firstDay: Date, today: Date = Date()) -> Int {
        let calendar = Calendar.current
        let start = calendar.startOfDay(for: firstDay)
        var day = calendar.startOfDay(for: today)
        var streak = 0
        while day >= start, streak < 365 {
            if load(day).limitHit { break }
            streak += 1
            guard let previous = calendar.date(byAdding: .day, value: -1, to: day) else { break }
            day = previous
        }
        return streak
    }
}

/// Why the apps are shielded right now. The shield screens read this to pick their text.
enum ShieldReason: String {
    case open
    case nudge
    case limit
    case focus
    case sprint
}

/// Small pieces of state shared between the monitor, the shield screens and the app.
enum ShieldState {
    private static var d: UserDefaults { AppGroup.defaults }

    /// open = "why are you here?", nudge = "go do your work".
    static var nudgeReason: ShieldReason {
        get { ShieldReason(rawValue: d.string(forKey: "gowork.nudgeReason") ?? "") ?? .nudge }
        set { d.set(newValue.rawValue, forKey: "gowork.nudgeReason") }
    }

    static var continueRequestedAt: Date? {
        get { d.object(forKey: "gowork.continueRequestedAt") as? Date }
        set { d.set(newValue, forKey: "gowork.continueRequestedAt") }
    }

    static var emergencyRequestedAt: Date? {
        get { d.object(forKey: "gowork.emergencyRequestedAt") as? Date }
        set { d.set(newValue, forKey: "gowork.emergencyRequestedAt") }
    }

    /// While set and in the future, hard locks (limit / focus / sprint) are lifted.
    static var emergencyUntil: Date? {
        get { d.object(forKey: "gowork.emergencyUntil") as? Date }
        set { d.set(newValue, forKey: "gowork.emergencyUntil") }
    }

    static var sprintEndsAt: Date? {
        get { d.object(forKey: "gowork.sprintEndsAt") as? Date }
        set { d.set(newValue, forKey: "gowork.sprintEndsAt") }
    }

    /// First day GoWork was set up (for streaks).
    static var firstDay: Date {
        if let date = d.object(forKey: "gowork.firstDay") as? Date { return date }
        let now = Date()
        d.set(now, forKey: "gowork.firstDay")
        return now
    }

    static func emergencyActive(_ now: Date = Date()) -> Bool {
        guard let until = emergencyUntil else { return false }
        return until > now
    }

    static func sprintActive(_ now: Date = Date()) -> Bool {
        guard let end = sprintEndsAt else { return false }
        return end > now
    }

    /// The strongest reason that currently applies.
    static func current(settings: GoSettings, now: Date = Date()) -> ShieldReason {
        if !emergencyActive(now) {
            if sprintActive(now) { return .sprint }
            if FocusWindow.isActive(settings, at: now) { return .focus }
            if StatsStore.load(now).limitHit { return .limit }
        }
        return nudgeReason
    }
}

extension ShieldReason {
    /// Hard locks only open with an emergency unlock.
    var isHard: Bool { self == .limit || self == .focus || self == .sprint }
}
