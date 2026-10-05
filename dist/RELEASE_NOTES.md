# 🧪 LastLab v2.0 — Relay-Native Android Client

**LastLab v2.0 is a ground-up rebuild of the app's backend.** The UI is the existing LastChat interface; everything behind it — the model catalog, chat streaming, and settings — now runs against the **Relay Gateway** (`@model-aggregator`) from a real in-app HTTP server, instead of the forked app's original data layer.

---

## 📦 Downloads

| Asset | Description |
|---|---|
| `lastlab.apk` | The Android app (standalone, no companion server needed) |
| `lastlab.apk.sha256` | SHA-256 checksum |

Verify before installing:

```bash
sha256sum -c lastlab.apk.sha256
```

---

## 🔑 First Launch — Add Your Gateway Key

**LastLab ships with no credentials.** The app talks only to your Relay Gateway, so you must supply your own key before the first message.

1. Open LastLab and go to **Settings**.
2. Open **Relay Gateway Key**.
3. Paste the key issued to you by the relay.
4. Tap **Save**. The key is stored on-device and displayed masked (`tes…890`) — only the last three characters are ever shown.

The key is held solely by the app's own key store. It is never mirrored to browser storage and never written to the web UI.

---

## 🚀 What Changed in v2.0

### Live model catalog, straight from the relay
The model list is fetched from the relay at runtime and served **exactly as returned** — no hardcoded model table. If the gateway is unreachable, the app says so and labels the list stale rather than quietly presenting old models as current.

Each model row shows its real context window (`128k ctx`, `400k ctx`) and output modalities (`TEXT -> TEXT`), taken from the relay rather than guessed.

### Real streaming, not a spinner
Replies stream token by token over a dedicated server-sent-events connection opened *before* generation starts. Reasoning traces arrive on their own channel and render as collapsible accordions. Token usage and throughput come from the relay's own accounting; anything the relay does not measure renders as a dash rather than a fabricated number.

### A backend that actually ships
The app embeds its own loopback HTTP server and serves the interface from it, so the whole stack — catalog, conversations, settings, streaming — runs on-device with no companion process.

### Fixed: the app could not start
v1.5 could render a chat page but was unusable in practice. Four shipping defects are fixed here:

- **Duplicate React instances.** The route manifest names its chunks relative to itself, so the browser requested them under a doubled `assets/assets/` path and loaded a second copy of React. Any interaction then crashed on a null dispatcher. Assets now resolve to a single canonical URL.
- **Missing display settings.** The client types `displaySetting` as required and several components read it unguarded, so a missing object crashed the chat page on first render. The server now always returns it, fully populated.
- **No assistant to select a model with.** The client disables every model control when there is no assistant, and with an empty list the picker could never open — leaving no way to choose a model at all. The app now ships with a default assistant.
- **Model selection silently discarded.** The model-change endpoint answered `200` while writing nothing, so a chosen model appeared to save and vanished on reload. It now persists, and reports an unknown assistant instead of failing quietly.

---

## 📲 Installation

Via ADB:

```bash
adb install -r lastlab.apk
```

On-device: download `lastlab.apk`, tap it, and confirm installation (allow installation from unknown sources if prompted). Requires **Android 7.0 (API 24)** or newer.

---

## ⚠️ Signing

This release is signed with the standard **Android debug key**. It is intended for evaluation and personal use, not for distribution through the Play Store — sideloaded builds from different developers will not update over one another. Re-sign it with your own key if you plan to maintain it long-term.

---

## 🧪 Verifying This Build

The repository ships the tooling used to validate it:

```bash
bash scripts/smoke/run_smoke.sh   # 63 end-to-end backend checks
bash scripts/verify_apk.sh        # APK contents and identity
```

The smoke suite runs the real server against a stub relay and covers catalog pass-through, streaming and reasoning channels, token accounting, gateway key masking, settings round-trips, and persistence across restart.

---

<sub>*Forked from <a href="https://github.com/Cocolalilal/LastChat">LastChat</a> / RikkaHub. Rebuilt around the Relay Gateway as a native Android client.*</sub>