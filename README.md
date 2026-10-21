# GoWork — Instagram & YouTube Lock ⚡

> **Go do your work.**

GoWork runs in the background on your phone, watches how long you spend on
**Instagram, YouTube** (and any other app you pick) and pulls you out:

- Every **2 minutes** of scrolling a full-screen lock appears: **"GO DO YOUR WORK."**
- Hit your **daily limit** → the app stays locked until midnight.
- Optional **Reels / Shorts blocker**, **focus hours**, **focus sprints**, **strict mode**.
- Reads your **real screen time** so you see exactly where the day went.

> **Hinglish mein:** App background mein chalta rehta hai. Jaise hi aap Instagram ya
> YouTube kholte ho, woh time count karta hai, aur har 2 minute pe poori screen pe lock
> aa jaata hai — *"Go do your work."* "Continue" sirf countdown ke baad milta hai, aur har
> baar countdown lamba hota jaata hai. Daily limit khatam → app midnight tak band.

| | Android | iPhone |
|---|---|---|
| "Go do your work" lock every N min (default 2) | ✅ full-screen overlay, countdown before *Continue*, longer every time | ✅ system shield, *Continue* needs 2 taps a few seconds apart |
| Mindful pause on open ("Why are you here?") | ✅ | ✅ |
| Daily limit → locked until midnight | ✅ per app, 3 emergency unlocks/day | ✅ all chosen apps together, 3 emergency unlocks/day |
| Reels / Shorts blocker (rest of the app still works) | ✅ beta | ❌ iOS does not allow it |
| Focus hours + focus sprints (15–60 min) | ✅ | ✅ |
| Strict mode (60 s wait to change settings) | ✅ | ✅ |
| Real screen time: today, per app, last 7 days | ✅ | ✅ |
| Spoken reminder ("Jaivish, go do your work.") | ✅ optional | – (notification instead) |
| Pauses the video behind the lock | ✅ | – |
| Runs in background, survives reboot | ✅ | ✅ (handled by iOS) |

---

## 📱 Android — install the APK

1. Open the repository on GitHub → **Actions** → **Android** → newest green run →
   **Artifacts** → download **GoWork-android** (a zip with `GoWork-<n>.apk` inside).
2. Unzip and open the APK on your phone. Allow *"Install unknown apps"* when Android asks.
3. Open **GoWork** and follow the setup. It asks for four things:

| Permission | Why | Where |
|---|---|---|
| **Accessibility** (required) | Sees which app is on screen and draws the lock | Settings → Accessibility → *GoWork Focus Guard* → On |
| **Usage access** | Reads your real screen time | Settings → Usage access → GoWork |
| **Notifications** | "GoWork is watching" status with today's time | Android pop-up |
| **Battery: Unrestricted** | Stops battery saver from killing the guard | Android pop-up |

> ⚠️ **"Restricted setting" / switch is grey?** Android 13+ blocks Accessibility for apps
> installed from an APK. Fix: **Settings → Apps → GoWork → ⋮ (top right) → Allow restricted
> settings**, then turn on Accessibility again. The setup screen has a button for this.
>
> **Xiaomi / Redmi / POCO, Oppo, Vivo, Realme, OnePlus:** also enable **Autostart** for GoWork and
> lock it in the recent-apps screen. **Samsung:** Settings → Battery → Background usage limits →
> add GoWork to *Never auto sleeping apps*.

The release APK from CI is signed with a temporary debug key, so to update later you may need
to uninstall the old version first (your stats reset). See *Signing* below for a permanent key.

### Build it yourself (Android)

Needs JDK 17 and the Android SDK (Android Studio is easiest).

```bash
cd android
./gradlew testDebugUnitTest   # engine tests
./gradlew assembleRelease     # → app/build/outputs/apk/release/app-release.apk
```

**Signing:** set `GOWORK_KEYSTORE_FILE`, `GOWORK_KEYSTORE_PASSWORD`, `GOWORK_KEY_ALIAS` and
`GOWORK_KEY_PASSWORD` to sign release builds with your own key (otherwise the debug key is used).

---

## 🍎 iPhone — build and install with Xcode

iOS only allows this kind of app through Apple's **Screen Time API (Family Controls)**. That means:

- You need a **Mac with Xcode 16 or newer**.
- You need a **paid Apple Developer account** ($99/year). Free accounts cannot use Family Controls.
- The app goes onto your iPhone straight from Xcode (no App Store needed for personal use).

Steps:

```bash
brew install xcodegen
cd ios
# 1. open Config/Signing.xcconfig and fill in DEVELOPMENT_TEAM (your Team ID)
#    and, if you like, change GOWORK_BUNDLE_ID to something unique to you
xcodegen generate
open GoWork.xcodeproj
```

2. In Xcode pick your iPhone as the run destination and press **Run** ▶.
   (First time: on the iPhone allow *Settings → General → VPN & Device Management → Developer App*,
   and turn on *Developer Mode* if iOS asks.)
3. In GoWork: allow **Screen Time access**, then choose **Instagram and YouTube** in the picker.

If Xcode complains about the App Group or Family Controls, open each target's
*Signing & Capabilities* tab once — automatic signing registers everything for you.

---

## How it works

```
android/   Kotlin + Jetpack Compose
  engine/FocusEngine.kt        the brain: sessions, reminders, limits, focus, sprints (pure Kotlin, unit-tested)
  service/GuardAccessibilityService.kt   sees the foreground app, Reels/Shorts detection, shows the lock
  service/LockOverlay.kt       full-screen lock window (accessibility overlay, above everything)
  service/GuardService.kt      always-on notification that keeps the guard alive
  system/ScreenTimeReader.kt   real screen time from UsageStatsManager

ios/       SwiftUI + Screen Time API (generated with XcodeGen from project.yml)
  GoWork/                      the app: onboarding, dashboard, settings
  GoWorkMonitor/               DeviceActivityMonitor: fires every N min of use → shields the apps
  GoWorkShieldConfig/          what the lock looks like ("GO DO YOUR WORK.", your goal, stats)
  GoWorkShieldAction/          what the lock buttons do (close app, two-tap continue, emergency)
  GoWorkReport/                DeviceActivityReport: today's screen time + 7-day chart
```

**Reminder logic (both platforms):** time on all watched apps is added together. Every
*N* minutes (default 2) the lock appears. "I'm going to work" sends you home; "Continue"
gives you another *N* minutes. On Android a break of 3+ minutes starts a fresh session (with the
mindful pause on the next open); on iOS the reminders are counted per day.

## Honest limits

- **Android:** Reels/Shorts detection looks for Instagram's and YouTube's internal screen
  ids; when those apps update, it can stop working until the ids in
  `ShortFormDetector.kt` are updated. Anyone holding the phone can still turn off
  Accessibility in Settings; strict mode only slows changes inside GoWork.
- **iPhone:** Apple doesn't allow a countdown on the shield or detecting Reels/Shorts.
  iOS decides exactly when Screen Time events fire, so a reminder can come a little late.
  Publishing on the App Store would need Apple's Family Controls distribution approval.

## Privacy

Everything stays on the phone. No accounts, no servers, no analytics. On Android GoWork only
reads which app is in front and (for the Reels blocker) a few screen element ids. On iOS Apple's
Screen Time API hides app names and usage from the app itself; only the sandboxed report
extension can draw them.
