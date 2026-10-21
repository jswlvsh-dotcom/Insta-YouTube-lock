import DeviceActivity
import SwiftUI

extension DeviceActivityReport.Context {
    /// Today's usage, total plus app-by-app.
    static let today = Self("GoWork.today")
    /// Daily totals for the last 7 days.
    static let week = Self("GoWork.week")
}

extension Color {
    static let goInk = Color(red: 0.043, green: 0.043, blue: 0.059)
    static let goPanel = Color(red: 0.082, green: 0.082, blue: 0.110)
    static let goPanelHigh = Color(red: 0.118, green: 0.118, blue: 0.157)
    static let goStroke = Color(red: 0.153, green: 0.153, blue: 0.204)
    static let goVolt = Color(red: 1.0, green: 0.84, blue: 0.04)
    static let goFlame = Color(red: 1.0, green: 0.27, blue: 0.23)
    static let goMint = Color(red: 0.20, green: 0.84, blue: 0.29)
    static let goSky = Color(red: 0.39, green: 0.82, blue: 1.0)
    static let goMuted = Color(red: 0.62, green: 0.62, blue: 0.68)
}

enum DurationText {
    /// "1h 05m", "23m", "40s".
    static func short(_ interval: TimeInterval) -> String {
        let total = max(0, Int(interval))
        let hours = total / 3600
        let minutes = (total % 3600) / 60
        if hours > 0 { return String(format: "%dh %02dm", hours, minutes) }
        if minutes > 0 { return "\(minutes)m" }
        return "\(total)s"
    }
}
