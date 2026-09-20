// Minimal JNI bridge over MNN's transformer LLM runtime.
//
// Three entry points mirror the Kotlin side: initModel / generateStream / releaseModel.
// Generation is synchronous on the calling thread; tokens are pushed back through a
// std::streambuf that forwards every flush to a Kotlin callback, which lets the Kotlin
// wrapper turn the call into a cold Flow<String>.
#include <jni.h>
#include <android/log.h>

#include <atomic>
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
        if (mPending.empty()) {
            return;
        }
        jstring text = mEnv->NewStringUTF(mPending.c_str());
        mPending.clear();
        if (text == nullptr) {
            // Non-UTF8 fragment (a split multi-byte codepoint); drop it rather than throw.
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
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out(chars != nullptr ? chars : "");
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(value, chars);
    }
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

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_srideep_pocketforge_engine_mnn_MnnLlmBridge_nativeLastStats(
    JNIEnv* env, jobject, jlong handle) {
    Session* session = asSession(handle);
    jlongArray out = env->NewLongArray(4);
    if (out == nullptr) {
        return nullptr;
    }
    jlong values[4] = {0, 0, 0, 0};
    if (session != nullptr && session->llm != nullptr) {
        // MNN keeps per-turn counters on the context; this is the only honest source
        // of throughput, since wall-clock in Kotlin also measures our own plumbing.
        const LlmContext* context = session->llm->getContext();
        values[0] = context->prompt_len;
        values[1] = context->gen_seq_len;
        values[2] = context->prefill_us;
        values[3] = context->decode_us;
    }
    env->SetLongArrayRegion(out, 0, 4, values);
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
