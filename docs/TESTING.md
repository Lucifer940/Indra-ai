# Testing INDRA AI

## Automated

```bash
./gradlew test                  # everything (uses the committed Gradle 8.7 wrapper)
./gradlew :indra-core:test      # 92 tests in 11 classes, no network needed
./gradlew :indra-backend:test   # ApiIntegrationTest: 12 scenarios of real HTTP against a temp data directory
```

`LocalModeIntegrationTest` (backend) covers the configuration the phone uses: the engine starts on
127.0.0.1 on an OS-assigned port, the device session opens without an account, `/api/v1/chat/stream` emits
`event: token` / `event: done` over the new pure-Java HTTP server, memories and tasks survive an engine
restart, and the device-session route is absent when the server runs in normal mode.

`ApiIntegrationTest` starts the actual server on a free port with `INDRA_DATA_DIR` pointed at a temporary
folder, then walks through 11 ordered scenarios:

1. health and version endpoints
2. registration + login + token shape
3. unauthenticated requests are rejected (401)
4. chat falls back to the offline brain when no provider key exists
5. memory create → list → delete
6. tasks with recurrence roll-forward on completion
7. settings update and provider catalogue
8. API keys are masked, then reset
9. device pairing, sync and revoke
10. licence issue + activation
11. admin RBAC (403 for normal users) and logout revocation

The core suite covers the intent classifier, natural-time parsing, recurrence math, memory recall,
prompt building, personality/mode prompts, licence key format, JSON round-trips, security helpers,
sync merge logic and voice text normalisation.

**These tests run in CI on every push.** The `Core and backend tests` job in
`.github/workflows/android-build.yml` compiles and executes `:indra-core:test` and `:indra-backend:test`; the
run is only green when they pass, and the job publishes a `test-summary.txt` (plus JUnit HTML/XML reports in
the `test-reports` artifact) with the executed/failed/skipped counts.

They have not been executed on an Android device or a Windows machine from this repository's authoring
environment — the manual checklists below are still the way to verify device behaviour.

## Manual checklist — Android

1. Fresh install → splash → login. Register a second account and confirm both work.
2. Send "hello" → streamed answer appears; with no key the offline reply explains it.
3. Speak a question; confirm the mic indicator, transcript and spoken reply, then Stop mid-sentence.
4. Say "Remember that I prefer tea" → Memory screen shows it; delete it; confirm it disappears.
5. "Remind me tomorrow at 8 am to run" → confirmation → task appears, notification fires at the right time.
   Force-stop the app, reboot, confirm the alarm is restored.
6. Ask "what's my battery" → real percentage; "open WhatsApp" → confirmation → WhatsApp opens.
7. Settings → API keys → add a key (masked afterwards) → remove → reset.
8. Settings → Devices → pair; verify code on the other device; revoke and confirm sync fails.
9. Turn hands-free on: notification appears, saying "Indra" opens the app, turning it off stops the service.
10. Sign out → tokens gone (relaunch requires login), memories remain on the server for next sign-in.

## Manual checklist — Windows

1. Launch with the backend stopped → clear "offline" status, Start backend works.
2. Sign in, chat with streaming, confirm auto-speak and the character's mouth movement.
3. Voice → Listen: transcript appears and is sent; if the speech engine is missing, the message says so.
4. Files → search home, open a file, rename with confirmation, create a folder, delete with confirmation.
5. Dashboard → Lock PC asks first; battery and disk figures match Windows.
6. Settings → persona/brain/language saved and reflected in replies; API keys and pairing dialogs work.

## What "working" means here

Claims in this repository are limited to code that exists. Where a capability needs a third-party credential
(AI provider, search, weather, OAuth), the app reports that it is unconfigured instead of fabricating a
result. No test result, build success or APK is claimed by the author for this snapshot.
