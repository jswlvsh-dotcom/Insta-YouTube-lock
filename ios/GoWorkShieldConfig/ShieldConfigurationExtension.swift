import Foundation
import ManagedSettings
import ManagedSettingsUI
import UIKit

/// Draws the lock screen ("shield") iOS shows over Instagram, YouTube and the other chosen apps.
final class ShieldConfigurationExtension: ShieldConfigurationDataSource {

    override func configuration(shielding application: Application) -> ShieldConfiguration {
        makeConfiguration(appName: application.localizedDisplayName)
    }

    override func configuration(shielding application: Application, in category: ActivityCategory) -> ShieldConfiguration {
        makeConfiguration(appName: application.localizedDisplayName ?? category.localizedDisplayName)
    }

    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        makeConfiguration(appName: webDomain.domain)
    }

    override func configuration(shielding webDomain: WebDomain, in category: ActivityCategory) -> ShieldConfiguration {
        makeConfiguration(appName: webDomain.domain ?? category.localizedDisplayName)
    }

    private let volt = UIColor(red: 1.0, green: 0.84, blue: 0.04, alpha: 1)
    private let flame = UIColor(red: 1.0, green: 0.27, blue: 0.23, alpha: 1)
    private let sky = UIColor(red: 0.39, green: 0.82, blue: 1.0, alpha: 1)
    private let ink = UIColor(red: 0.04, green: 0.04, blue: 0.06, alpha: 1)
    private let muted = UIColor(white: 0.72, alpha: 1)

    private func makeConfiguration(appName: String?) -> ShieldConfiguration {
        let settings = GoSettings.load()
        let now = Date()
        let app = appName ?? "This app"
        let stats = StatsStore.load(now)
        let reason = ShieldState.current(settings: settings, now: now)
        let goal = settings.goal.trimmingCharacters(in: .whitespacesAndNewlines)
        let goalLine = goal.isEmpty ? "" : "\n🎯 \(goal)"
        let emergenciesLeft = max(0, 3 - stats.emergencies)

        switch reason {
        case .limit, .focus, .sprint:
            let accent = reason == .limit ? flame : sky
            let title: String
            let detail: String
            switch reason {
            case .limit:
                title = "That's enough for today."
                detail = "\(app) is locked until midnight."
            case .focus:
                title = "It's focus time."
                detail = "\(app) is locked until \(FocusWindow.label(minute: settings.focusEndMinute))."
            default:
                title = "You're in a sprint."
                let end = ShieldState.sprintEndsAt?.formatted(date: .omitted, time: .shortened) ?? "later"
                detail = "\(app) is locked until \(end)."
            }
            let emergencyHint = emergenciesLeft > 0
                ? "\nEmergency? Tap it twice, 30 s apart, for 5 minutes."
                : "\nNo emergency unlocks left today."
            return ShieldConfiguration(
                backgroundBlurStyle: .systemUltraThinMaterialDark,
                backgroundColor: ink.withAlphaComponent(0.92),
                icon: symbol(reason == .limit ? "hourglass" : "lock.fill", color: accent),
                title: .init(text: title, color: .white),
                subtitle: .init(text: "\(detail) \(GoMessages.goDoYourWork(settings))\(goalLine)\(emergencyHint)", color: muted),
                primaryButtonLabel: .init(text: "Back to work", color: ink),
                primaryButtonBackgroundColor: accent,
                secondaryButtonLabel: emergenciesLeft > 0
                    ? ShieldConfiguration.Label(text: emergencyButtonText(now: now, left: emergenciesLeft), color: .white)
                    : nil
            )

        case .open:
            return ShieldConfiguration(
                backgroundBlurStyle: .systemUltraThinMaterialDark,
                backgroundColor: ink.withAlphaComponent(0.92),
                icon: symbol("wind", color: volt),
                title: .init(text: "Wait. Why are you here?", color: .white),
                subtitle: .init(
                    text: "Take one breath before opening \(app).\(goalLine)\nNeed it for work? Tap “Open anyway” twice, 5 s apart.",
                    color: muted
                ),
                primaryButtonLabel: .init(text: "Never mind, back to work", color: ink),
                primaryButtonBackgroundColor: volt,
                secondaryButtonLabel: .init(text: continueButtonText(now: now, wait: 5, label: "Open anyway"), color: .white)
            )

        case .nudge:
            let minutes = stats.lastNudgeMinutes
            let used = minutes > 0 ? "\(minutes) min on your apps today. " : ""
            let wait = settings.continueWaitSeconds
            return ShieldConfiguration(
                backgroundBlurStyle: .systemUltraThinMaterialDark,
                backgroundColor: ink.withAlphaComponent(0.92),
                icon: symbol("bolt.fill", color: volt),
                title: .init(text: GoMessages.goDoYourWork(settings).uppercased(), color: .white),
                subtitle: .init(
                    text: "\(used)\(GoMessages.line())\(goalLine)\nReally need more? Tap “Continue” twice, \(wait) s apart.",
                    color: muted
                ),
                primaryButtonLabel: .init(text: "I'm going to work 💪", color: ink),
                primaryButtonBackgroundColor: volt,
                secondaryButtonLabel: .init(
                    text: continueButtonText(now: now, wait: wait, label: "Continue \(settings.nudgeIntervalMinutes) more min"),
                    color: .white
                )
            )
        }
    }

    private func continueButtonText(now: Date, wait: Int, label: String) -> String {
        guard let first = ShieldState.continueRequestedAt, now.timeIntervalSince(first) < 120 else { return label }
        let left = Int((TimeInterval(wait) - now.timeIntervalSince(first)).rounded(.up))
        return left > 0 ? "Wait \(left)s, then tap again" : "Tap again to continue"
    }

    private func emergencyButtonText(now: Date, left: Int) -> String {
        guard let first = ShieldState.emergencyRequestedAt, now.timeIntervalSince(first) < 180 else {
            return "Emergency 5 min (\(left) left)"
        }
        let wait = Int((30 - now.timeIntervalSince(first)).rounded(.up))
        return wait > 0 ? "Wait \(wait)s, then tap again" : "Tap again to unlock 5 min"
    }

    private func symbol(_ name: String, color: UIColor) -> UIImage? {
        let config = UIImage.SymbolConfiguration(pointSize: 46, weight: .bold)
        return UIImage(systemName: name, withConfiguration: config)?.withTintColor(color, renderingMode: .alwaysOriginal)
    }
}
