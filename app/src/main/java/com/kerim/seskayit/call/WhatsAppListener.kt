package com.kerim.seskayit.call

import android.Manifest
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kerim.seskayit.data.Prefs
import com.kerim.seskayit.data.Source
import com.kerim.seskayit.recording.RecorderState
import com.kerim.seskayit.recording.RecordingService
import java.util.Locale

/**
 * WhatsApp ve WhatsApp Business bildirimlerini izleyerek sesli/görüntülü aramaları algılar.
 * Arama bildirimi görününce kayıt başlar, tüm arama bildirimleri kalkınca kayıt durur.
 */
class WhatsAppListener : NotificationListenerService() {

    private val activeCallKeys = mutableSetOf<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in PACKAGES) return
        val prefs = Prefs(this)
        if (!prefs.autoRecordWhatsApp) return

        val n = sbn.notification ?: return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
        ).joinToString(" ")

        val kind = classify(n, title, text)
        if (kind == CallKind.NONE) {
            // Arama bildirimi mesaj/cevapsız bildirimine dönüştüyse aramayı bitmiş say.
            if (activeCallKeys.remove(sbn.key)) maybeStop()
            return
        }

        activeCallKeys.add(sbn.key)
        if (kind == CallKind.RINGING) return // çalıyor; cevaplanınca bildirim "devam eden" olur

        val state = RecorderState.state.value
        if (!state.isRecording && CallUtils.has(this, Manifest.permission.RECORD_AUDIO)) {
            val name = title?.takeUnless { it.isBlank() || it.equals("WhatsApp", true) || it.startsWith("WhatsApp ") }
            RecordingService.start(this, Source.WHATSAPP, name = name)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName !in PACKAGES) return
        if (activeCallKeys.remove(sbn.key)) maybeStop()
    }

    override fun onListenerDisconnected() {
        activeCallKeys.clear()
        super.onListenerDisconnected()
    }

    private fun maybeStop() {
        if (activeCallKeys.isEmpty() && RecorderState.state.value.source == Source.WHATSAPP) {
            RecordingService.stop(this, onlySource = Source.WHATSAPP)
        }
    }

    enum class CallKind { NONE, RINGING, ACTIVE }

    companion object {
        val PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val TR = Locale("tr", "TR")

        private val CALL_WORDS = listOf(
            "gelen arama", "gelen sesli arama", "gelen görüntülü arama", "sesli arama",
            "görüntülü arama", "devam eden", "aranıyor", "çalıyor", "bağlanıyor",
            "ongoing call", "ongoing voice call", "ongoing video call", "incoming voice call",
            "incoming video call", "incoming call", "voice call", "video call", "calling", "ringing",
        )
        private val RINGING_WORDS = listOf("gelen", "incoming")
        private val EXCLUDE_WORDS = listOf("cevapsız", "missed", "kaçırılan")

        fun classify(n: Notification, title: String?, text: String): CallKind {
            val haystack = "${title.orEmpty()} $text".lowercase(TR)
            if (EXCLUDE_WORDS.any { it in haystack }) return CallKind.NONE

            val isCallCategory = n.category == Notification.CATEGORY_CALL
            val isMessage = n.category == Notification.CATEGORY_MESSAGE ||
                n.extras.containsKey(Notification.EXTRA_MESSAGES) ||
                n.extras.getCharSequence(Notification.EXTRA_TEMPLATE)?.contains("MessagingStyle") == true
            if (isMessage && !isCallCategory) return CallKind.NONE

            val ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
                n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
            val keyword = CALL_WORDS.any { it in haystack }

            // Mesaj içinde "sesli arama" yazması aramayı tetiklemesin: anahtar kelime tek başına
            // yetmez, bildirim kalıcı (ongoing) olmalı ya da arama kategorisinde olmalı.
            if (!isCallCategory && !(keyword && ongoing)) return CallKind.NONE

            return if (RINGING_WORDS.any { it in haystack } && !haystack.contains("devam eden") &&
                !haystack.contains("ongoing")
            ) CallKind.RINGING else CallKind.ACTIVE
        }
    }
}
