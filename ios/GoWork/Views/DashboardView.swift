import DeviceActivity
import FamilyControls
import SwiftUI

struct DashboardView: View {
    @EnvironmentObject private var model: AppModel
    @State private var now = Date()
    @State private var confirmEndSprint = false
    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                statusCard
                if let error = model.scheduleError {
                    GoCard(border: .goFlame) {
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .foregroundColor(.goFlame)
                            .font(.subheadline)
                    }
                }
                reportCard(title: "Your apps today", context: .today, filter: watchedFilter(for: todayInterval), height: 360)
                reportCard(title: "Last 7 days · your apps", context: .week, filter: watchedFilter(for: weekInterval), height: 210)
                sprintCard
                winsCard
                reportCard(title: "Whole phone today", context: .today, filter: phoneFilter, height: 360)
                Text("Everything stays on this iPhone. iOS never shows GoWork your app names or data outside these cards.")
                    .font(.footnote)
                    .foregroundColor(.goMuted)
            }
            .padding(20)
        }
        .background(Color.goInk.ignoresSafeArea())
        .onReceive(timer) { now = $0 }
        .alert("End the sprint?", isPresented: $confirmEndSprint) {
            Button("End it", role: .destructive) { model.endSprint() }
            Button("Keep going", role: .cancel) {}
        } message: {
            Text("Future you will thank you for finishing it.")
        }
    }

    // MARK: Sections

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(model.settings.displayName.isEmpty ? "Hey 👋" : "Hey \(model.settings.displayName) 👋")
                .font(.headline)
                .foregroundColor(.goMuted)
            Headline(text: "Go do your work.", size: 36)
        }
    }

    private var statusCard: some View {
        let on = model.settings.protectionEnabled && !model.selection.isEmptySelection
        return GoCard(border: on ? .goStroke : .goFlame) {
            HStack {
                Circle().fill(on ? Color.goMint : Color.goFlame).frame(width: 9, height: 9)
                SectionLabel(
                    text: on ? "Protection on · \(model.selection.itemCount) apps watched" : "Protection off",
                    color: on ? .goMint : .goFlame
                )
            }
            HStack(spacing: 12) {
                StatBlock(value: "\(model.settings.nudgeIntervalMinutes) min", label: "\"Go do your work\" every", color: .goVolt)
                StatBlock(
                    value: model.settings.dailyLimitMinutes == 0 ? "Off" : "\(model.settings.dailyLimitMinutes) min",
                    label: "daily limit"
                )
                StatBlock(value: model.today.limitHit ? "Locked" : "Open", label: "until midnight", color: model.today.limitHit ? .goFlame : .goMint)
            }
            if !model.settings.goal.isEmpty {
                Text("🎯 \(model.settings.goal)")
                    .font(.subheadline.weight(.semibold))
            }
        }
    }

    private var sprintCard: some View {
        GoCard(border: model.sprintEndsAt != nil ? .goSky : .goStroke) {
            SectionLabel(text: "Focus sprint", color: model.sprintEndsAt != nil ? .goSky : .goMuted)
            if let end = model.sprintEndsAt, end > now {
                Text(remaining(until: end))
                    .font(.system(size: 44, weight: .black, design: .rounded))
                    .monospacedDigit()
                Text("Your apps are locked until \(end.formatted(date: .omitted, time: .shortened)).")
                    .font(.subheadline)
                    .foregroundColor(.goMuted)
                Button(model.settings.strictMode ? "Strict mode: no stopping early" : "End sprint early") {
                    confirmEndSprint = true
                }
                .buttonStyle(SecondaryButtonStyle())
                .disabled(model.settings.strictMode)
            } else {
                Text("Lock your apps and do one task until the timer ends.")
                    .font(.subheadline)
                    .foregroundColor(.goMuted)
                HStack(spacing: 8) {
                    ForEach([15, 25, 45, 60], id: \.self) { minutes in
                        Button("\(minutes)m") { model.startSprint(minutes: minutes) }
                            .buttonStyle(SecondaryButtonStyle())
                    }
                }
            }
        }
    }

    private var winsCard: some View {
        GoCard {
            SectionLabel(text: "Your wins today")
            HStack(spacing: 12) {
                StatBlock(value: "🔥 \(model.streak)", label: model.streak == 1 ? "day under your limit" : "days under your limit")
                StatBlock(value: "\(model.today.wentToWork)", label: "times you went to work", color: .goMint)
                StatBlock(value: "\(model.today.nudges)", label: "reminders today")
            }
        }
    }

    private func reportCard(title: String, context: DeviceActivityReport.Context, filter: DeviceActivityFilter, height: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel(text: title)
            DeviceActivityReport(context, filter: filter)
                .frame(height: height)
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(Color.goStroke, lineWidth: 1))
        }
    }

    // MARK: Filters

    private var todayInterval: DateInterval {
        let calendar = Calendar.current
        let start = calendar.startOfDay(for: Date())
        let end = calendar.date(byAdding: .day, value: 1, to: start) ?? start.addingTimeInterval(86_400)
        return DateInterval(start: start, end: end)
    }

    private var weekInterval: DateInterval {
        let calendar = Calendar.current
        let today = todayInterval
        let start = calendar.date(byAdding: .day, value: -6, to: today.start) ?? today.start
        return DateInterval(start: start, end: today.end)
    }

    private func watchedFilter(for interval: DateInterval) -> DeviceActivityFilter {
        DeviceActivityFilter(
            segment: .daily(during: interval),
            users: .all,
            devices: .all,
            applications: model.selection.applicationTokens,
            categories: model.selection.categoryTokens,
            webDomains: model.selection.webDomainTokens
        )
    }

    private var phoneFilter: DeviceActivityFilter {
        DeviceActivityFilter(
            segment: .daily(during: todayInterval),
            users: .all,
            devices: .all,
            applications: [],
            categories: [],
            webDomains: []
        )
    }

    private func remaining(until end: Date) -> String {
        let seconds = max(0, Int(end.timeIntervalSince(now)))
        return String(format: "%d:%02d", seconds / 60, seconds % 60)
    }
}
