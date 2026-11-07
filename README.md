# GateKeeper

A two-phone Android alert app that stops people getting locked out at the gate. I built it for my parents: one commutes, the other is at home, and a normal notification was easy to miss.

## How it works

1. **Departure notice:** when the commuter leaves work, the phone at home gets a quiet notification, including the commuter's battery level.
2. **Proximity alarm:** within 500 m of home, about 3 minutes away, the phone at home sounds a looping full-screen alarm.
3. **Confirmation:** the alarm keeps going until someone taps to acknowledge it, and the commuter is told.

Live tracking runs only during the commute and stops on arrival home.

## Features

- Geofence triggers, with no manual tracking
- A time window that filters out false alarms from short walks during the day
- SMS fallback when there is no internet
- A "Honk Gate" home-screen widget to sound the alarm manually
- QR code pairing, with no logins or passwords

## Tech stack

Kotlin, Android (minSdk 26), Firebase Firestore and Cloud Messaging, Google Play Services Location and Maps, ZXing

## Setup

This repo leaves out private config, so you need to add your own:

1. Create a Firebase project and put its `google-services.json` in `app/`.
2. Replace `YOUR_GOOGLE_MAPS_API_KEY_HERE` in `app/src/main/AndroidManifest.xml` with your Maps API key.
3. Open the project in Android Studio and build. The Gradle wrapper scripts aren't included, so Android Studio or a local Gradle install is needed.

The app is side-loaded instead of published on Google Play, because of Play's restrictions on SMS permissions.

## Known gaps

- Internet alerts rely on a Firebase Cloud Function that turns Firestore writes into push notifications. It isn't in this repo.
- Three steps are still marked `TODO` in the code: showing a departure notice received by SMS, showing an acknowledgment received by SMS, and saving the device's push token to Firestore.

## Docs

- [Problem breakdown](problem.md)
- [Product requirements](prd.md)
- [Build plan](plan.md)
