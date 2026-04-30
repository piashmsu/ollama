package com.piashmsu.aichat.voice

import android.content.Context

/**
 * Voice / speech scaffold.
 *
 * Phase 1: hooks for permission flow + mic state UI.
 * Phase 2: integrate Whisper.cpp for offline STT (small/tiny multi-lingual)
 * and Sherpa-ONNX for TTS. The interface here will not change.
 */
class VoiceController(private val context: Context) {
    private var listening: Boolean = false

    fun isAvailable(): Boolean = false // wired up in Phase 2
    fun startListening(onPartial: (String) -> Unit, onFinal: (String) -> Unit) {
        listening = true
        // Phase 2: feed AudioRecord into whisper.cpp
    }
    fun stopListening() { listening = false }
    fun isListening(): Boolean = listening

    fun speak(text: String) {
        // Phase 2: route through Sherpa-ONNX or Android's built-in TTS as fallback
    }
}
