package com.piashmsu.aichat.llm

/**
 * JNI bridge to the native inference engine.
 *
 * Phase 1: backed by a stub native lib (`dolphinai_jni.cpp`) that produces
 * deterministic streaming output so the entire app can be exercised without a
 * real LLM. Phase 2 swaps the same JNI surface to llama.cpp once the submodule
 * is added under `app/src/main/cpp/llama.cpp`.
 */
object LlamaNative {
    init {
        System.loadLibrary("dolphinai")
    }

    interface Callback {
        fun onToken(token: String)
        fun onDone(cancelled: Boolean)
    }

    fun backendInfo(): String = nativeBackendInfo()
    fun open(modelPath: String, contextSize: Int, threads: Int): Long =
        nativeOpen(modelPath, contextSize, threads)
    fun close(handle: Long) = nativeClose(handle)
    fun cancel(handle: Long) = nativeCancel(handle)
    fun generate(
        handle: Long,
        prompt: String,
        temperature: Float,
        topP: Float,
        topK: Int,
        maxTokens: Int,
        tokenRateCap: Int,
        callback: Callback,
    ) = nativeGenerate(handle, prompt, temperature, topP, topK, maxTokens, tokenRateCap, callback)

    @JvmStatic external fun nativeBackendInfo(): String
    @JvmStatic external fun nativeOpen(modelPath: String, contextSize: Int, threads: Int): Long
    @JvmStatic external fun nativeClose(handle: Long)
    @JvmStatic external fun nativeCancel(handle: Long)
    @JvmStatic external fun nativeGenerate(
        handle: Long,
        prompt: String,
        temperature: Float,
        topP: Float,
        topK: Int,
        maxTokens: Int,
        tokenRateCap: Int,
        callback: Callback,
    )
}
