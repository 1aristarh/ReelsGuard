# ReelsGuard

Android app that limits Instagram Reels, YouTube Shorts and TikTok. It watches the screen with an
accessibility service, counts the time you spend on Reels/Shorts, and when time is up it swipes or
goes back out of Reels (Instagram: swipe left, so you stay in the app).

## Features
- **Setup first:** a guided setup (mode, limits, passes, permission, review) ends with a press-and-hold approval that locks your settings. The guard does nothing until you approve it.
- **Sessions:** scrolling budget per session (default 10 min per 1 hour). Scrolling: 5 min to 1 h, session length: 30 min to 24 h.
- **Block completely:** no sessions, Reels/Shorts are closed as soon as they open.
- **"I need to see something":** a few short passes per day (count and length are settable).
- **Throttle lever:** every number is set with a pop-up throttle lever. While the settings are locked it can't go up, only down.
- **Lock:** 1, 3, 7, 14 or 30 days. After it ends, going up is a request that waits 24 h, then you review and hold to apply.
- Settings sit behind the gear in the top right corner. The main screen shows only the timer and your daily total.

## Install
1. Download `ReelsGuard-1.1.0.apk` from the [latest release](../../releases/latest) and open it (allow installing from your browser/files app if asked).
2. Open ReelsGuard and tap **Guard is off** to open Accessibility settings, then enable **ReelsGuard**.
3. Android may grey the switch out for sideloaded apps: in Settings > Apps > ReelsGuard > menu (three dots) tap **Allow restricted settings**, then try again.

Requires Android 14+ (arm64). Wiping the app data or reinstalling clears the lock.

## Notes
- Detection relies on the apps' view IDs and the selected Reels/Shorts tab; an app update can break it.
- iOS isn't supported: iOS has no way for an app to watch another app's screen without Apple's paid Family Controls entitlement.
- Debug builds add extra short stops (1 min scrolling, 3 min session) for testing.

## Build
`gradle :app:assembleRelease` (the release build is signed with the debug key, fine for sideloading).
