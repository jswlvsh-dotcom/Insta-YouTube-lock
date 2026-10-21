import Foundation

/// Storage shared by the app and its Screen Time extensions (App Group container).
enum AppGroup {
    /// Read from Info.plist (`GoWorkAppGroup`), which Xcode fills from Config/Signing.xcconfig.
    static let identifier: String = {
        if let id = Bundle.main.object(forInfoDictionaryKey: "GoWorkAppGroup") as? String,
           !id.isEmpty, !id.contains("$(") {
            return id
        }
        return "group.com.jaivish.gowork"
    }()

    static let defaults: UserDefaults = UserDefaults(suiteName: identifier) ?? .standard
}

/// "2026-10-21" in the current calendar; used as the key for daily stats.
enum DayKey {
    static func string(for date: Date = Date()) -> String {
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }
}
