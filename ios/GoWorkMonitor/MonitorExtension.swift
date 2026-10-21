import DeviceActivity
import FamilyControls
import Foundation
import ManagedSettings

/// Runs in the background (iOS wakes it up) whenever a Screen Time schedule or threshold fires.
final class MonitorExtension: DeviceActivityMonitor {

    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        let settings = GoSettings.load()
        guard settings.protectionEnabled else {
            Shields.clearAll()
            return
        }
        if activity == .daily {
            // A new day: the limit lock lifts, and with "mindful open" every first open needs a decision.
            ShieldState.continueRequestedAt = nil
            ShieldState.emergencyRequestedAt = nil
            if settings.mindfulOpen {
                ShieldState.nudgeReason = .open
                Shields.apply(.nudge)
            } else {
                Shields.clear(.nudge)
            }
        }
        Shields.refreshHardShields(settings: settings)
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        let settings = GoSettings.load()
        if activity == .sprint {
            ShieldState.sprintEndsAt = nil
            Notifier.send(
                id: "gowork.sprint",
                title: "Sprint done 🎉",
                body: "Nice work. Take a real break — not a scroll break.",
                minimumGap: 0
            )
        } else if activity == .emergency {
            ShieldState.emergencyUntil = nil
        }
        Shields.refreshHardShields(settings: settings)
    }

    override func eventDidReachThreshold(_ event: DeviceActivityEvent.Name, activity: DeviceActivityName) {
        super.eventDidReachThreshold(event, activity: activity)
        let settings = GoSettings.load()

        if activity == .emergency, event == .emergencyUsed {
            ShieldState.emergencyUntil = nil
            DeviceActivityCenter().stopMonitoring([.emergency])
            Shields.refreshHardShields(settings: settings)
            return
        }

        guard activity == .daily, settings.protectionEnabled else { return }

        if event == .limit {
            StatsStore.update { $0.limitHit = true }
            Shields.refreshHardShields(settings: settings)
            Notifier.send(
                id: "gowork.limit",
                title: "That's enough for today.",
                body: "Your apps are locked until midnight. \(GoMessages.goDoYourWork(settings))",
                minimumGap: 0
            )
            return
        }

        if let minutes = event.nudgeMinutes {
            StatsStore.update {
                $0.nudges += 1
                $0.lastNudgeMinutes = minutes
            }
            ShieldState.nudgeReason = .nudge
            ShieldState.continueRequestedAt = nil
            Shields.apply(.nudge)
            if settings.notifyOnNudge {
                Notifier.send(
                    id: "gowork.nudge",
                    title: GoMessages.goDoYourWork(settings),
                    body: "\(minutes) min on your apps today. \(GoMessages.line())"
                )
            }
        }
    }
}
