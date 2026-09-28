// Minimal JNI bridge over MNN's transformer LLM runtime.
//
// Three entry points mirror the Kotlin side: initModel / generateStream / releaseModel.
// Generation is synchronous on the calling thread; tokens are pushed back through a
// std::streambuf that forwards every flush to a Kotlin callback, which lets the Kotlin
// wrapper turn the call into a cold Flow<String>.
#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <ostream>
#include <streambuf>
#include <string>

#include "llm/llm.hpp"

#define LOG_TAG "PocketForgeLlm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

using MNN::Transformer::Llm;
using MNN::Transformer::LlmContext;
using MNN::Transformer::LlmStatus;

// Holds the loaded model plus the cancellation flag shared with stopGeneration().
struct Session {
    std::unique_ptr<Llm> llm;
    std::mutex generateLock;
    std::atomic<bool> cancelled{false};
};

Session* asSession(jlong handle) {
    return reinterpret_cast<Session*>(handle);
}

// JNI's *StringUTF* functions speak Modified UTF-8, not UTF-8: a 4-byte sequence (any emoji)
// is invalid to them, and CheckJNI aborts the app on invalid input instead of returning null.
// MNN produces and consumes real UTF-8, so text crosses the boundary as UTF-16 instead.

constexpr char16_t kReplacement = 0xFFFD;

// Length of the longest prefix of [s] that does not end partway through a code point. A token
// boundary can split a multi-byte character; the tail waits for the next token to complete it.
size_t completeUtf8Prefix(const std::string& s) {
    const size_t n = s.size();
    for (size_t back = 1; back <= 4 && back <= n; ++back) {
        const auto c = static_cast<unsigned char>(s[n - back]);
        if ((c & 0xC0) == 0x80) continue;  // continuation byte: keep looking for the lead
        const size_t need = c >= 0xF0 ? 4 : c >= 0xE0 ? 3 : c >= 0xC0 ? 2 : 1;
        return need > back ? n - back : n;
    }
    return n;  // only continuation bytes: invalid, let the decoder replace them
}

std::u16string utf8ToUtf16(const char* s, size_t n) {
    std::u16string out;
    out.reserve(n);
    size_t i = 0;
    while (i < n) {
        const auto c = static_cast<unsigned char>(s[i]);
        size_t len = c < 0x80 ? 1 : (c & 0xE0) == 0xC0 ? 2 : (c & 0xF0) == 0xE0 ? 3 : (c & 0xF8) == 0xF0 ? 4 : 0;
        uint32_t cp = len == 1 ? c : len == 2 ? (c & 0x1F) : len == 3 ? (c & 0x0F) : (c & 0x07);
        bool valid = len != 0 && i + len <= n;
        for (size_t k = 1; valid && k < len; ++k) {
            const auto cc = static_cast<unsigned char>(s[i + k]);
            if ((cc & 0xC0) != 0x80) valid = false;
            cp = (cp << 6) | (cc & 0x3F);
        }
        if (!valid || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
            out.push_back(kReplacement);
            ++i;
            continue;
        }
        if (cp >= 0x10000) {
            cp -= 0x10000;
            out.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
            out.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
        } else {
            out.push_back(static_cast<char16_t>(cp));
        }
        i += len;
    }
    return out;
}

