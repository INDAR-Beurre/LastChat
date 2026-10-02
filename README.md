# LastChat Mobile Playground (Relay Edition)

<div align="center">
  <img src="docs/LastChat_icon.png" alt="LastChat Icon" width="128" height="128" />
  <br><br>
  <strong>High-Density AI Mobile Playground & Admin Console</strong><br>
  <em>Exclusively powered by <a href="https://relay-gw.pages.dev">@model-aggregator</a></em>
  <br><br>
</div>

**LastChat Mobile Playground** is a mobile-first AI playground application and native Android APK. Forked from LastChat and tailored for power-user and admin workflows, it strips away heavy third-party dependencies and connects directly to the **Relay Gateway** (`@model-aggregator`).

### 🚀 Key Highlights & Enhancements
- **Single Exclusive Provider (`@model-aggregator`)**: Fully integrated with the Relay Gateway (`https://relay-gw.pages.dev` / `https://relay-gateway.isisosiris107.workers.dev`), pooling 35+ upstream providers behind an OpenAI-compatible endpoint with automatic failover.
- **⚡ Admin Model Registry**: Tailored command-menu model picker displaying live provider badges (`workbuddy`, `yjs`, `tokenforge`, `bai`), probe health dots, modality tags, context lengths, and admin model overrides.
- **🎛️ Full Playground Hyperparameters**: Live sliders and controls for Temperature, Top-P, Max Output Tokens, Reasoning Effort (`None` / `Low` / `Medium` / `High`), Frequency & Presence Penalties, and SSE Streaming toggle.
- **🧠 First-Class Thinking & Reasoning**: Automatic detection and collapsible rendering of step-by-step thinking processes (DeepSeek-R1 / V4.1, Kimi-K3, etc.).
- **📊 Real-time Telemetry & Raw Inspector**: Live tracking of `x-relay-latency-ms`, token counts, upstream round-trip times, plus a full raw cURL / JSON request & response inspector.
- **📱 100% Lightweight APK Build (No Android Studio)**: Built with a headless CLI toolchain (`aapt2`, `javac`, `d8`, `uber-apk-signer`). Compiles and cryptographically signs a valid Android APK in **under 1 second** on low-spec hardware (~150MB peak RAM).

---

## 📦 Delivered Android APK

Pre-built release package ready for Android devices:
- **Location:** `dist/lastchat-playground.apk`
- **Checksum:** `dist/lastchat-playground.apk.sha256`
- **Package ID:** `me.rerere.lastchat.playground`
- **Supported Android Versions:** Android 7.0+ (API 24 to 34)

### Building the APK Locally (Lightweight)

No Android Studio or heavy Gradle daemons required:

```bash
./scripts/build_apk.sh
```

The build completes in ~0.5 seconds and outputs `dist/lastchat-playground.apk`.

---

## Original LastChat Overview


## Gallery

<table align="center" style="border: none;">
  <tr>
    <td align="center" style="border: none; width: 50%;">
      <img src="docs/1.4.7_chat.jpg" alt="Chat Interface" width="100%" />
    </td>
    <td align="center" style="border: none; width: 50%;">
      <img src="docs/1.4.3_stats.jpg" alt="Home Screen" width="100%" />
    </td>
  </tr>
  <tr>
    <td align="center" style="border: none; width: 50%;">
      <img src="docs/1.3.4_providers_use.gif" alt="providers page" width="100%" />
    </td>
    <td align="center" style="border: none; width: 50%;">
      <img src="docs/1.4.3_memory.jpg" alt="Memory Settings" width="100%" />
    </td>
  </tr>
</table>

## ✨ Key Features

### Advanced AI Capabilities
* **Multi-Provider Support**: Provider presets make it easier to get up and running. There's support for custom providers too!
* **RAG Memory**: Features a RAG-based memory system. Assistants can "remember" details from past conversations using embeddings.
* **Multi-Modal Inputs**: Interact using Text, Images, Video, and Audio.

### Tools & Integrations
* **Python**: Built-in Python Engine, powered by Workspaces/PRoot-based Linux environments.
* **JavaScript**: Built-in **JavaScript Engine** (QuickJS)
* **Web Search**: Integrated web search capabilities to fetch real-time information.
* **MCP**: Support for MCP servers.

### Assistant Management
* **Multiple Personas**: Create, manage, and switch between unlimited custom assistants.
* **Tagging System**: Organize assistants with custom tags.
* **Import/Export**: Easily share or backup your assistant configurations.

### Modern & Fluid UI
* **Material You**: The app was designed with Material You 3 Expressive in mind.
* **Rich Rendering**: Markdown support with LaTeX for math, code highlighting, and tables.

### Additional Modules
* **Image Generation**: Dedicated interface for generating images using supported models.
* **Text-to-Speech (TTS)**: Supports system TTS or other providers.

### Privacy & Data
* **Local-First**: Chat history and vector memory are stored locally on your device.
* **WebDAV Backup**: Securely sync and backup your data to any WebDAV-compatible server.

## Built With
* **Kotlin** & **Jetpack Compose**
* **Koin** for Dependency Injection
* **Room** & **DataStore** for persistence
* **WorkManager** & **AlarmManager** for reliable background tasks
* **QuickJS** for JavaScript integration

## Credits
* Original Project: [RikkaHub](https://github.com/re-ovo/RikkaHub)
* Image cropper is an edited version of the image editor found in [LavenderPhotos](https://github.com/kaii-lb/LavenderPhotos)
* Made with various **AI Agents**

##
*Note: This project is a fork and may contain modifications or features not present in the original RikkaHub repository.*
