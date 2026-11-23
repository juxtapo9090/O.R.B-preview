# 🔮 O.R.B. — Omnipresent Remote Bridge

> A mobile coordination surface for people and AI agents.

O.R.B. brings live agent conversations, file handoff, voice delivery, and trusted device context into one Android companion. It is built for a private, permissioned environment: the phone stays in the loop, while a team can coordinate work through a controlled network connection.

## Why O.R.B.

Modern agent work fragments quickly: chat happens in one place, files in another, voice somewhere else, and the mobile device is left outside the loop. O.R.B. makes the phone a first-class coordination surface.

```text
Android companion
  chat · attachments · voice · delivery state · optional device context
                         │
                         │ private network connection
                         ▼
O.R.B. coordination layer
  agent streams · message routing · file transfer · operational status
                         │
                         ▼
Human + AI team
  shared context · accountable handoff · fast mobile access
```

## What works today

- **Multi-agent chat** — separate live streaming sessions for a team of agents, with tool activity and code-change evidence rendered in the phone conversation.
- **House console** — a mobile front door for live internal services and their availability.
- **DeltaMesh file lanes** — staged attachments and direct file-transfer workflows between phone and trusted peers.
- **Voice delivery** — native queued playback for voice clips, with delivery state available to the coordination layer.
- **Device-aware support** — optional notification, screen, and accessibility-assisted context for operator-owned devices.
- **Private deployment** — phone and coordination services communicate on a private network rather than exposing the control surface to the public internet.

## Product in action

O.R.B. is not a static dashboard. It is a working surface where an operator can follow an agent's work, see its tool evidence, and keep the conversation close to the device.

## Architecture

The Android app is a Kotlin/Jetpack Compose project with an embedded Ktor service layer. Its core pieces are deliberately separated:

- **App surface:** Home, House, Chat, DeltaMesh, and Settings.
- **Agent stream client:** renders server-sent chat streams and tool messages in the mobile UI.
- **BackSocket:** an on-device service layer for health, status, notifications, voice, transfer receipt, and explicitly enabled device capabilities.
- **DeltaMesh:** chunked file-transfer receive/send paths with visible delivery state.
- **Private deployment:** intended for a private-network, operator-controlled environment.

See [docs/architecture.md](docs/architecture.md) and [docs/endpoints.md](docs/endpoints.md) for the component map and endpoint summary.

## Security and consent

O.R.B. is an engineering prototype for trusted, operator-owned devices. Some optional features require Android's explicit runtime grants, including overlay, notifications, accessibility, screen capture, camera, or storage access.

- Android permission gates remain user-controlled.
- The intended deployment topology is a private network; do not expose the phone service directly to the public internet.
- The project is not yet a consumer-ready remote-device-management product. Authentication, onboarding, auditing, and deployment hardening are active product work.

## Roadmap

1. Ship **O.R.B. Mira**, a normal-user companion limited to chat, attachments, delivery state, connection health, and optional voice.
2. Add authenticated multiplexed bridging and invitation-based onboarding.
3. Make audit trails and consent state visible in the product.
4. Package repeatable private deployments for small team pilots.

## Status

O.R.B. is an actively evolving technical prototype. This repository documents the Android companion; the broader coordination and Mira work are being developed alongside it.

## License

MIT
