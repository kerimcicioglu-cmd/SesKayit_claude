package com.kerim.seskayit.call

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Hiçbir ekran içeriği okumaz. Etkin olması, Android'in görüşme sırasında uygulamanın
 * mikrofona erişimine izin vermesine yardımcı olur (birçok arama kaydedicinin kullandığı yöntem).
 */
class RecorderAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
