# Architecture

`O.R.B.` is an Android-side bridge with two faces:

- a visible overlay that feels like an on-device AI companion
- a foreground service layer that exposes phone capabilities to higher-level agents

This preview keeps the most interesting boundaries.

## Core Shape

```text
Remote agent / higher-level backend
  |
  | messages, commands, context requests
  v
BackSocketService
  |
  | /notify /tts /status /context /logcat /screenshot
  |
  +--> ChatEventBus ------> Overlay chat panel
  +--> Android TTS
  +--> phone status queries
  +--> PhantomContext
  +--> LogcatBridge
  +--> MediaProjection capture
```

## Overlay Side

The overlay side is represented here by:

- `OverlayService.kt`
- `BubbleView.kt`
- `ChatPanel.kt`
- `ChatEventBus.kt`

That layer is responsible for:

- showing the draggable floating bubble
- expanding into a chat panel
- receiving injected system messages from background services
- keeping the UI feeling present even when the user is outside the main app activity

## Phone Context Side

The phone-context slice is represented by:

- `PhantomAccessibilityService.kt`
- `PhantomNotificationListener.kt`
- `PhantomContext.kt`

That layer is responsible for:

- tracking the current foreground app
- scraping visible screen text through accessibility events
- capturing the latest notification summary
- exposing that state through a shared in-memory context object

## Termux Lane

The Termux slice is represented by:

- `TermuxBridge.kt`
- `LocalTermuxSession.kt`
- `TermuxResultService.kt`

The interesting design choice here is boundary clarity:

- one lane is a one-shot local command path
- another lane is a passthrough to a user-managed provider
- the app does not pretend to fully own the external shell environment

That is a better story than silent automation magic. It keeps responsibility visible.

## Why This Preview Exists

The full private repo contains a lot of real operator setup and project-specific wiring. The preview strips that away and keeps the slices that best show:

- Android systems work
- service composition
- UI + service interplay
- command-boundary discipline
