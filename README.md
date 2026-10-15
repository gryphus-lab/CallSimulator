# Call Simulator – Personal Android App

A simple, personal full-screen phone call simulator for Android.  
Simulate realistic incoming and outgoing calls with caller ID, ringtone, vibration, and an in-call UI.

**For personal use only on your own device.**

## Features

- **Incoming call screen**
  - Full-screen UI that can appear over the lock screen
  - Custom caller name & number
  - Default system ringtone + vibration pattern
  - Answer / Decline buttons
  - High-priority notification with full-screen intent

- **In-call screen**
  - Call duration timer
  - Mute / Speaker / Keypad controls (UI only)
  - End call button

- **Outgoing call simulation**
  - “Calling…” status then transitions to timer

- **Main setup screen**
  - Edit caller name and phone number before starting a simulation

## Requirements

- Android Studio Ladybug | 2024.2.1 or newer (or any recent version supporting AGP 8.7+)
- JDK 17
- Android device or emulator running **API 26+** (Android 8.0)

## How to open & run

1. Unzip / copy the `CallSimulator` folder.
2. Open **Android Studio** → **File → Open** → select the `CallSimulator` directory.
3. Let Gradle sync (it will download dependencies automatically).
4. Connect a device or start an emulator.
5. Click **Run** (green play button).

### First-run permissions

- On Android 13+ the app will request **Notifications** permission.
- For the best full-screen experience when the phone is locked:
  - Go to **Settings → Apps → Call Simulator → Notifications** and ensure the channel is allowed.
  - On some OEMs you may also need to allow **Display over other apps** or **Full screen notifications**.

## Project structure

```
CallSimulator/
├── app/
│   ├── build.gradle.kts          # App-level dependencies & config
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/callsimulator/
│       │   ├── MainActivity.kt
│       │   ├── IncomingCallActivity.kt
│       │   ├── InCallActivity.kt
│       │   └── CallSimulationService.kt
│       └── res/
│           ├── layout/
│           ├── drawable/
│           ├── values/
│           └── mipmap-*/
├── build.gradle.kts              # Project-level plugins
├── settings.gradle.kts
├── gradle.properties
└── gradle/wrapper/
```

## Dependencies (already declared)

- AndroidX Core KTX
- AppCompat
- Material Components 1.12
- ConstraintLayout
- Activity KTX
- Lifecycle Runtime KTX

Target SDK 35, min SDK 26, Kotlin 2.0.21, AGP 8.7.2.

## Customization ideas

- Replace the person avatar with a photo from the gallery.
- Add a list of saved contacts.
- Schedule a simulated call for a future time (AlarmManager / WorkManager).
- Change colors in `res/values/colors.xml`.

## Notes

- This app does **not** make real phone calls or use the telephony stack.
- It only simulates the visual/audio experience of an incoming/outgoing call.
- Ringtone uses the device’s default ringtone via `RingtoneManager`.
- The foreground service is used solely to deliver a high-priority call-style notification with a full-screen intent.

Enjoy testing your call UI!
