# Public Endpoint Summary

The full private project exposes a phone-side HTTP surface through `BackSocketService`.

This preview keeps the implementation slice but rewrites the endpoint story without private network details.

## Endpoints

- `GET /health`
  - liveness check for the phone-side bridge
- `POST /notify`
  - inject a message into the overlay and raise a system notification
- `POST /tts`
  - speak text using Android text-to-speech
- `GET /status`
  - return phone state such as battery/network/screen status
- `GET /context`
  - return the current phone-awareness snapshot
- `POST /logcat`
  - return filtered logcat output for debugging
- `GET /screenshot`
  - return a captured screen image when MediaProjection has been granted

## Context Payload Shape

The implementation slice shows a shared context object with fields for:

- foreground app
- screen text
- last notification
- last notification app

## Design Notes

- `notify` is more than a toast; it can feed both the overlay chat and a visible Android notification lane
- `context` depends on separate Android capabilities working together rather than one giant service doing everything
- `logcat` is useful for debugging but still needs explicit platform permissions and practical output capping
- `screenshot` is intentionally permission-gated through MediaProjection instead of pretending screen access is always available
