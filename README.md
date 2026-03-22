# O.R.B-preview

Selected implementation slices from the private `O.R.B.` Android project.

`O.R.B.` stands for `Omnipresent Remote Bridge`: an Android overlay app that acts as both a floating AI chat surface and a phone-side bridge for remote automation features such as notifications, text-to-speech, device context, and controlled local command lanes.

This preview is intentionally partial. It is meant to show the architecture and implementation style, not to publish the full operational repo or deployment workflow.

It is not the complete Android Studio project and is not framed as a one-command build/install repo.

## What This Shows

- a foreground Android service that exposes a phone-side HTTP control surface
- a floating overlay bubble and expandable chat panel built with Compose
- context capture through Accessibility and Notification Listener services
- a Termux command lane with explicit boundary handling
- selected source files from the real app, kept in their original package structure

## What This Does Not Include

- the full private repo
- real operator topology, internal hostnames, or private IPs
- backend deployment wiring or production streamer setup
- every screen, setting, or integration from the live app

## Repo Layout

- `app-slices/`
  - selected Android source files copied from the real project
- `docs/architecture.md`
  - high-level system shape and feature boundaries
- `docs/endpoints.md`
  - public-safe summary of the phone-side HTTP surface
- `assets/ic_orb.png`
  - icon asset used by the overlay

## Included Source Slices

- `service/BackSocketService.kt`
- `service/OverlayService.kt`
- `service/PhantomAccessibilityService.kt`
- `service/PhantomNotificationListener.kt`
- `service/PhantomContext.kt`
- `service/LogcatBridge.kt`
- `ui/overlay/BubbleView.kt`
- `ui/overlay/ChatPanel.kt`
- `ui/overlay/ChatEventBus.kt`
- `termux/TermuxBridge.kt`
- `termux/LocalTermuxSession.kt`
- `termux/TermuxResultService.kt`
- `AndroidManifest.xml`

## Framing

This is portfolio code: real implementation, deliberately curated.

The point is to show:

- Android service architecture
- overlay UI mechanics
- device-context capture patterns
- safe-ish command boundary design on mobile
