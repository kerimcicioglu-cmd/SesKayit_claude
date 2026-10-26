package com.kerim.seskayit.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Servis ile arayüz arasında paylaşılan anlık kayıt durumu. */
object RecorderState {
    data class State(
        val isRecording: Boolean = false,
        /** SystemClock.elapsedRealtime() tabanlı başlangıç zamanı */
        val startedAt: Long = 0L,
        val source: String? = null,
        val label: String? = null,
    )

    internal val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable
}
