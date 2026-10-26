package com.kerim.seskayit.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.kerim.seskayit.data.AutoContact
import java.util.Locale

object CallUtils {
    private val TR = Locale("tr", "TR")

    fun has(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Rehberde numaraya karşılık gelen kişi adı. */
    fun lookupName(context: Context, number: String?): String? {
        if (number.isNullOrBlank() || !has(context, Manifest.permission.READ_CONTACTS)) return null
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    /** Arama kaydındaki (call log) en son numara; arama bitince numarayı bulmak için. */
    fun lastCallNumber(context: Context, notBefore: Long): String? {
        if (!has(context, Manifest.permission.READ_CALL_LOG)) return null
        return runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                null, null, "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                if (c.moveToFirst() && c.getLong(1) >= notBefore - 60_000) c.getString(0) else null
            }
        }.getOrNull()
    }

    fun normalize(number: String?): String = number.orEmpty().filter { it.isDigit() }.takeLast(10)

    fun matches(list: List<AutoContact>, number: String?, name: String?): Boolean {
        val n = normalize(number)
        return list.any { c ->
            (n.length >= 7 && normalize(c.number) == n) ||
                (!name.isNullOrBlank() && c.name.trim().lowercase(TR) == name.trim().lowercase(TR))
        }
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any {
            it.startsWith(context.packageName + "/") && it.endsWith("RecorderAccessibilityService")
        }
    }
}
