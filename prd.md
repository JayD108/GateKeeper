# Product Requirements Document (PRD)
**Project:** GateKeeper (Two-Phone Alert System)

## 1. Problem and Goal
**Problem:** P1 works at an office (L1). P2 works from home (L2). P2 frequently locks the gate and forgets, leaving P1 stuck outside when they return. Because both users are non-technical and 50+, they often miss single-chime notifications, and manual tracking apps are forgotten. Alerting P2 too early means they unlock the gate, go back to work, and forget again.
**Goal:** Automatically, reliably, and loudly alert P2 to unlock the gate exactly 3 minutes before P1 arrives, with zero manual input required from either user, effectively preventing physical lockouts.

## 2. Users and Use Case
- **Target Users:** Two adults, aged 50+. 
- **Needs:** The apps must be "set-it-and-forget-it". UI must be large, high-contrast, and extremely simple. No complex maps to navigate daily. The alert must be impossible to ignore.

## 3. Scope
**In Scope:**
- Time-filtered Geofence triggers (L1 Departure and L2 Proximity).
- Real-time live location tracking only during the active commute.
- 3-day location history.
- Two-Stage Alert System (Departure info vs. Proximity Alarm).
- "Persistent Siren" on P2 with 1-Tap Acknowledgment loop back to P1.
- Push notifications via internet (FCM).
- SMS fallback for offline/low-network scenarios.
- "HONK GATE" manual override widget.
- Auto-stop tracking when both devices are at L2.

**Out of Scope:**
- Route directions, ETA calculations, or traffic data.
- iOS support (Android only).
- Publishing to Google Play Store (side-loading APKs is required due to aggressive SMS permission restrictions).

## 4. Functional Requirements
1. **Setup & Pairing:** The app must allow setting the L1 (Office) and L2 (Home) coordinates via a simple map tap. P1 generates a QR code containing a unique session ID; P2 scans it to permanently pair the devices.
2. **Time-Filtered Trigger (P1):** The OS monitors the L1 geofence (200m). To prevent false alarms (e.g., lunch), the departure alert is suppressed unless the time is between 5:00 PM and 8:00 PM, or P1 stays outside the geofence for >20 minutes.
3. **Stage 1 - Departure Alert (P2):** When P1 officially exits L1, P2 receives a standard, single-chime push notification: *"P1 has left the office."* P1's phone checks its battery and appends it to the message: *(P1 Battery: 85%)*.
4. **Live Tracking (P1):** Upon exiting L1, P1 starts a Foreground Service sending live GPS coordinates to the cloud every 10 seconds.
5. **Stage 2 - Proximity Alarm (P2):** When P1 breaches a 500m geofence around L2 (Home), P2's phone triggers a loud, looping alarm chime.
6. **1-Tap Acknowledgment (P2 -> P1):** P2 must tap a massive, high-contrast button on their screen ("GATE UNLOCKED / GOT IT") to silence the alarm. This sends a silent push back to P1's screen displaying *"P2 acknowledged!"*
7. **Manual Override ("Honk Gate"):** P1's home screen features a massive 1x1 widget. Tapping it bypasses all logic and immediately sounds the Stage 2 alarm on P2's phone.
8. **Offline Fallback (P1 -> P2):** If P1 exits L1 or enters the 500m L2 perimeter and has no internet, P1 sends a structured SMS to P2 (e.g., `GATEKEEPER_CMD:ALARM`). P2's app intercepts this SMS and triggers the local alarm.
9. **Auto-Stop (P1 & P2):** When P1 enters the core L2 (Home) geofence (50m), all live tracking and background services must stop immediately to save battery.

## 5. Non-Functional Requirements
- **Battery:** P1 should consume < 5% extra battery during the workday. P2 should consume negligible battery while at home.
- **Latency:** Proximity alarms must trigger within 30 seconds of crossing the 500m boundary.
- **Reliability:** The alert must wake up P2's phone even if the screen is off (using High-Priority FCM or SMS).
- **UI/UX:** Buttons must be large. Text must be at least 18sp.

## 6. User Flows
- **The Commute:** 6:00 PM: P1 leaves L1 -> P1 app pings Firebase -> P2 gets a ding: "P1 has left" -> P1 app sends live GPS. 6:30 PM: P1 crosses 500m L2 geofence -> P2's phone rings loudly on loop -> P2 taps "Got It!" -> P1 gets a silent notification "P2 Acknowledged" -> P1 arrives at L2 -> Tracking stops automatically.
- **Low Network:** 6:30 PM: P1 crosses 500m L2 geofence -> Network request fails -> P1 app sends SMS `GATEKEEPER_CMD:ALARM` -> P2 app receives SMS -> P2 phone rings loudly on loop.

## 7. Permissions & OS Behavior
- **Location:** Requires `ACCESS_BACKGROUND_LOCATION` (P1 only) to detect geofence boundaries.
- **SMS:** Requires `SEND_SMS` (P1) and `RECEIVE_SMS` (P2) for the offline fallback.
- **Notifications:** Requires `POST_NOTIFICATIONS` (Android 13+) and `USE_FULL_SCREEN_INTENT` (for the looping alarm screen on P2).
- **OEM Battery Killers:** To survive Samsung/Xiaomi/Oppo aggressive battery management, users must manually: 
  1. Pin the app in the recent apps menu.
  2. Set Battery Optimization to "Unrestricted".
  3. Turn on "Auto-Start" in the OEM security app.

## 8. Data Model (Firestore)
**Collection: `sessions`**
- `status`: string ("active", "completed")
- `startTime`: timestamp
- `endTime`: timestamp
- `p1_battery`: number
- `p2_acknowledged`: boolean

**Collection: `locations` (Sub-collection of sessions)**
- `lat`: number
- `lng`: number
- `timestamp`: timestamp

## 9. Security & Privacy
- **Consent:** Both phones must explicitly agree to location sharing during setup.
- **Data Pruning:** A Firebase Cloud Function will run nightly to delete `locations` older than 3 days.

## 10. Risks and Mitigations
- **Risk:** Google Play blocks apps that use `SEND_SMS` without being the default SMS app. 
  - **Mitigation:** Distribute via direct APK side-loading.
- **Risk:** P2's phone goes into Deep Doze and ignores the Firebase FCM push.
  - **Mitigation:** Use FCM `priority: "high"` and pair it with a WakeLock in the Android code. If that fails, the SMS fallback acts as a secondary wake-up method.