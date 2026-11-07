# Problem & Feature Breakdown
**Project:** GateKeeper (Two-Phone Alert System)

## 1. Deeper Problem Breakdown
The core issue isn't simply "detecting movement"—it is **preventing a physical lockout with zero friction for two non-technical users (50+ years old)**. 

When analyzing the daily routine, several friction points and failure modes appear that a simple 100m tracker will fail to solve:

1. **The Asymmetry of Attention:**
   * **P1 (Commuter):** Focused on leaving work, traffic, and commuting. If an app requires manually tapping "Start Tracking" or "I am leaving," they will inevitably forget.
   * **P2 (Home):** Deep in focused work, household chores, or resting. A standard phone buzz or subtle notification chime is easily missed or dismissed subconsciously.
2. **Timing & Distance Constraints (The Commute Window):**
   * If the alert sounds when P1 is only 100 meters away from home, P2 doesn't have enough time to get up, find keys, walk to the gate, and unlock it before P1 is stuck outside waiting.
   * If P2 is alerted the exact moment P1 leaves the office, P2 might say, *"Okay, they just left, I have 30 minutes,"* go back to their activities, and **forget again** by the time P1 actually arrives.
3. **The "False Exit" Pitfall:**
   * P1 walking 100 meters to grab lunch near the office, or walking around inside a large office building, can trigger false alarms. This causes "alert fatigue," where P2 begins to ignore the notifications entirely.
4. **Hardware & Age-Related Barriers:**
   * Aggressive battery managers on modern phones (especially Xiaomi, Oppo, and Samsung) silently kill background tracking apps.
   * Small UI text, complex pairing codes, or managing GPS permissions manually will lead to abandonment of the app.

---

## 2. High-Impact Feature Upgrades
To make this system reliable, practical, and tailored to the users, we need to layer these specific features into the design:

### A. Two-Stage "Smart Warning" System 
*(Solves P2 Forgetting Twice)*
Instead of a single alert, use a two-phase alert model:
* **Stage 1 (Departure Info):** When P1 leaves L1 (Office), P2 receives a standard, quiet status notification: *"P1 has left the office."* This sets the expectation.
* **Stage 2 (Proximity Alert - Critical):** We set a **500m geofence around Home (L2)**. When P1 enters this perimeter (~3 minutes before reaching the gate), P2's phone sounds a distinct, persistent alarm: *"P1 is 3 minutes away! Please unlock the gate."*

### B. "Persistent Siren" with 1-Tap Acknowledgment
* Standard Android notifications make a single ding and disappear. For the Stage 2 gate alert, we will trigger an Android **Full Screen Intent** (similar to an incoming phone call).
* It will loop a loud chime until P2 taps a massive, high-contrast button: **"GATE UNLOCKED / GOT IT"**.
* Tapping this sends a silent confirmation back to P1's screen (*"P2 acknowledged"*), giving P1 peace of mind during the final stretch.

### C. Time-Window Filtering 
*(Suppresses False Alarms)*
* Since P1 primarily moves between 6:00 PM and 7:00 PM, the app stays in ultra-low-power idle mode during the day.
* If P1 walks 150m at 1:00 PM to a nearby cafeteria, the app suppresses departure alerts unless P1 is away for more than 20 minutes or outside working hours, preventing spurious alarms.

### D. "Honk Gate" Manual Override Widget
* If all automated triggers fail (e.g., a GPS glitch or dead battery zone), P1 has a single giant 1x1 widget on their Android home screen: **"HONK GATE"**.
* Tapping it bypasses all logic and sends an instant high-priority push (or fallback SMS) to sound the alarm on P2's phone immediately.

### E. Zero-Maintenance Auto-Pairing
* Instead of requiring logins or passwords, P1's app generates a single QR code during setup. P2 scans it once with their app, and the devices are permanently paired in the backend.

### F. Battery & Network Resilience Monitor
* If P1 leaves the office with under 15% battery, P2's initial alert includes a discreet note: *(P1 phone battery: 12%)*. This prevents P2 from panicking if P1's live tracking drops midway through the journey.
* **SMS Fallback:** If P1 loses internet on the commute, the app automatically fails over to sending a hidden SMS text message that P2's app intercepts to trigger the alarm.

---

## 3. Workflow Comparison

| Scenario | Basic 100m Tracker | Upgraded Context-Aware System |
| :--- | :--- | :--- |
| **P1 leaves desk for lunch** | Triggers false alarm to P2 | Ignored (filtered by time/distance logic) |
| **P1 leaves office for home** | Alerts P2 immediately *(P2 forgets 30 mins later)* | Stage 1 notification on departure + Stage 2 loud alert 500m from home |
| **P2 is cooking/in another room** | Standard notification ding *(easily missed)* | Persistent full-screen ringing until tapped "Got It" |
| **No cellular data on road** | Fails silently | Auto-switches to background SMS trigger |
| **Commute ends** | Requires manual stop or drains battery | Automatically shuts down all GPS tracking when inside L2 |