# Dolphin AI

**Offline AI in your pocket** — a Kotlin/Jetpack Compose Android app that runs
large language models locally on your phone, with a ChatGPT/Gemini-class chat
UX, syntax-highlighted code blocks, conversation history, and a thermal guard
tuned for performance phones like the RedMagic 7S Pro.

> This is **Phase 1** of a multi-phase project — the foundation is in place,
> later phases add real on-device inference via llama.cpp, voice/vision,
> agent tool-use, an AI-girl avatar, and RedMagic-specific power tuning. See
> [the roadmap](#roadmap) below.

## Features (Phase 1)

- **Chat UI** — markdown rendering with bold/italic/inline-code/links, fenced
  code blocks with language label, syntax highlighting and one-tap copy
  button (Kotlin, Python, JS/TS, Java, Rust, shell), streaming token output.
- **Conversation history** — Room-backed, pin/rename/delete, persists across
  launches.
- **Model manager** — curated GGUF models (Phi-3 Mini, Qwen 2.5, Llama 3.2,
  Dolphin 8B, Gemma 2), download by URL, mark active, delete to reclaim
  storage.
- **Settings** — temperature / top-p / top-k / max tokens / threads / context
  size, custom system prompt, dynamic color (Material You), dark / light /
  system theming, English + Bangla strings.
- **Thermal guard** — reads battery temperature and auto-pauses generation
  above the configured threshold, resumes once cool. Token-rate cap also
  available for proactive heat avoidance.
- **JNI bridge** — `LlamaNative` interface ready for the Phase-2 llama.cpp
  swap-in. Phase 1 ships a deterministic stub native library so the entire
  pipeline (streaming, code-block rendering, copy, regenerate, history,
  thermal guard) can be exercised on any device.

## Build

Requirements: JDK 17, Android SDK with `platform-tools`, `platforms;android-34`,
`build-tools;34.0.0`, NDK `26.1.10909125`, CMake `3.22.1`.

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

The APK targets `arm64-v8a` only (the only ABI shipping on modern phones; keeps
APK size manageable). Min SDK 28, target SDK 34.

## Architecture

```
app/src/main/
├── cpp/                          ← native (CMake)
│   ├── CMakeLists.txt            ← USE_LLAMA_CPP toggles real inference
│   └── dolphinai_jni.cpp         ← JNI surface (stub now → llama.cpp later)
├── res/
│   ├── values/                   ← English strings, theme, icon, colors
│   └── values-bn/                ← Bangla strings
└── java/com/piashmsu/aichat/
    ├── App.kt                    ← Application + DI container
    ├── MainActivity.kt           ← single-activity Compose entrypoint
    ├── data/
    │   ├── AppContainer.kt       ← manual DI graph
    │   ├── db/                   ← Room: Conversation, Message, Memory
    │   ├── prefs/AppPrefs.kt     ← DataStore-backed settings
    │   └── download/             ← OkHttp model downloader + curated catalog
    ├── llm/
    │   ├── LlamaNative.kt        ← JNI bridge object (loads libdolphinai.so)
    │   └── LlmRuntime.kt         ← Engine interface + chat-prompt builder
    ├── memory/MemoryStore.kt     ← long-term memory (Phase 2 RAG-ready)
    ├── agent/                    ← tool-use scaffold + calculator/clipboard
    ├── voice/                    ← STT/TTS controller stub (Phase 2)
    ├── avatar/                   ← Live2D state machine stub (Phase 3)
    ├── thermal/                  ← battery-temperature thermal guard
    └── ui/
        ├── theme/                ← Material 3 + neon-cyan dark palette
        ├── components/           ← MarkdownText, CodeBlock, syntax-highlight
        ├── chat/                 ← chat screen + ViewModel + bubble + input
        ├── models/               ← model picker + downloader UI
        ├── settings/             ← inference, thermal, persona, appearance
        ├── history/              ← conversation list
        └── AppNav.kt             ← bottom-nav scaffold
```

### LLM engine swap

The chat layer talks to a `LlamaEngine` interface. Phase 1 ships
`LlamaCppEngine` wired to a stub native lib that returns deterministic streamed
output. To enable real llama.cpp inference:

1. Add the upstream as a git submodule:
   ```bash
   git submodule add https://github.com/ggerganov/llama.cpp app/src/main/cpp/llama.cpp
   ```
2. Toggle the CMake option in `app/build.gradle.kts`:
   ```kotlin
   externalNativeBuild { cmake { arguments += "-DUSE_LLAMA_CPP=ON" } }
   ```
3. Implement the four `nativeOpen` / `nativeClose` / `nativeCancel` /
   `nativeGenerate` bodies inside `dolphinai_jni.cpp` against
   `llama_model_load_from_file` / `llama_decode` / `llama_token_to_piece`.

The Kotlin side does not change.

## Roadmap

### Phase 2 — Memory, Voice, Vision
- Real llama.cpp integration (the JNI shim above)
- Long-term memory: fact extraction → ONNX-Runtime-Mobile embedding RAG
- Whisper.cpp for offline STT (push-to-talk + VAD)
- Sherpa-ONNX TTS (English + Bangla)
- Image input via LLaVA-Phi3 / MiniCPM-V multimodal GGUF

### Phase 3 — Agents, Skills, Avatar
- Function-calling agent loop with streaming tool execution
- Tool registry: web search, calendar, notes, file I/O, screen-capture-and-explain, app-launcher, Tasker bridge
- JSON skill descriptor format (drop-in plugins)
- Live2D Cubism AI-girl avatar with idle/talking/thinking states, lip-sync to TTS

### Phase 4 — RedMagic perf + polish
- RedMagic Game Space fan-control intent integration
- CPU affinity pinning to prime cores during inference
- Adaptive context: shrink KV cache as skin temp climbs
- Battery-aware mode: smaller model when unplugged
- Optional bundled Ollama Go daemon for the `ollama pull` UX (advanced toggle)
- Video understanding (frame sampling + VLM)

## License

Apache-2.0
