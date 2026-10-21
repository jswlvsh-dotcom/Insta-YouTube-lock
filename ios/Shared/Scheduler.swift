import DeviceActivity
import FamilyControls
import Foundation

/// Registers the Screen Time schedules that drive every reminder and lock.
///
/// - `daily` (00:00–23:59, repeating) carries one event per reminder (every N minutes of use
///   on the chosen apps) and one event for the daily limit. The monitor extension reacts.
/// - `focus.<weekday>` intervals shield the apps during focus hours.
/// - `sprint` and `emergency` are one-off intervals started on demand.
enum Scheduler {
    /// Upper bound on reminder events in one day (keeps the schedule small).
    static let maxNudgeEvents = 120
    /// Without a daily limit, reminders cover this many minutes of use.
    static let reminderHorizonMinutes = 240

    enum SchedulerError: LocalizedError {
        case nothingSelected

        var errorDescription: String? { "Pick at least one app first." }
    }

    /// Restarts monitoring from the saved settings. Returns a readable error, or nil.
    @discardableResult
    static func apply(settings: GoSettings = .load(), selection: FamilyActivitySelection = SelectionStore.load()) -> String? {
        let center = DeviceActivityCenter()
        let keep: [DeviceActivityName] = [.sprint, .emergency]
        center.stopMonitoring(center.activities.filter { !keep.contains($0) })

        guard settings.protectionEnabled, !selection.isEmptySelection else {
            Shields.clear(.nudge)
            Shields.refreshHardShields(settings: settings)
            return nil
        }
        do {
            try startDaily(center: center, settings: settings, selection: selection)
            if settings.focusEnabled {
                try startFocus(center: center, settings: settings)
            }
        } catch {
            return describe(error)
        }
        Shields.refreshHardShields(settings: settings)
        return nil
    }

    static func stopAll() {
        let center = DeviceActivityCenter()
        center.stopMonitoring(center.activities)
        Shields.clearAll()
    }

    private static func startDaily(center: DeviceActivityCenter, settings: GoSettings, selection: FamilyActivitySelection) throws {
        let schedule = DeviceActivitySchedule(
            intervalStart: DateComponents(hour: 0, minute: 0, second: 0),
            intervalEnd: DateComponents(hour: 23, minute: 59, second: 59),
            repeats: true,
            warningTime: nil
        )
        var events: [DeviceActivityEvent.Name: DeviceActivityEvent] = [:]
        let interval = max(1, settings.nudgeIntervalMinutes)
        let limit = settings.dailyLimitMinutes
        let horizon = limit > 0 ? limit : reminderHorizonMinutes
        var minute = interval
        while minute < horizon, events.count < maxNudgeEvents {
            // Reminders count from when GoWork starts watching, so no backlog fires at once.
            events[.nudge(minutes: minute)] = event(for: selection, minutes: minute, includesPastActivity: false)
            minute += interval
        }
        if limit > 0 {
            // The limit counts the whole day, including time before GoWork was set up.
            events[.limit] = event(for: selection, minutes: limit, includesPastActivity: true)
        }
        try center.startMonitoring(.daily, during: schedule, events: events)
    }

    private static func startFocus(center: DeviceActivityCenter, settings: GoSettings) throws {
        let start = settings.focusStartMinute
        let end = settings.focusEndMinute
        guard start != end else { return }
        for weekday in settings.focusWeekdays.sorted() {
            let endWeekday = end > start ? weekday : (weekday % 7) + 1
            let schedule = DeviceActivitySchedule(
                intervalStart: DateComponents(hour: start / 60, minute: start % 60, weekday: weekday),
                intervalEnd: DateComponents(hour: end / 60, minute: end % 60, weekday: endWeekday),
                repeats: true,
                warningTime: nil
            )
            try center.startMonitoring(.focus(weekday: weekday), during: schedule, events: [:])
        }
    }

    /// Hard-locks the chosen apps for `minutes`.
    static func startSprint(minutes: Int, settings: GoSettings = .load()) throws {
        let selection = SelectionStore.load()
        guard !selection.isEmptySelection else { throw SchedulerError.nothingSelected }
        let now = Date()
        let end = now.addingTimeInterval(TimeInterval(max(15, minutes) * 60))
        try startOneOff(.sprint, from: now, to: end, events: [:])
        ShieldState.sprintEndsAt = end
        Shields.refreshHardShields(settings: settings, now: now)
    }

    static func endSprint(settings: GoSettings = .load()) {
        DeviceActivityCenter().stopMonitoring([.sprint])
        ShieldState.sprintEndsAt = nil
        Shields.refreshHardShields(settings: settings)
    }

    /// Lifts hard locks for 5 minutes of use (or 16 minutes of clock time, whichever is first).
    static func startEmergency(minutes: Int = 5) throws {
        let selection = SelectionStore.load()
        let now = Date()
        let used = event(for: selection, minutes: minutes, includesPastActivity: false)
        try startOneOff(.emergency, from: now, to: now.addingTimeInterval(16 * 60), events: [.emergencyUsed: used])
        ShieldState.emergencyUntil = now.addingTimeInterval(16 * 60)
    }

    private static func startOneOff(
        _ name: DeviceActivityName,
        from start: Date,
        to end: Date,
        events: [DeviceActivityEvent.Name: DeviceActivityEvent]
    ) throws {
        let calendar = Calendar.current
        let schedule = DeviceActivitySchedule(
            intervalStart: calendar.dateComponents([.hour, .minute, .second], from: start),
            intervalEnd: calendar.dateComponents([.hour, .minute, .second], from: end),
            repeats: false,
            warningTime: nil
        )
        let center = DeviceActivityCenter()
        center.stopMonitoring([name])
        try center.startMonitoring(name, during: schedule, events: events)
    }

    static func event(for selection: FamilyActivitySelection, minutes: Int, includesPastActivity: Bool) -> DeviceActivityEvent {
        let threshold = DateComponents(hour: minutes / 60, minute: minutes % 60)
        if #available(iOS 17.4, *) {
            return DeviceActivityEvent(
                applications: selection.applicationTokens,
                categories: selection.categoryTokens,
                webDomains: selection.webDomainTokens,
                threshold: threshold,
                includesPastActivity: includesPastActivity
            )
        }
        return DeviceActivityEvent(
            applications: selection.applicationTokens,
            categories: selection.categoryTokens,
            webDomains: selection.webDomainTokens,
            threshold: threshold
        )
    }

    private static func describe(_ error: Error) -> String {
        if let monitoring = error as? DeviceActivityCenter.MonitoringError {
            switch monitoring {
            case .intervalTooShort:
                return "Focus hours must be at least 15 minutes long."
            case .intervalTooLong:
                return "That schedule is too long for iOS."
            case .excessiveActivities:
                return "Too many schedules. Turn off focus hours on some days."
            case .invalidDateComponents:
                return "iOS didn't accept that time range."
            case .unauthorized:
                return "Screen Time permission is missing. Open GoWork and allow it again."
            @unknown default:
                return monitoring.localizedDescription
            }
        }
        return error.localizedDescription
    }
}
