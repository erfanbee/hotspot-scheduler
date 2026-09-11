# Hotspot Scheduler

Native Android app (Kotlin, MVVM, Room, Coroutines/Flow, Hilt, Jetpack Compose) that schedules the
**Samsung Mobile Hotspot** on/off via time-of-day routines, data-cap rules, or both.
Target: Android 13 (API 33), tested device profile: Samsung Galaxy A22, One UI 5.1.

Because Android 10 removed the public hotspot API, **all hotspot control is done through an
AccessibilityService that automates the Settings toggle** — no hidden/reflective
WifiManager/ConnectivityManager calls are used anywhere.

## Behavior decisions (as agreed)

- **Boundary-only enforcement**: the app turns hotspot ON at a routine window start and OFF at the
  window end. It never fights manual changes between boundaries.
- **Strictest cap wins**: one shared "hotspot data today" counter (reset at midnight). While
  overlapping routines are active, the lowest cap among them governs. Cap hit -> OFF + suppressed
  until midnight.
- **"Turn off now"**: turns hotspot OFF and suppresses automation until the next window start.
- **"Pause automation for today"**: pauses all actions until midnight (does not toggle the hotspot).
- **Alerts** (cap reached, toggle failure, accessibility disabled) use a high-importance channel with
  sound + vibration. The persistent status notification is silent.
- Nice-to-haves included: 7-day usage chart, home-screen widget, JSON export/import.

## Build

1. Open the `HotspotScheduler` folder in Android Studio (Hedgehog or newer), JDK 17.
2. Let Gradle sync (AGP 8.1.3 / Gradle 8.1.1 / Kotlin 1.9.10). If `gradlew` is missing the wrapper
   jar, Android Studio will offer to generate it, or run `gradle wrapper` once.
3. `Run` on the Galaxy A22.

## Package layout

```
com.iranjan.hotspotscheduler
 ├─ ui          Compose screens + ViewModels (routines, editor, setup, calibration, usage)
 ├─ service     HotspotAutomationService (foreground), NotificationHelper, ActionReceiver, WidgetProvider
 ├─ accessibility  HotspotAccessibilityService, NodeMatcher (4 strategies), NodeDumper, HotspotController
 ├─ core        RoutineEvaluator, AlarmScheduler, AlarmReceiver, SystemEventsReceiver
 ├─ data        Room (db), DataStore (prefs), UsageMonitor (NetworkStatsManager), RoutineRepository
 ├─ di          Hilt modules
 └─ util        AccessibilityUtils (service health check), Formatters
```

## Setup — permissions with One UI 5.1 paths

Open the app, go to the **Setup** tab. Each card shows live Granted/Not-set status.

1. **Accessibility Service** (required)
   Settings → Accessibility → Installed apps → Hotspot Scheduler → turn **On** → Allow.
   The app re-verifies this on every app open and every 2-minute service tick; if it goes missing a
   high-priority notification with a "Re-enable" button appears.
2. **Usage Access** (required for data caps)
   Settings → Apps → ⋮ (More) → Special access → Usage access → Hotspot Scheduler → **Allow**.
   Used to query tethering traffic with NetworkStatsManager (tethering UID attribution; falls back
   to the mobile device-total bucket if the OEM blocks per-UID reads).
3. **Notifications** (required on Android 13)
   The runtime POST_NOTIFICATIONS dialog appears on first launch; you can also tap the Setup card.
4. **Alarms and reminders** (required for exact scheduling)
   Settings → Apps → Hotspot Scheduler → Alarms and reminders → **Allow**. Without it the app falls
   back to inexact `setWindow`.
5. **Battery optimizations** (required for reliability)
   Tap the card to request exemption, then:
   Settings → Battery and device care → Battery → Background usage limits → **Never sleeping apps** →
   add **Hotspot Scheduler**. Also open **Put unused apps to sleep** and turn it Off (or verify the
   app is not in **Deep sleeping apps**). This is the #1 reason automations die overnight on One UI.
6. **Display over other apps** (strongly recommended)
   Settings → Apps → Hotspot Scheduler → Appear on top → **Allow**.
   With this, the app can open the hotspot Settings screen by itself in the background and toggle
   hands-free. Without it, each toggle opens a high-priority "Action needed" notification — tap it,
   the hotspot Settings screen opens, and the app presses the switch itself.

## Calibration — one-time walkthrough

1. Setup tab → **Open calibration** → **Start calibration**.
2. The app attempts a direct launch of the One UI Mobile Hotspot settings screen. If it cannot
   (no overlay permission), tap the "Action needed" notification or navigate manually:
   Settings → Connections → **Mobile Hotspot and Tethering**.
3. The accessibility service dumps every node on that screen (class, resource-id, text,
   content-description, bounds, clickable/checkable/checked) into the on-screen list and Logcat
   (tag `HSAuto`). You can also hit **Rescan current screen** while anywhere in Settings.
4. Tap the row that is the hotspot switch — on One UI this is normally
   `android.widget.Switch` with id `com.android.settings:id/switch_widget`, paired with a
   `com.android.settings:id/switch_text` sibling reading On/Off. Confirm in the dialog.
5. The confirmed identifier is persisted and takes priority on every future toggle. If it ever
   stops matching (One UI update), the app falls back through its built-in strategies:
   1. exact id `com.android.settings:id/switch_widget`
   2. any `android.widget.Switch` node on the screen
   3. text proximity: nodes containing "Hotspot" -> nearest clickable Switch in siblings/parents
   If all fail you get the manual-toggle failure notification — clear/re-run calibration.

## How scheduling works

- `AlarmScheduler` books the **next boundary** (earliest start or end across all enabled routines)
  plus the next **midnight** (cap/pause reset) with `setExactAndAllowWhileIdle` (RTC_WAKEUP), so it
  fires in Doze. `SystemEventsReceiver` re-reschedules on `BOOT_COMPLETED`, `TIME_SET`,
  `TIMEZONE_CHANGED`, and `MY_PACKAGE_REPLACED`; all times are computed via `java.time` in the
  current system timezone, so DST shifts are handled, and any missed boundary is reconciled once on
  the next service tick.
- `HotspotAutomationService` (foreground, `dataSync` type) ticks every 2 minutes:
  verifies accessibility health, polls tethering usage, enforces the strictest active cap,
  reconciles missed boundaries, updates the persistent status notification (hotspot state, active
  routine, today usage vs cap, accessibility status) and the home-screen widget.
- Toggle safety: the switch state is read **before and after** every action; if it does not change
  within 3 s the click is retried once, then the app aborts and alerts
  "Couldn't toggle hotspot automatically — please toggle manually" (all failures are logged under
  tag `HSAuto`).

## Testing tips

- `adb logcat -s HSAuto` shows navigation, matching strategy used, toggle retries, and failures.
- Create a routine 2 minutes in the future covering "every day" and watch the boundary fire.
- To test the cap rule quickly, set a tiny cap (e.g. 1 MB) and stream through the hotspot.

## Known constraints

- The hotspot switch state is only readable while the Samsung Settings hotspot screen is the
  foreground window; between checks the status notification shows the last automation-known state.
- Tethering usage attribution is OEM-dependent; the Setup README number should be cross-checked
  once against One UI's own "Mobile Hotspot" usage display and the routine cap adjusted accordingly.