void appendUtf8(std::string& out, uint32_t cp) {
    if (cp < 0x80) {
        out.push_back(static_cast<char>(cp));
    } else if (cp < 0x800) {
        out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else if (cp < 0x10000) {
        out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else {
        out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    }
}

// Streams decoded text out of MNN and into the Kotlin callback. MNN flushes once per
// decoded token, so one flush == one emission on the Flow.
class CallbackStreamBuf : public std::streambuf {
public:
    CallbackStreamBuf(JNIEnv* env, jobject callback, jmethodID onToken)
        : mEnv(env), mCallback(callback), mOnToken(onToken) {}

protected:
    int overflow(int c) override {
        if (c != EOF) {
            mPending.push_back(static_cast<char>(c));
        }
        return c;
    }

    std::streamsize xsputn(const char* s, std::streamsize n) override {
        mPending.append(s, static_cast<size_t>(n));
        return n;
    }

    int sync() override {
        emitPending();
        return 0;
    }

private:
    void emitPending() {
        const size_t complete = completeUtf8Prefix(mPending);
        if (complete == 0) {
            return;
        }
        const std::u16string utf16 = utf8ToUtf16(mPending.data(), complete);
        mPending.erase(0, complete);
        jstring text = mEnv->NewString(reinterpret_cast<const jchar*>(utf16.data()),
                                       static_cast<jsize>(utf16.size()));
        if (text == nullptr) {
            mEnv->ExceptionClear();
            return;
        }
        mEnv->CallVoidMethod(mCallback, mOnToken, text);
        mEnv->DeleteLocalRef(text);
        if (mEnv->ExceptionCheck()) {
            mEnv->ExceptionDescribe();
            mEnv->ExceptionClear();
        }
    }

    JNIEnv* mEnv;
    jobject mCallback;
    jmethodID mOnToken;
    std::string mPending;
};

std::string toStdString(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const jsize length = env->GetStringLength(value);
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }
    std::string out;
    out.reserve(static_cast<size_t>(length));
    for (jsize i = 0; i < length; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < length && chars[i + 1] >= 0xDC00 && chars[i + 1] <= 0xDFFF) {
            cp = 0x10000 + ((cp - 0xD800) << 10) + (chars[i + 1] - 0xDC00);
            ++i;
        } else if (cp >= 0xD800 && cp <= 0xDFFF) {
            cp = kReplacement;  // unpaired surrogate
        }
        appendUtf8(out, cp);
    }
    env->ReleaseStringChars(value, chars);
    return out;
}

std::vector<std::string> toStringVector(JNIEnv* env, jobjectArray values) {
    std::vector<std::string> out;
    if (values == nullptr) {
        return out;
    }
    const jsize count = env->GetArrayLength(values);
    out.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(values, i));
        out.push_back(toStdString(env, value));
        if (value != nullptr) {
            env->DeleteLocalRef(value);
        }
    }
    return out;
}

