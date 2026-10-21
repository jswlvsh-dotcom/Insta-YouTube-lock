import DeviceActivity
import ManagedSettings
import SwiftUI

/// Renders real Screen Time data inside the GoWork app. iOS keeps this data private:
/// it only ever reaches this sandboxed extension, never the app itself.
@main
struct GoWorkReportExtension: DeviceActivityReportExtension {
    @DeviceActivityReportBuilder
    var body: some DeviceActivityReportScene {
        TodayReport { summary in
            TodayReportView(summary: summary)
        }
        WeekReport { days in
            WeekReportView(days: days)
        }
    }
}

// MARK: - Today

struct AppUsage: Identifiable, Hashable {
    let id: String
    let name: String
    var duration: TimeInterval
    var pickups: Int
}

struct TodaySummary {
    var total: TimeInterval = 0
    var pickups = 0
    var apps: [AppUsage] = []
}

struct TodayReport: DeviceActivityReportScene {
    let context: DeviceActivityReport.Context = .today
    let content: (TodaySummary) -> TodayReportView

    func makeConfiguration(representing data: DeviceActivityResults<DeviceActivityData>) async -> TodaySummary {
        var apps: [String: AppUsage] = [:]
        var pickups = 0
        for await item in data {
            for await segment in item.activitySegments {
                pickups += segment.totalPickupsWithoutApplicationActivity
                for await category in segment.categories {
                    for await activity in category.applications {
                        let application = activity.application
                        let name = application.localizedDisplayName ?? application.bundleIdentifier ?? "App"
                        let key = application.bundleIdentifier ?? name
                        var entry = apps[key] ?? AppUsage(id: key, name: name, duration: 0, pickups: 0)
                        entry.duration += activity.totalActivityDuration
                        entry.pickups += activity.numberOfPickups
                        apps[key] = entry
                    }
                    for await web in category.webDomains {
                        let name = web.webDomain.domain ?? "Website"
                        var entry = apps[name] ?? AppUsage(id: name, name: name, duration: 0, pickups: 0)
                        entry.duration += web.totalActivityDuration
                        apps[name] = entry
                    }
                }
            }
        }
        let sorted = apps.values.filter { $0.duration >= 1 }.sorted { $0.duration > $1.duration }
        return TodaySummary(
            total: sorted.reduce(0) { $0 + $1.duration },
            pickups: pickups + sorted.reduce(0) { $0 + $1.pickups },
            apps: Array(sorted.prefix(8))
        )
    }
}

struct TodayReportView: View {
    let summary: TodaySummary

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline) {
                Text(DurationText.short(summary.total))
                    .font(.system(size: 40, weight: .black, design: .rounded))
                    .foregroundColor(.white)
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text("\(summary.pickups)")
                        .font(.title3.bold())
                        .foregroundColor(.goVolt)
                    Text("pickups")
                        .font(.caption)
                        .foregroundColor(.goMuted)
                }
            }
            if summary.apps.isEmpty {
                Text("No screen time yet today. Keep it that way.")
                    .font(.subheadline)
                    .foregroundColor(.goMuted)
            } else {
                let longest = max(summary.apps.first?.duration ?? 1, 1)
                ForEach(summary.apps) { app in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(app.name)
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(.white)
                                .lineLimit(1)
                            Spacer()
                            Text(DurationText.short(app.duration))
                                .font(.subheadline.monospacedDigit())
                                .foregroundColor(.goMuted)
                        }
                        GeometryReader { proxy in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Color.goPanelHigh)
                                Capsule()
                                    .fill(app.id == summary.apps.first?.id ? Color.goVolt : Color.goVolt.opacity(0.55))
                                    .frame(width: max(6, proxy.size.width * CGFloat(app.duration / longest)))
                            }
                        }
                        .frame(height: 6)
                    }
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.goPanel)
    }
}

// MARK: - Week

struct DayTotal: Identifiable, Hashable {
    var id: Date { date }
    let date: Date
    var total: TimeInterval
}

struct WeekReport: DeviceActivityReportScene {
    let context: DeviceActivityReport.Context = .week
    let content: ([DayTotal]) -> WeekReportView

    func makeConfiguration(representing data: DeviceActivityResults<DeviceActivityData>) async -> [DayTotal] {
        let calendar = Calendar.current
        var totals: [Date: TimeInterval] = [:]
        for await item in data {
            for await segment in item.activitySegments {
                let day = calendar.startOfDay(for: segment.dateInterval.start)
                var used: TimeInterval = 0
                for await category in segment.categories {
                    for await activity in category.applications {
                        used += activity.totalActivityDuration
                    }
                    for await web in category.webDomains {
                        used += web.totalActivityDuration
                    }
                }
                totals[day, default: 0] += used
            }
        }
        let today = calendar.startOfDay(for: Date())
        return (0..<7).reversed().compactMap { back in
            guard let day = calendar.date(byAdding: .day, value: -back, to: today) else { return nil }
            return DayTotal(date: day, total: totals[day] ?? 0)
        }
    }
}

struct WeekReportView: View {
    let days: [DayTotal]

    var body: some View {
        let peak = max(days.map(\.total).max() ?? 0, 60)
        let average = days.isEmpty ? 0 : days.map(\.total).reduce(0, +) / Double(days.count)
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .bottom, spacing: 10) {
                ForEach(days) { day in
                    let isToday = Calendar.current.isDateInToday(day.date)
                    VStack(spacing: 6) {
                        Text(day.total > 0 ? DurationText.short(day.total) : "–")
                            .font(.system(size: 9, weight: .semibold))
                            .foregroundColor(isToday ? .goVolt : .goMuted)
                            .lineLimit(1)
                            .minimumScaleFactor(0.6)
                        GeometryReader { proxy in
                            VStack {
                                Spacer(minLength: 0)
                                RoundedRectangle(cornerRadius: 6)
                                    .fill(isToday ? Color.goVolt : Color.goVolt.opacity(0.45))
                                    .frame(height: max(4, proxy.size.height * CGFloat(day.total / peak)))
                            }
                        }
                        Text(isToday ? "Today" : day.date.formatted(.dateTime.weekday(.abbreviated)))
                            .font(.caption2.weight(.medium))
                            .foregroundColor(isToday ? .white : .goMuted)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(height: 150)
            Text("Daily average \(DurationText.short(average))")
                .font(.caption)
                .foregroundColor(.goMuted)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.goPanel)
    }
}
