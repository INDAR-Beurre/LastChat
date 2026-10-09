# 🧪 LastLab v1.0.0 — Official Version 1 Release

**LastLab v1.0.0 is the official release of the Relay-Native Android Client & AI Playground.** The UI provides a high-density, responsive AI interface; everything behind it — live model cataloging with dynamic refreshing and resilient fallbacks, streaming chat with reasoning traces, complete conversation branching, and full settings persistence — runs against the **Relay Gateway** (`@model-aggregator`) from an embedded in-app loopback HTTP server.

---

## 📦 Downloads

| Asset | Description |
|---|---|
| `lastlab.apk` | The Android app (`1.2 MB` lightweight build, standalone) |
| `lastlab.apk.sha256` | SHA-256 checksum (`fa62920c...`) |

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

## 🚀 Features in Version 1.0.0 Release

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

### ⚡ Ultra-Lightweight Footprint (1.2 MB APK, ~22MB RAM)
- **40% APK Size Reduction**: Slashed install binary from `2.0 MB` to `1.2 MB` (`1.18 MiB`).
- **Font Deduplication**: Removed 50 redundant `.ttf` and `.woff` KaTeX font files, retaining standard `.woff2` (native in Android 7+ WebView) with transparent in-app server fallback.
- **Bytecode Stripping & D8 Release Mode**: DEX compilation configured with `--release --min-api 24`, stripping line tables and debug metadata.
- **Resource Pruning**: Purged unused heavy marketing PNGs and unreferenced assets.
- **Runtime RAM & CPU Management**: Integrated Android `onTrimMemory` and `onLowMemory` cache clearing, plus `onPause`/`onResume` JavaScript timer pausing to eliminate background battery drain.

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
bash scripts/smoke/run_smoke.sh   # 218 checks (57 smoke + 6 restart + 155 config iterations)
bash scripts/verify_apk.sh        # APK contents and identity
```

The test suite runs the real server against a stub relay and covers catalog pass-through, streaming and reasoning channels, token accounting, gateway key masking, settings round-trips, persistence across restart, and 155 distinct configuration iterations across every endpoint and edge case.

---

<sub>*Forked from <a href="https://github.com/Cocolalilal/LastChat">LastChat</a> / RikkaHub. Rebuilt around the Relay Gateway as a native Android client.*</sub>