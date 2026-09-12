# Hotspot Scheduler

Native Android app (Kotlin, MVVM, Room, Coroutines/Flow, Hilt, Jetpack Compose) that schedules the
**Samsung Mobile Hotspot** and **mobile data** on/off via time-of-day routines, data-cap rules, or both.
Target: Android 13 (API 33, compileSdk 34), tested device profile: Samsung Galaxy A22, One UI 5.1.

Because Android 10 removed the public hotspot API, hotspot control requires UI automation.
This app runs **two toggle engines** and picks the best one automatically:

| | Accessibility engine (fallback) | Shizuku engine (preferred) |
|---|---|---|
| Opens Settings | Yes, every toggle | Never |
| Speed | 2-8 seconds | Instant |
| Screen must be on | Yes | No |
| Works on lock screen | Swipe/none only | Yes, even with PIN |
| Routine password | Typed on screen | Passed to the system command |

Both engines are safety-gated: a switch is only clicked when its row text positively matches
("Mobile Hotspot" / "Mobile data"), unrelated rows (Bluetooth, Data saver, Roaming) are excluded,
and only windows belonging to `com.android.settings` are ever read.

## The two engines

**Shizuku engine** — the app talks to the [Shizuku](https://shizuku.rikka.app) service (ADB-level
privileges, no root) through a bound `UserService` and runs: `svc data enable|disable` for mobile
data and `cmd wifi start-softap <ssid> wpa2 <passphrase>` / `cmd wifi stop-softap` (Android 13)
for the hotspot. Because the shell command's config is session-only and the saved Settings
passphrase is not retrievable (masked in `dumpsys`), the app learns your SSID from the live
system state, caches it (encrypted), and uses the routine's password when one is set. Toggles
are verified against the actual hotspot state after every command and are instant, silent, and
work while locked.

**Accessibility engine** — drives the One UI Settings screens with 4 matching strategies
(saved calibration → exact `switch_widget` id → Switch class → text-proximity), read-verify-retry
state machine (state read before and after every click, retry once, abort with notification),
wake + keyguard handling, and adaptive menu navigation (clicks through "Connections → Data usage"
itself). Every attempt is recorded to the persistent diagnostics log.

## Behavior decisions

- **Boundary-only enforcement**: ON at window start, OFF at window end; manual changes between
  boundaries are never fought.
- **Strictest cap wins**: one shared daily hotspot counter (reset at midnight); overlapping
  routines use the lowest cap. Cap hit → OFF + suppressed until midnight.
- **"Turn off now"** suppresses automation until the next window starts; **"Pause for today"**
  pauses until midnight.
- **Optional mobile data per routine**: ON at window start; OFF at end only if no other active
  routine needs it.
- **Per-routine hotspot password** (optional): applied at window start; empty = keep the password
  already configured in Settings. Stored AES-GCM-encrypted (Android Keystore); never exported.
- Alerts (cap reached, toggle failure, both engines unavailable) use a high-importance channel.

## Build

1. Open the folder in Android Studio (JDK 17). AGP 8.1.3 / Gradle 8.1.1 / Kotlin 1.9.10 / KSP.
2. Run on the Galaxy A22. Unit tests: `gradlew testDebugUnitTest` (also run in CI on every push;
   CI publishes every green build to GitHub Releases).

## Setup — permissions with One UI 5.1 paths

Open the app's **Setup** tab; each card shows live status and opens the right system screen.

1. **Background engine (Shizuku)** — recommended: install Shizuku from the Play Store, start it
   via Wireless debugging (pairing code), then tap Grant in this app. Skip if you prefer the
   accessibility path.
2. **Accessibility Service** — Settings → Accessibility → Installed apps → Hotspot Scheduler → On.
   Re-verified on every app open and every service tick (only nags if Shizuku is not ready).
3. **Usage Access** — Settings → Apps → ⋮ → Special access → Usage access → Allow.
4. **Notifications** — Android 13 runtime dialog on first launch.
5. **Alarms and reminders** — Settings → Apps → Hotspot Scheduler → Alarms and reminders → Allow.
6. **Battery** — tap to ignore optimizations, then Battery and device care → Battery → Background
   usage limits → **Never sleeping apps** → add this app; make sure it is not in Deep sleeping apps.
7. **Display over other apps** — needed only for the accessibility engine to launch Settings from
   the background. Shizuku engine does not need it.
8. **Lock screen** — with a PIN, no app can unlock the phone (Android rule): the app wakes the
   screen and waits up to 3 minutes for you to unlock. For hands-free nights use Settings →
   Lock screen → **Extend Unlock** → Trusted places (home).

## Calibration

Setup → Open calibration → Start → the hotspot Settings screen opens → tap the row that is the
real hotspot switch → confirm. Persisted and prioritized by the accessibility engine. Clear it
after One UI updates. Not needed for the Shizuku engine.

## Diagnostics

Setup → Diagnostics: persistent log (survives restarts) of every automation attempt, including a
control-dump of any screen where a toggle failed, plus Share (text) and Clear buttons. The
**Live test** card fires Hotspot ON/OFF and Data ON/OFF on demand using the same code path as
routines.

## Architecture

```
ui        Compose screens + ViewModels (routines, editor, setup, calibration, usage)
service   HotspotAutomationService (foreground), NotificationHelper, WidgetProvider
toggle    ShizukuEngine + IShellService (AIDL UserService)
accessibility  HotspotAccessibilityService, NodeMatcher, NodeDumper, controller facade
core      RoutineEvaluator (pure, unit-tested), AlarmScheduler, receivers
data      Room, DataStore, UsageMonitor (NetworkStatsManager), repository
di/util   Hilt modules, PasswordCrypto (Keystore), AttemptLog
```
