# LocalMeta

> A RayBan Meta smart-glasses AI assistant that runs **on your own machine**.

LocalMeta turns your Meta glasses into a multimodal AI companion. Snap a photo,
ask a question, dictate a note — the app captures the moment and routes it to
an AI backend for an answer. The point of this fork: **the backend can be your
own server**, not a third-party cloud.

<div align="center">

[<img src="./rayban.png" width="64" alt="LocalMeta logo"/>](./rayban.png)

**Your glasses. Your AI. Your network.**

</div>

---

## Why this fork?

The original app is locked to hosted APIs (Alibaba Dashscope, OpenRouter,
Google Gemini). This fork keeps all of that but adds a **Local Server**
provider, so the app can talk to anything that speaks the OpenAI
`/v1/chat/completions` protocol:

- 🦙 [Ollama](https://ollama.com) — `ollama serve` + `ollama pull llava`
- 🦙 [llama.cpp server](https://github.com/ggerganov/llama.cpp) — local GGUF models
- 🧪 [LM Studio](https://lmstudio.ai) — GUI for GGUF models
- ⚡ [vLLM](https://docs.vllm.ai) — high-throughput inference
- 🧠 [LocalAI](https://localai.org) — drop-in OpenAI replacement

Your photos, voice, and conversations never leave the device running the
server. No accounts, no API keys, no per-image charges.

> **Note:** The iOS source is kept in the repo for reference but is not
> maintained in this fork. The active platform is **Android**.

---

## What's in the box

- 📷 **Quick Vision** — point, tap, get a description. Six specialist modes
  (standard, health, blind-assist, reading, translation, encyclopedia).
- 🎙️ **Live AI** — push-to-talk voice chat with a vision-capable model.
- 🍎 **Nutrition analysis (LeanEat)** — point at food, get calories, macros,
  additives, and health notes.
- 📡 **OpenClaw integration** — bridge to the OpenClaw agent platform.
- 🎬 **RTMP streaming** — live stream the glasses feed to any RTMP endpoint.
- 🖼️ **Records** — every Quick Vision capture is saved with its AI response
  for later review.

---

## Local AI server — quick start

The fastest way to try this on your phone:

```bash
# 1. Install Ollama (or llama.cpp, LM Studio, anything OpenAI-compatible)
curl -fsSL https://ollama.com/install.sh | sh

# 2. Pull a vision-capable model
ollama pull llava

# 3. Start the server (default: http://localhost:11434)
ollama serve
```

In the app:

1. **Settings → Vision API Provider → Local Server (OpenAI-compatible)**
2. Pick **Ollama** from the preset list (URL and model auto-fill)
3. Tap **Test connection** — you should see `llava` listed under "Models"
4. Save
5. Go take a photo with Quick Vision

That's it. No account, no API key.

### Other connection modes

- **Termux on the same phone** — `ollama serve` inside Termux, then use
  `http://127.0.0.1:11434/v1` in the app.
- **Phone on the same Wi-Fi as your dev machine** — use your dev machine's LAN
  IP, e.g. `http://192.168.1.42:11434/v1`. Cleartext HTTP is allowed to
  private LAN ranges by default.
- **Android emulator** — `http://10.0.2.2:11434/v1` reaches the host machine.

### Picking a model

The OpenAI-compatible endpoint requires the model to be on the server. Use any
vision-capable model (`llava`, `llava-llama3`, `moondream`, `qwen2-vl`,
`minicpm-v`, etc.) for Quick Vision / LeanEat. For Live AI you'll want a
chat-tuned model in addition to a vision one.

If you don't enter a model name, the app will list everything your server
reports on first test, and you can pick from the dropdown.

---

## Cloud providers (still supported)

If you don't want to run a server, the original providers still work. Pick
one in **Settings → Vision API Provider**:

- **Alibaba DashScope** — Beijing (China) or Singapore (international) regions.
  Free tier available. Best for Chinese-language responses.
- **OpenRouter** — 500+ hosted models, pay-as-you-go. Includes GPT-4o, Claude,
  Gemini, Llama, Qwen, etc.
- **Google Gemini Live** — real-time voice chat (Android only).
- **Local Server** — your own machine, as above.

API keys are stored in encrypted preferences (`EncryptedSharedPreferences`),
never logged.

---

## Build the Android app

```bash
git clone https://github.com/Potowai/local-meta-rayban-ai.git
cd local-meta-rayban-ai/android
```

### Prerequisites

- **Android SDK 35** + **build-tools 34.0.0** + **platform-tools**
- **JDK 17 or 21**
- A **GitHub PAT** with `read:packages` scope (to fetch
  `com.meta.wearable:mwdat-core` from GitHub Packages)

### Configure

```bash
# Point Gradle at your SDK
echo "sdk.dir=C:\\Android" > local.properties         # Windows
# echo "sdk.dir=$HOME/Android/sdk" > local.properties # macOS / Linux

# Auth for the Meta DAT SDK
echo "github_username=YOUR_GITHUB_USER" >> local.properties
echo "github_token=ghp_xxxxx" >> local.properties
# or: export GITHUB_TOKEN=*** build

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/`
- `app-universal-debug.apk` — all ABIs (115 MB)
- `app-arm64-v8a-debug.apk` — modern phones (86 MB)
- `app-armeabi-v7a-debug.apk` — older phones (83 MB)

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
```

On first launch, grant the requested permissions (Bluetooth, microphone,
camera). Pair the glasses through the Meta View app, then connect them in
LocalMeta.

> The debug APK is signed with the Android debug keystore. That's fine for
> testing but **not for the Play Store** — for that, swap in your own release
> keystore in `app/build.gradle.kts`.

---

## Downloads

Pre-built debug APKs are attached to the [Releases](../../releases) page.

| Version | Date | Highlights |
|---|---|---|
| **v1.5.1** | 2026-06-09 | Local AI server (this fork) + rebrand to LocalMeta |
| v1.4.0 | earlier | OpenRouter, multi-region Alibaba, Gemini Live |

---

## Project layout

```
local-meta-rayban-ai/
├── android/             # ← active platform
│   ├── app/
│   │   └── src/main/
│   │       ├── java/com/smartview/glassai/
│   │       │   ├── managers/    # APIProviderManager, mode managers
│   │       │   ├── services/    # VisionAPI, OmniRealtime, TTS, RTMP
│   │       │   ├── viewmodels/  # Compose ViewModels
│   │       │   ├── ui/          # screens, components, theme
│   │       │   ├── data/        # ConversationStorage, QuickVisionStorage
│   │       │   └── utils/       # APIKeyManager
│   │       └── res/
│   └── build.gradle.kts
├── CameraAccess/        # iOS source (unchanged in this fork)
├── CameraAccess.xcodeproj
├── LICENSE
└── README.md
```

The Android package id is `com.smartview.glassai` and the Java root is
`com.smartview.glassai.*`. Search the codebase starting from
`managers/APIProviderManager.kt` if you want to understand the provider
plumbing.

---

## How the Local Server provider works

`APIProvider.CUSTOM` is a third value alongside `ALIBABA` and `OPENROUTER` in
the `APIProvider` enum. When selected, the app:

1. Reads the user-configured **base URL**, **model name**, and optional
   **API key** from `SharedPreferences`.
2. Builds requests against `<baseUrl>/chat/completions` with the standard
   OpenAI JSON shape.
3. Adds `Authorization: Bearer <key>` **only if** a key is configured — most
   local servers don't need one.
4. Saves images to the camera roll under `Pictures/LocalMeta/`.
5. On the "Test connection" button, calls `<baseUrl>/models` and parses
   the response to populate the model dropdown.

The presets just prefill sensible defaults — Ollama on `:11434`, llama.cpp
server on `:8080`, LM Studio on `:1234`, vLLM on `:8000`. You can override
both URL and model per server.

Cleartext HTTP to public domains is **disabled**. Cleartext is only allowed
to:
- `localhost`, `127.0.0.1`
- `10.0.2.2` (Android emulator → host)
- Common private LAN ranges (`192.168.x.x`, `10.0.0.0/8`, `172.16.0.0/12`)

If your server is on a non-standard network, edit
`android/app/src/main/res/xml/network_security_config.xml`.

---

## Privacy

- All AI providers, including the local one, run **as configured**. Local
  servers keep everything on your network. Cloud providers (Alibaba,
  OpenRouter, Gemini) send data to those services under their respective
  terms.
- API keys are stored in `EncryptedSharedPreferences` (AES-256 GCM).
- The app does not phone home, no analytics, no crash reporting. Check the
  source if you want proof.
- Photos and conversation history are stored on-device. Clearing app data
  wipes them.

---

## License

MIT — see [LICENSE](./LICENSE). Use it, fork it, sell your own version.

The original project (CameraAccess, original iOS app) is by
[Turbo1123](https://github.com/Turbo1123). This fork is by
[Potowai](https://github.com/Potowai).

The Meta Wearables DAT SDK is governed by the
[Meta Wearables Developer Center](https://wearables.developer.meta.com/) terms.

---

## Contributing

Issues and pull requests welcome on the
[GitHub repo](https://github.com/Potowai/local-meta-rayban-ai). For new
providers, follow the pattern in `APIProviderManager.kt` — add a value to
the enum, branch in `VisionAPIService.kt`, add a Settings section in
`SettingsViewModel.kt`.
