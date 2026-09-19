// JNI shim around the embedded Node 18 runtime.
//
// node::Start() owns the calling thread until Node exits, so NodeRuntime calls
// nativeStart from a dedicated thread inside the :node process. Everything Node
// writes to stdout/stderr is pumped into logcat so a dev server's output is visible.
#include <jni.h>
#include <android/log.h>

#include <string>
#include <thread>
#include <unistd.h>
#include <vector>

#include "node.h"

#define LOG_TAG "PocketForgeNode"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

int g_pipe[2];
bool g_redirected = false;

void pumpToLogcat() {
    char buffer[2048];
    ssize_t read_bytes;
    while ((read_bytes = read(g_pipe[0], buffer, sizeof(buffer) - 1)) > 0) {
        if (buffer[read_bytes - 1] == '\n') {
            read_bytes--;
        }
        buffer[read_bytes] = '\0';
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%s", buffer);
    }
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_srideep_pocketforge_runtime_node_NodeRuntime_nativeRedirectOutput(JNIEnv*, jclass) {
    if (g_redirected) {
        return;
    }
    setvbuf(stdout, nullptr, _IONBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);
    if (pipe(g_pipe) != 0) {
        LOGE("could not create stdio pipe");
        return;
    }
    dup2(g_pipe[1], STDOUT_FILENO);
    dup2(g_pipe[1], STDERR_FILENO);
    std::thread(pumpToLogcat).detach();
    g_redirected = true;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_srideep_pocketforge_runtime_node_NodeRuntime_nativeStart(
    JNIEnv* env, jclass, jobjectArray jArgs) {
    const jint argc = env->GetArrayLength(jArgs);

    std::vector<std::string> storage;
    storage.reserve(static_cast<size_t>(argc));
    for (jint i = 0; i < argc; i++) {
        auto arg = reinterpret_cast<jstring>(env->GetObjectArrayElement(jArgs, i));
        const char* chars = env->GetStringUTFChars(arg, nullptr);
        storage.emplace_back(chars != nullptr ? chars : "");
        if (chars != nullptr) {
            env->ReleaseStringUTFChars(arg, chars);
        }
        env->DeleteLocalRef(arg);
    }

    std::vector<char*> argv;
    argv.reserve(storage.size() + 1);
    for (auto& value : storage) {
        argv.push_back(const_cast<char*>(value.c_str()));
    }
    argv.push_back(nullptr);

    LOGI("starting node with %d args", argc);
    const int exit_code = node::Start(argc, argv.data());
    LOGI("node exited with code %d", exit_code);
    return exit_code;
}
