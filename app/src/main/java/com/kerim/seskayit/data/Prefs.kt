package com.kerim.seskayit.data

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

enum class Quality(val label: String, val bitRate: Int, val sampleRate: Int, val channels: Int) {
    LOW("Düşük", 64_000, 22_050, 1),
    MEDIUM("Orta", 128_000, 44_100, 1),
    HIGH("Yüksek", 256_000, 48_000, 2),
}

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun setBool(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    private fun str(key: String, def: String = "") = sp.getString(key, def) ?: def
    private fun setStr(key: String, v: String) = sp.edit().putString(key, v).apply()

    var autoRecordPhone: Boolean
        get() = bool("auto_phone", true)
        set(v) = setBool("auto_phone", v)

    var autoRecordWhatsApp: Boolean
        get() = bool("auto_whatsapp", true)
        set(v) = setBool("auto_whatsapp", v)

    /** Kapalıysa yalnızca listedeki kişiler kaydedilir. */
    var recordAllCalls: Boolean
        get() = bool("record_all", true)
        set(v) = setBool("record_all", v)

    var showNotification: Boolean
        get() = bool("show_notification", true)
        set(v) = setBool("show_notification", v)

    var quality: Quality
        get() = runCatching { Quality.valueOf(str("quality", Quality.MEDIUM.name)) }.getOrDefault(Quality.MEDIUM)
        set(v) = setStr("quality", v.name)

    var groqKey: String
        get() = str("groq_key")
        set(v) = setStr("groq_key", v.trim())

    var geminiKey: String
        get() = str("gemini_key")
        set(v) = setStr("gemini_key", v.trim())

    var groqChatModel: String
        get() = str("groq_chat_model", DEFAULT_GROQ_CHAT_MODEL).ifBlank { DEFAULT_GROQ_CHAT_MODEL }
        set(v) = setStr("groq_chat_model", v.trim())

    var geminiModel: String
        get() = str("gemini_model", DEFAULT_GEMINI_MODEL).ifBlank { DEFAULT_GEMINI_MODEL }
        set(v) = setStr("gemini_model", v.trim())

    var firstRunDone: Boolean
        get() = bool("first_run_done", false)
        set(v) = setBool("first_run_done", v)

    val hasPassword: Boolean get() = str("pw_hash").isNotEmpty()

    fun setPassword(password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        sp.edit().putString("pw_salt", salt).putString("pw_hash", hash(salt, password)).apply()
    }

    fun clearPassword() {
        sp.edit().remove("pw_salt").remove("pw_hash").apply()
    }

    fun checkPassword(password: String): Boolean =
        hasPassword && hash(str("pw_salt"), password) == str("pw_hash")

    private fun hash(salt: String, password: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + password).toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    companion object {
        const val DEFAULT_GROQ_CHAT_MODEL = "llama-3.3-70b-versatile"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
    }
}
