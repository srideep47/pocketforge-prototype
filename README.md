# PocketForge

An on-device web development studio for Android. A local LLM writes the site, a local
Node runtime serves it, and a WebView previews it — all on the phone, with no network.

Prototype. arm64 only, `minSdk 29`, targets Android 15 (API 35).

## What it does

| | |
|---|---|
| **Local inference** | Alibaba MNN runs a quantised Qwen 3.5 checkpoint through a small JNI bridge, with a 32k context. Tokens come back as a `Flow<String>`. |
| **Chat + dictation** | Compose chat with Markdown rendering. The mic button transcribes into the input field with Android's `SpeechRecognizer` (offline where the device supports it) — no TTS, no playback, no always-on listening. |
| **Web dev environment** | Node 18 (nodejs-mobile) in an isolated `:node` process serving the project over HTTP on `localhost:5173`, with live reload on file change. |
| **Mini IDE** | A collapsible project tree with create, rename, delete, and a monospace editor. |
| **Autonomous agent** | A constrained raw-HTML artifact loop writes the site and starts the preview deterministically, with tolerant Hermes parsing retained as a fallback. |

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

## Models

Two are offered in-app, downloaded from HuggingFace on first use and cached on the
device afterwards:

| | Size | Notes |
|---|---|---|
| `taobao-mnn/Qwen3.5-2B-MNN` | ~1.4 GB | Recommended default: fastest iteration and ample room for the dev server. |
| `taobao-mnn/Qwen3.5-4B-MNN` | ~2.8 GB | Better code, fewer malformed tool calls. |

Downloads resume per file, so a dropped connection costs only the file in flight. Both
exports are multimodal and MNN refuses to load without the `visual.mnn` pair, so it is
fetched even though only the text path is used today.

The runtime config is written at load time — see `ModelConfig`. It is tuned for a coding
agent rather than for chat:

- `max_all_tokens = 10000`, `max_new_tokens = 4096`
- Qwen's validated mixed sampler: `temperature 1.0`, `topP 0.95`, `topK 20`,
  `minP 0`, repetition penalty `1.0`, and presence penalty `1.5`
- `use_mmap`, `reuse_kv`, `attention_mode = 8` (FlashAttention with fp16 KV cache)
- cached weight mmap and KV-cache mmap off for the dense 2B/4B models; this avoids the
  reload crashes and corrupt generation seen with MNN 3.6.1's dense static cache path
- thinking off: a `<think>` block before every tool call is a large latency tax at ~10 tok/s

Agent turns use MNN's role-aware chat overload and resend the full system/user/assistant/tool
history. The single-string overload is suitable for one-shot chat but loses the tool contract
between rounds and must not be used by the website agent. New sites use a raw
`<site>…</site>` envelope so Qwen 2B can emit HTML directly; requiring several kilobytes of HTML
to survive JSON escaping was the main source of malformed calls.

## Notes

- The dev server is a dependency-free static server. There is no npm on the phone, so the
  agent is told to write plain HTML/CSS/JS rather than reach for a framework or a CDN.
- Projects live in `files/projects/site`. Every path the model produces is resolved
  against that root and rejected if it escapes.
