package com.kerim.seskayit.call

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.kerim.seskayit.data.Prefs
import com.kerim.seskayit.data.Source
import com.kerim.seskayit.recording.RecordingService

/** Gelen/giden GSM aramalarını PHONE_STATE yayınıyla algılar. */
class PhoneCallReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_NEW_OUTGOING_CALL -> {
                outgoingNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                return
            }
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> Unit
            else -> return
        }

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incoming = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!incoming.isNullOrBlank()) incomingNumber = incoming
                lastState = state
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (lastState == TelephonyManager.EXTRA_STATE_OFFHOOK) return
                val wasRinging = lastState == TelephonyManager.EXTRA_STATE_RINGING
                lastState = state
                val number = if (wasRinging) incomingNumber ?: incoming else outgoingNumber
                val prefs = Prefs(context)
                if (prefs.autoRecordPhone && CallUtils.has(context, Manifest.permission.RECORD_AUDIO)) {
                    RecordingService.start(context, Source.PHONE, number = number)
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (lastState == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                    val number = incomingNumber ?: outgoingNumber
                    RecordingService.stop(context, onlySource = Source.PHONE, number = number)
                }
                lastState = state
                incomingNumber = null
                outgoingNumber = null
            }
        }
    }

    companion object {
        private var lastState: String = TelephonyManager.EXTRA_STATE_IDLE
        private var incomingNumber: String? = null
        private var outgoingNumber: String? = null
    }
}
