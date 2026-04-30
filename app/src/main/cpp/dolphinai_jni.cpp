// JNI bridge for Dolphin AI.
//
// In Phase 1 this file ships a *stub* implementation that lets the app build
// and exercise the entire UI/streaming pipeline without depending on a native
// LLM. It produces tokens at a configurable rate and echoes a deterministic
// canned response so the chat surface, code-block parser, copy/regenerate,
// thermal guard and persistence layers can all be validated end-to-end on any
// emulator or physical device.
//
// In Phase 2 the project will add llama.cpp as a git submodule under
// app/src/main/cpp/llama.cpp and toggle the CMake option `USE_LLAMA_CPP=ON`.
// At that point the native methods below become thin wrappers around
// `llama_init_from_file`, `llama_decode`, `llama_token_to_piece`, etc. The
// Kotlin-side LlamaEngine interface is identical between the two
// implementations, so the rest of the app does not change.

#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <chrono>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#define LOG_TAG "DolphinJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct Session {
    std::string model_path;
    int n_ctx = 2048;
    int n_threads = 4;
    std::atomic<bool> cancel{false};
    std::mutex mu;
};

std::mutex g_sessions_mu;
std::vector<std::unique_ptr<Session>> g_sessions;

Session* get_session(jlong handle) {
    if (handle <= 0) return nullptr;
    std::lock_guard<std::mutex> lk(g_sessions_mu);
    auto idx = static_cast<size_t>(handle - 1);
    if (idx >= g_sessions.size()) return nullptr;
    return g_sessions[idx].get();
}

std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

// Phase-1 stub: produce a deterministic, structured response that exercises
// markdown + code blocks so the UI rendering path can be tested without a
// real LLM.
std::vector<std::string> compose_stub_reply(const std::string& prompt) {
    std::string lower;
    lower.reserve(prompt.size());
    for (char c : prompt) lower.push_back(static_cast<char>(tolower(c)));

    std::string body;
    if (lower.find("code") != std::string::npos ||
        lower.find("kotlin") != std::string::npos ||
        lower.find("python") != std::string::npos) {
        body =
            "Sure — here is a small Kotlin example showing how the engine streams "
            "tokens back to the UI:\n\n"
            "```kotlin\n"
            "engine.generate(prompt, settings).collect { chunk ->\n"
            "    state.update { it.append(chunk) }\n"
            "}\n"
            "```\n\n"
            "And the equivalent in Python:\n\n"
            "```python\n"
            "for chunk in engine.generate(prompt, settings):\n"
            "    print(chunk, end=\"\", flush=True)\n"
            "```\n\n"
            "_Note: this is the Phase-1 stub engine. Drop in llama.cpp via the "
            "Phase-2 submodule for real inference._";
    } else if (lower.find("hello") != std::string::npos ||
               lower.find("hi") != std::string::npos ||
               lower.find("salam") != std::string::npos) {
        body =
            "Hello! I'm **Dolphin AI**, your offline assistant.\n\n"
            "I can help with:\n"
            "- Writing & explaining code\n"
            "- Brainstorming ideas\n"
            "- Translation (English ⇄ Bangla)\n"
            "- Quick reference & math\n\n"
            "Ask me anything.";
    } else {
        body =
            "I'm running in **Phase-1 stub mode** — the chat plumbing, markdown "
            "rendering, code-block highlighting and thermal guard are all live, "
            "but real inference is enabled by adding the llama.cpp submodule.\n\n"
            "Your message: _" + prompt + "_";
    }

    // Tokenize naively into ~3-4 char chunks so streaming feels real.
    std::vector<std::string> chunks;
    std::string cur;
    for (char c : body) {
        cur.push_back(c);
        if (cur.size() >= 3 && (c == ' ' || c == '\n' || c == ',' || c == '.')) {
            chunks.push_back(cur);
            cur.clear();
        }
    }
    if (!cur.empty()) chunks.push_back(cur);
    return chunks;
}

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_piashmsu_aichat_llm_LlamaNative_nativeBackendInfo(JNIEnv* env, jclass) {
#ifdef DOLPHIN_USE_LLAMA_CPP
    return env->NewStringUTF("llama.cpp (native)");
#else
    return env->NewStringUTF("stub (Phase 1)");
#endif
}

JNIEXPORT jlong JNICALL
Java_com_piashmsu_aichat_llm_LlamaNative_nativeOpen(
        JNIEnv* env, jclass, jstring jpath, jint nCtx, jint nThreads) {
    auto s = std::make_unique<Session>();
    s->model_path = jstr(env, jpath);
    s->n_ctx = nCtx > 0 ? nCtx : 2048;
    s->n_threads = nThreads > 0 ? nThreads : 4;
    LOGI("nativeOpen path=%s n_ctx=%d n_threads=%d",
         s->model_path.c_str(), s->n_ctx, s->n_threads);
    std::lock_guard<std::mutex> lk(g_sessions_mu);
    g_sessions.push_back(std::move(s));
    return static_cast<jlong>(g_sessions.size());
}

JNIEXPORT void JNICALL
Java_com_piashmsu_aichat_llm_LlamaNative_nativeClose(JNIEnv*, jclass, jlong handle) {
    auto* s = get_session(handle);
    if (!s) return;
    s->cancel = true;
    LOGI("nativeClose handle=%lld", static_cast<long long>(handle));
}

JNIEXPORT void JNICALL
Java_com_piashmsu_aichat_llm_LlamaNative_nativeCancel(JNIEnv*, jclass, jlong handle) {
    auto* s = get_session(handle);
    if (!s) return;
    s->cancel = true;
}

JNIEXPORT void JNICALL
Java_com_piashmsu_aichat_llm_LlamaNative_nativeGenerate(
        JNIEnv* env, jclass,
        jlong handle, jstring jprompt,
        jfloat /*temperature*/, jfloat /*topP*/, jint /*topK*/,
        jint maxTokens, jint tokenRateCap,
        jobject callback) {
    auto* s = get_session(handle);
    if (!s) {
        LOGE("nativeGenerate: invalid handle %lld", (long long)handle);
        return;
    }
    s->cancel = false;

    jclass cb_cls = env->GetObjectClass(callback);
    jmethodID on_tok = env->GetMethodID(cb_cls, "onToken", "(Ljava/lang/String;)V");
    jmethodID on_done = env->GetMethodID(cb_cls, "onDone", "(Z)V");
    if (!on_tok || !on_done) {
        LOGE("nativeGenerate: callback methods missing");
        return;
    }

    std::string prompt = jstr(env, jprompt);
    auto chunks = compose_stub_reply(prompt);

    int rate = tokenRateCap > 0 ? tokenRateCap : 35;
    auto interval = std::chrono::milliseconds(static_cast<long>(1000.0 / rate));

    int emitted = 0;
    for (const auto& tok : chunks) {
        if (s->cancel.load() || (maxTokens > 0 && emitted >= maxTokens)) break;
        jstring jtok = env->NewStringUTF(tok.c_str());
        env->CallVoidMethod(callback, on_tok, jtok);
        env->DeleteLocalRef(jtok);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            break;
        }
        ++emitted;
        std::this_thread::sleep_for(interval);
    }

    bool cancelled = s->cancel.load();
    env->CallVoidMethod(callback, on_done, static_cast<jboolean>(cancelled));
}

} // extern "C"
