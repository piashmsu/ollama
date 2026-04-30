package com.piashmsu.aichat.avatar

/**
 * AI-girl avatar scaffold.
 *
 * Phase 3 will add a Live2D Cubism SDK GLSurfaceView, idle/talking/thinking
 * animation states, and lip-sync driven by TTS audio amplitude. Phase 1
 * exposes only the state machine so the chat screen can already show a
 * placeholder slot reactively.
 */
enum class AvatarState { Hidden, Idle, Listening, Thinking, Speaking }

class AvatarController {
    @Volatile var state: AvatarState = AvatarState.Hidden
    fun setEnabled(enabled: Boolean) {
        state = if (enabled) AvatarState.Idle else AvatarState.Hidden
    }
}
