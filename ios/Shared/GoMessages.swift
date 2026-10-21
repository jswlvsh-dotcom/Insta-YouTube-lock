import Foundation
import UserNotifications

enum GoMessages {
    static let lines = [
        "Scrolling won't edit your videos.",
        "Your future self is watching. Close it.",
        "Creators create. Consumers scroll. Pick one.",
        "That reel can wait. Your goals can't.",
        "Nobody got rich watching Shorts.",
        "The algorithm is winning. Take your time back.",
        "Close the app. Open your project.",
        "Discipline now, freedom later.",
        "Your competition isn't scrolling right now.",
        "Make content. Don't just consume it.",
        "One small task. Start it now.",
        "Two minutes gone. Don't make it twenty.",
        "Put the phone down. Pick the work up.",
        "This feed is someone else's work. Go make yours.",
        "Ten focused minutes beat two hours of scrolling.",
        "The edit won't finish itself.",
    ]

    static func line(seed: Int = Int(Date().timeIntervalSince1970 / 60)) -> String {
        lines[((seed % lines.count) + lines.count) % lines.count]
    }

    /// "Jaivish, go do your work." or "Go do your work."
    static func goDoYourWork(_ settings: GoSettings) -> String {
        let name = settings.displayName
        return name.isEmpty ? "Go do your work." : "\(name), go do your work."
    }
}

/// Local notifications posted by the monitor extension.
enum Notifier {
    private static let lastKey = "gowork.lastNotificationAt"

    static func send(id: String, title: String, body: String, minimumGap: TimeInterval = 45) {
        let defaults = AppGroup.defaults
        let now = Date().timeIntervalSince1970
        if now - defaults.double(forKey: lastKey) < minimumGap { return }
        defaults.set(now, forKey: lastKey)

        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        let request = UNNotificationRequest(identifier: id, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
    }
}
