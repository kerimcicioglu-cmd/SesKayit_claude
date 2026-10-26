package com.kerim.seskayit

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDING, "Kayıt", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Kayıt devam ederken gösterilir" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SILENT, "Sessiz kayıt", NotificationManager.IMPORTANCE_MIN)
                .apply {
                    description = "Bildirim kapalıyken kullanılan, en düşük öncelikli kanal"
                    setShowBadge(false)
                }
        )
    }

    companion object {
        const val CHANNEL_RECORDING = "recording"
        const val CHANNEL_SILENT = "recording_silent"
    }
}
