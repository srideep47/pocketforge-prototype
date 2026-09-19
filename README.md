# PocketForge

An on-device web development studio for Android. A local LLM writes the site, a local
Node runtime serves it, and a WebView previews it — all on the phone, with no network.

Prototype. arm64 only, `minSdk 29`, targets Android 15 (API 35).

## What it does

| | |
|---|---|
| **Local inference** | Alibaba MNN runs a quantised Qwen 2.5/3.x checkpoint through a small JNI bridge. Tokens come back as a `Flow<String>`. |
| **Chat + dictation** | Compose chat with Markdown rendering. The mic button transcribes into the input field with Android's `SpeechRecognizer` (offline where the device supports it) — no TTS, no playback, no always-on listening. |
| **Web dev environment** | Node 18 (nodejs-mobile) in an isolated `:node` process serving the project over HTTP on `localhost:5173`, with live reload on file change. |
| **Mini IDE** | A drawer listing every project file, a monospace editor, and create/read/update/delete. |
| **Autonomous agent** | A Hermes-format tool-calling loop: the model creates files, edits them, starts the dev server, and the preview tab follows along. |

Tools the agent can call: `create_file`, `edit_file`, `read_file`, `list_files`,
`start_dev_server`, `stop_dev_server`.

## Layout

```
app/                     Compose UI, ViewModel, agent loop, workspace, dictation
engine/mnn/              MNN JNI bridge (llm_jni.cpp) + Kotlin streaming wrapper
runtime/node/            nodejs-mobile host, :node service, dev server script
```

Three processes' worth of separation matters here: the model holds hundreds of megabytes
of mmap'd weights in `:main`, and `node::Start` never returns and takes its process down
with it when V8 faults. Keeping Node in `:node` means a broken dev server costs a restart
of the server, not of the loaded model.

## Building

```bash
./gradlew :app:assembleDebug
```

Requirements:

- JDK 17, Android SDK 35, NDK 27+, CMake 3.22.1
- `mnnSourceRoot` in `gradle.properties` pointing at an MNN checkout — headers only
  (`include/`, `transformers/llm/engine/include/`); the matching `libMNN.so` is vendored.

### Vendored binaries

Three prebuilt artifacts are checked in under the modules that consume them:

- `engine/mnn/src/main/jniLibs/arm64-v8a/libMNN.so` — MNN 3.6.1 with the LLM runtime
- `runtime/node/src/main/jniLibs/arm64-v8a/libnode.so` — nodejs-mobile 18.20.4
- `runtime/node/src/main/cpp/include/node/` — the matching embedding headers

They must stay in step with each other; `libMNN.so` and the `mnnSourceRoot` headers in
particular are one unit.

## Installing a model

Push an MNN-exported model into the app's external files directory, one directory per
model:

```bash
adb push Qwen2.5-1.5B-Instruct-MNN /sdcard/Android/data/com.srideep.pocketforge/files/models/
```

A directory is offered in the model menu when it contains `llm.mnn`. The runtime config is
written at load time (`use_mmap`, `reuse_kv`, `attention_mode = 10` for the INT8 QKV
cache) — see `ModelConfig`.

## Notes

- The dev server is a dependency-free static server. There is no npm on the phone, so the
  agent is told to write plain HTML/CSS/JS rather than reach for a framework or a CDN.
- Projects live in `files/projects/site`. Every path the model produces is resolved
  against that root and rejected if it escapes.
