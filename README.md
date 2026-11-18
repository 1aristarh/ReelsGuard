# ReelsGuard

Android app that limits Instagram Reels, YouTube Shorts and TikTok. It watches the screen with an
accessibility service, counts the time you spend on Reels/Shorts, and when time is up it swipes or
goes back out of Reels (Instagram: swipe left, so you stay in the app).

## Features
- **Sessions:** scrolling budget per session (default 10 min per 1 hour). Budget: 5/10/15/20/30 min, session length: 30 min/1/2/3 h.
- **Block completely:** no sessions, Reels/Shorts are closed as soon as they open.
- **"I need to see something":** a few short passes per day (count and length are settable).
- Daily total of time spent on Reels.

## Install
1. Download `ReelsGuard-1.0.0.apk` from the [latest release](../../releases/latest) and open it (allow installing from your browser/files app if asked).
2. Open ReelsGuard and tap **Guard is off** to open Accessibility settings, then enable **ReelsGuard**.
3. Android may grey the switch out for sideloaded apps: in Settings > Apps > ReelsGuard > menu (three dots) tap **Allow restricted settings**, then try again.

Requires Android 14+ (arm64). Settings are locked while a cooldown is running.

## Notes
- Detection relies on the apps' view IDs and the selected Reels/Shorts tab; an app update can break it.
- iOS isn't supported: iOS has no way for an app to watch another app's screen without Apple's paid Family Controls entitlement.
- Debug builds use short timings (30 s per 2 min) for testing.

## Build
`gradle :app:assembleRelease` (the release build is signed with the debug key, fine for sideloading).
