# LocalMeta Ray-Ban AI — Android

**Version 1.5.0**

LocalMeta is a RayBan Meta smart-glasses AI assistant Android client. It is
the Android half of the [LocalMeta project](../README.md).

## Highlights

- 🕶️ **Meta Ray-Ban Display / Camera support** via the official Meta Wearables DAT SDK
- 🖥️ **Local AI server support** — use Ollama, llama.cpp, LM Studio, vLLM, or any
  OpenAI-compatible endpoint. No cloud account required, no data leaves your
  device. See [Local AI Server](../README.md#️-use-a-local-ai-server-ollama-llamacpp-lm-studio-vllm)
  in the root README for setup.
- 🌐 **OpenRouter support** — 500+ hosted models
- 🌏 **Alibaba DashScope** — Beijing / Singapore regions
- 🎙️ **Live AI mode**, Quick Vision, nutrition analysis, RTMP streaming,
  OpenClaw integration

## Build from source

### Prerequisites
- Android Studio (Hedgehog 2023.1.1 or newer) **or** the Android command-line tools
- JDK 17 or 21
- A [GitHub PAT](https://github.com/settings/tokens?type=beta) with
  `read:packages` scope (needed to fetch `com.meta.wearable:mwdat-core` from
  GitHub Packages)

### Steps

```bash
git clone https://github.com/Potowai/local-meta-rayban-ai.git
cd local-meta-rayban-ai/android

# 1. Point Gradle at your Android SDK (edit the path if yours is different)
echo "sdk.dir=C:\\Android" > local.properties      # Windows
# or: echo "sdk.dir=$HOME/Android/sdk" > local.properties  # macOS / Linux

# 2. Add your GitHub credentials so Gradle can fetch the Meta DAT SDK
#    Either edit local.properties:
#       github_username=YOUR_GITHUB_USER
#       github_token=ghp_xxxxx
#    Or export them as env vars:
#       export GITHUB_TOKEN=ghp_xxxxx

# 3. Build the debug APK
./gradlew :app:assembleDebug
```

Output APKs land in `app/build/outputs/apk/debug/`.

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
```

## Project layout

```
android/app/src/main/java/com/smartview/glassai/
├── LocalMetaApplication.kt
├── MainActivity.kt
├── managers/          # State holders (API providers, languages, modes)
├── services/          # Network, vision, TTS, streaming
├── viewmodels/        # Compose ViewModels
├── ui/
│   ├── components/    # Reusable Composables
│   ├── screens/       # One file per screen
│   ├── navigation/    # Nav graph
│   └── theme/         # Color, type, theme
├── data/              # Storage, prefs
└── utils/             # Helpers
```

## Documentation

- [Main README](../README.md)
- [Local AI Server setup](../README.md#️-use-a-local-ai-server-ollama-llamacpp-lm-studio-vllm)
- [API Key configuration](../README.md)

## License

See the root of the repository.