bool streamResponse(
    JNIEnv* env,
    Session* session,
    jobject callback,
    const std::function<void(std::ostream*)>& respond) {
    if (session == nullptr || session->llm == nullptr || callback == nullptr) {
        return false;
    }

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(callbackClass);
    if (onToken == nullptr) {
        LOGE("callback is missing onToken(String)");
        return false;
    }

    std::lock_guard<std::mutex> lock(session->generateLock);
    session->cancelled.store(false);
    auto* context = const_cast<LlmContext*>(session->llm->getContext());
    context->status = LlmStatus::RUNNING;

    CallbackStreamBuf buffer(env, callback, onToken);
    std::ostream out(&buffer);
    respond(&out);
    out.flush();
    return !session->cancelled.load();
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_srideep_pocketforge_engine_mnn_MnnLlmBridge_nativeInitModel(
    JNIEnv* env, jobject, jstring jConfigPath) {
    const std::string configPath = toStdString(env, jConfigPath);
    LOGI("loading model config %s", configPath.c_str());

    std::unique_ptr<Llm> llm(Llm::createLLM(configPath));
    if (llm == nullptr) {
        LOGE("createLLM returned null for %s", configPath.c_str());
        return 0;
    }
    if (!llm->load()) {
        LOGE("model load failed for %s", configPath.c_str());
        return 0;
    }

    auto* session = new Session();
    session->llm = std::move(llm);
    LOGI("model ready");
    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_srideep_pocketforge_engine_mnn_MnnLlmBridge_nativeGenerateStream(
    JNIEnv* env, jobject, jlong handle, jstring jPrompt, jint maxNewTokens, jobject callback) {
    Session* session = asSession(handle);
    const std::string prompt = toStdString(env, jPrompt);
    return streamResponse(env, session, callback, [&](std::ostream* out) {
        session->llm->response(prompt, out, nullptr, maxNewTokens);
    }) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_srideep_pocketforge_engine_mnn_MnnLlmBridge_nativeGenerateChatStream(
    JNIEnv* env, jobject, jlong handle, jobjectArray jRoles, jobjectArray jContents,
    jint maxNewTokens, jobject callback) {
    Session* session = asSession(handle);
    const auto roles = toStringVector(env, jRoles);
    const auto contents = toStringVector(env, jContents);
    if (roles.size() != contents.size() || roles.empty()) {
        LOGE("invalid chat history: roles=%zu contents=%zu", roles.size(), contents.size());
        return JNI_FALSE;
    }

    MNN::Transformer::ChatMessages messages;
    messages.reserve(roles.size());
    for (size_t i = 0; i < roles.size(); ++i) {
        messages.emplace_back(roles[i], contents[i]);
    }
    return streamResponse(env, session, callback, [&](std::ostream* out) {
        session->llm->response(messages, out, nullptr, maxNewTokens);
    }) ? JNI_TRUE : JNI_FALSE;
}

// Continues a reply whose start is given: the conversation goes through the chat template as
// usual, [jAssistantPrefix] is appended verbatim after the assistant header, and decoding
// picks up from there. Used to close a reasoning block the model would not end on its own.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_srideep_pocketforge_engine_mnn_MnnLlmBridge_nativeContinueChatStream(
    JNIEnv* env, jobject, jlong handle, jobjectArray jRoles, jobjectArray jContents,
    jstring jAssistantPrefix, jint maxNewTokens, jobject callback) {
    Session* session = asSession(handle);
    if (session == nullptr || session->llm == nullptr) {
        return JNI_FALSE;
    }
    const auto roles = toStringVector(env, jRoles);
    const auto contents = toStringVector(env, jContents);
    if (roles.size() != contents.size() || roles.empty()) {
        LOGE("invalid chat history: roles=%zu contents=%zu", roles.size(), contents.size());
        return JNI_FALSE;
    }
    MNN::Transformer::ChatMessages messages;
    messages.reserve(roles.size());
    for (size_t i = 0; i < roles.size(); ++i) {
        messages.emplace_back(roles[i], contents[i]);
    }
    const std::string prompt =
        session->llm->apply_chat_template(messages) + toStdString(env, jAssistantPrefix);
    // The prompt is already templated; a second pass would wrap it as a new user message.
    session->llm->set_config("{\"use_template\":false}");
    const bool ok = streamResponse(env, session, callback, [&](std::ostream* out) {
        session->llm->response(prompt, out, nullptr, maxNewTokens);
    });
    session->llm->set_config("{\"use_template\":true}");
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_srideep_pocketforge_engine_mnn_MnnLlmBridge_nativeLastStats(
    JNIEnv* env, jobject, jlong handle) {
    Session* session = asSession(handle);
    jlongArray out = env->NewLongArray(5);
    if (out == nullptr) {
        return nullptr;
    }
    jlong values[5] = {0, 0, 0, 0, 0};
    if (session != nullptr && session->llm != nullptr) {
        // MNN keeps per-turn counters on the context; this is the only honest source
        // of throughput, since wall-clock in Kotlin also measures our own plumbing.
        const LlmContext* context = session->llm->getContext();
        values[0] = context->prompt_len;
        values[1] = context->gen_seq_len;
        values[2] = context->prefill_us;
        values[3] = context->decode_us;
        // Unlike the others this accumulates until the next reset(), not per response().
        values[4] = context->vision_us;
    }
    env->SetLongArrayRegion(out, 0, 5, values);
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_com_srideep_pocketforge_engine_mnn_MnnLlmBridge_nativeStopGeneration(
    JNIEnv*, jobject, jlong handle) {
    Session* session = asSession(handle);
    if (session == nullptr || session->llm == nullptr) {
        return;
    }
    // Llm::is_stop() treats USER_CANCEL as a stop token, so the decode loop unwinds on
    // its next step without tearing down the model.
    session->cancelled.store(true);
    const_cast<LlmContext*>(session->llm->getContext())->status = LlmStatus::USER_CANCEL;
}

extern "C" JNIEXPORT void JNICALL
Java_com_srideep_pocketforge_engine_mnn_MnnLlmBridge_nativeResetHistory(
    JNIEnv*, jobject, jlong handle) {
    Session* session = asSession(handle);
    if (session == nullptr || session->llm == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(session->generateLock);
    session->llm->reset();
}

extern "C" JNIEXPORT void JNICALL
Java_com_srideep_pocketforge_engine_mnn_MnnLlmBridge_nativeReleaseModel(
    JNIEnv*, jobject, jlong handle) {
    Session* session = asSession(handle);
    if (session == nullptr) {
        return;
    }
    {
        std::lock_guard<std::mutex> lock(session->generateLock);
        session->llm.reset();
    }
    delete session;
    LOGI("model released");
}
