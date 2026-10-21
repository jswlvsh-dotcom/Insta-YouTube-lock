import DeviceActivity
import FamilyControls
import Foundation
import ManagedSettings

/// Handles the two buttons on the shield.
///
/// Primary   → "I'm going to work": closes the app.
/// Secondary → "Continue" / "Emergency": must be tapped twice, a few seconds apart, so it is
///             always a conscious choice. (iOS gives shields no countdown, so this is the friction.)
final class ShieldActionExtension: ShieldActionDelegate {

    override func handle(action: ShieldAction, for application: ApplicationToken, completionHandler: @escaping (ShieldActionResponse) -> Void) {
        completionHandler(respond(to: action))
    }

    override func handle(action: ShieldAction, for webDomain: WebDomainToken, completionHandler: @escaping (ShieldActionResponse) -> Void) {
        completionHandler(respond(to: action))
    }

    override func handle(action: ShieldAction, for category: ActivityCategoryToken, completionHandler: @escaping (ShieldActionResponse) -> Void) {
        completionHandler(respond(to: action))
    }

    private func respond(to action: ShieldAction) -> ShieldActionResponse {
        let settings = GoSettings.load()
        let now = Date()
        // Drop locks whose time is over even if iOS hasn't delivered the "interval ended" callback yet.
        Shields.refreshHardShields(settings: settings, now: now)
        let reason = ShieldState.current(settings: settings, now: now)

        switch action {
        case .primaryButtonPressed:
            StatsStore.update { $0.wentToWork += 1 }
            ShieldState.continueRequestedAt = nil
            ShieldState.emergencyRequestedAt = nil
            if !reason.isHard, settings.mindfulOpen {
                // The shield stays, so coming straight back still needs the two-tap "Continue";
                // the next open asks "why are you here?" instead of repeating the reminder.
                ShieldState.nudgeReason = .open
            }
            return .close

        case .secondaryButtonPressed:
            return reason.isHard ? emergency(now: now, settings: settings) : continueAnyway(now: now, settings: settings)

        @unknown default:
            return .close
        }
    }

    private func continueAnyway(now: Date, settings: GoSettings) -> ShieldActionResponse {
        let wait = TimeInterval(ShieldState.nudgeReason == .open ? 5 : settings.continueWaitSeconds)
        if let first = ShieldState.continueRequestedAt, now.timeIntervalSince(first) < 120 {
            if now.timeIntervalSince(first) >= wait {
                ShieldState.continueRequestedAt = nil
                StatsStore.update { $0.continues += 1 }
                Shields.clear(.nudge)
            }
            return .defer
        }
        ShieldState.continueRequestedAt = now
        return .defer
    }

    private func emergency(now: Date, settings: GoSettings) -> ShieldActionResponse {
        guard StatsStore.load(now).emergencies < 3 else { return .defer }
        if let first = ShieldState.emergencyRequestedAt, now.timeIntervalSince(first) < 180 {
            if now.timeIntervalSince(first) >= 30 {
                do {
                    try Scheduler.startEmergency()
                    ShieldState.emergencyRequestedAt = nil
                    StatsStore.update { $0.emergencies += 1 }
                    Shields.clear(.nudge)
                    Shields.refreshHardShields(settings: settings, now: now)
                } catch {
                    // Could not schedule the re-lock, so keep the apps locked.
                }
            }
            return .defer
        }
        ShieldState.emergencyRequestedAt = now
        return .defer
    }
}
