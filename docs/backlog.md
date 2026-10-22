# Backlog

Requests collected along the way, with where they are planned.

| Request | Plan | Notes |
|---|---|---|
| Triple tap on the home screen to lock the phone | Stage 5 (gestures) | A normal app can only lock the screen through public APIs that the user enables once: an accessibility service (`GLOBAL_ACTION_LOCK_SCREEN`, Android 9+) or device admin (`lockNow`). Plan: an opt-in accessibility service with a clear explanation; if it is not enabled the gesture offers to set it up. Triple tap needs a short delay before single taps on empty space act. |
| Notification count badges | Stage 5, if wanted | Needs notification access (user grants it in Settings). |
| Icon shape for icons a pack does not cover, per-app icon override, dynamic clock icons | Stage 4 | |
| Real glass on the dock and over the wallpaper | Stage 5 | Launcher-managed wallpaper copy (normal apps cannot read the system wallpaper on Android 13). |
