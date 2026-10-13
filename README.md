# CoLink

Native Android client for **Hermes Agent** — the Android counterpart to [Hermes Conduit](https://github.com/kaishi00/hermes-conduit) (iOS).

Connect your phone to your self-hosted Hermes dashboard (port 9119): chat with streaming responses, get **"agent call" full-screen alerts** (native incoming-call style), and manage your agent from anywhere.

## Status

🚧 Early development — Phase 1 (connect + chat + call) in progress.

## Plan

- **Phase 1** — MVP: connect to dashboard, chat with streaming, full-screen "agent call" screen
- **Phase 2** — push notifications (FCM relay) + voice input
- **Phase 3** — Kanban, multi-dashboard, model switching

## Stack

- Kotlin / Jetpack Compose
- Hermes dashboard API (REST + WebSocket, port 9119)

## Related

- [Hermes Agent](https://github.com/NousResearch/hermes-agent)
- [Hermes Conduit (iOS)](https://github.com/kaishi00/hermes-conduit)
