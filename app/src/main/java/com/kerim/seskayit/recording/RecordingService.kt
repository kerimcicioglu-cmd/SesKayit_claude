package com.kerim.seskayit.recording

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kerim.seskayit.App
import com.kerim.seskayit.R
import com.kerim.seskayit.call.CallUtils
import com.kerim.seskayit.data.AppDatabase
import com.kerim.seskayit.data.Prefs
import com.kerim.seskayit.data.Recording
import com.kerim.seskayit.data.Source
import com.kerim.seskayit.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startElapsed = 0L
    private var startWallClock = 0L
    private var source = Source.MANUAL
    private var number: String? = null
    private var name: String? = null
    private var pendingSaves = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val src = intent.getStringExtra(EXTRA_SOURCE) ?: Source.MANUAL
                val label = labelFor(src, intent.getStringExtra(EXTRA_NAME), intent.getStringExtra(EXTRA_NUMBER))
                if (!startForegroundSafe(label)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (recorder == null) {
                    startRecording(src, intent.getStringExtra(EXTRA_NUMBER), intent.getStringExtra(EXTRA_NAME))
                }
            }
            ACTION_STOP -> {
                val only = intent.getStringExtra(EXTRA_SOURCE)
                if (recorder != null && (only == null || only == source)) {
                    stopRecording(intent.getStringExtra(EXTRA_NUMBER))
                } else if (recorder == null && pendingSaves == 0) {
                    stopSelf()
                }
            }
            else -> if (recorder == null && pendingSaves == 0) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun labelFor(src: String, name: String?, number: String?): String {
        val who = name ?: number
        return when (src) {
            Source.PHONE -> "Telefon görüşmesi kaydediliyor" + (who?.let { ": $it" } ?: "")
            Source.WHATSAPP -> "WhatsApp araması kaydediliyor" + (who?.let { ": $it" } ?: "")
            else -> "Ses kaydı yapılıyor"
        }
    }

    private fun startForegroundSafe(text: String): Boolean {
        val prefs = Prefs(this)
        val channel = if (prefs.showNotification) App.CHANNEL_RECORDING else App.CHANNEL_SILENT
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setUsesChronometer(true)
            .setPriority(if (prefs.showNotification) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp)
            .addAction(0, "Durdur", stopIntent)
            .build()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ön plan servisi başlatılamadı", e)
            false
        }
    }

    private fun audioSources(src: String): List<Int> =
        if (src == Source.MANUAL) {
            listOf(MediaRecorder.AudioSource.MIC)
        } else {
            // Android 10+ üçüncü taraf uygulamalara VOICE_CALL izni vermez; erişilebilirlik servisi
            // açıkken VOICE_RECOGNITION kaynağı görüşme sırasında ses almaya en elverişli olanıdır.
            listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            )
        }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()

    private fun startRecording(src: String, num: String?, nm: String?) {
        val quality = Prefs(this).quality
        val dir = File(filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "kayit_${System.currentTimeMillis()}.m4a")

        for (audioSource in audioSources(src)) {
            val r = newRecorder()
            try {
                r.setAudioSource(audioSource)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioEncodingBitRate(quality.bitRate)
                r.setAudioSamplingRate(quality.sampleRate)
                r.setAudioChannels(quality.channels)
                r.setOutputFile(file.absolutePath)
                r.prepare()
                r.start()
                recorder = r
                break
            } catch (e: Exception) {
                Log.w(TAG, "Ses kaynağı $audioSource başarısız", e)
                runCatching { r.release() }
                file.delete()
            }
        }

        if (recorder == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        outputFile = file
        source = src
        number = num
        name = nm ?: CallUtils.lookupName(this, num)
        startElapsed = SystemClock.elapsedRealtime()
        startWallClock = System.currentTimeMillis()
        RecorderState.mutable.value = RecorderState.State(
            isRecording = true, startedAt = startElapsed, source = src, label = name ?: number
        )

        // Numara / isim baştan biliniyorsa ve listede değilse kaydı hemen iptal et.
        if (src != Source.MANUAL && (num != null || nm != null)) {
            scope.launch {
                if (!isAllowed(num, name)) {
                    launch(Dispatchers.Main) { discardIfSame(file) }
                }
            }
        }
    }

    private suspend fun isAllowed(num: String?, nm: String?): Boolean {
        val prefs = Prefs(this)
        if (prefs.recordAllCalls) return true
        return CallUtils.matches(AppDatabase.get(this).autoContactDao().getAll(), num, nm)
    }

    private fun discardIfSame(file: File) {
        if (outputFile != file) return
        releaseRecorder()
        file.delete()
        finishIfIdle()
    }

    private fun releaseRecorder(): Boolean {
        val r = recorder ?: return false
        var ok = true
        try {
            r.stop()
        } catch (e: RuntimeException) {
            ok = false
        }
        runCatching { r.release() }
        recorder = null
        RecorderState.mutable.value = RecorderState.State()
        return ok
    }

    private fun stopRecording(numberHint: String?) {
        val file = outputFile ?: return
        val duration = SystemClock.elapsedRealtime() - startElapsed
        val ok = releaseRecorder()
        outputFile = null

        if (!ok || duration < MIN_DURATION_MS) {
            file.delete()
            finishIfIdle()
            return
        }

        val src = source
        val startedAt = startWallClock
        var num = numberHint ?: number
        var nm = name
        pendingSaves++
        scope.launch {
            try {
                if (src == Source.PHONE && num.isNullOrBlank()) {
                    delay(1500) // arama kaydı (call log) birkaç yüz ms sonra yazılır
                    num = CallUtils.lastCallNumber(this@RecordingService, startedAt)
                }
                if (nm == null) nm = CallUtils.lookupName(this@RecordingService, num)

                if (src != Source.MANUAL && !isAllowed(num, nm)) {
                    file.delete()
                } else {
                    AppDatabase.get(this@RecordingService).recordingDao().insert(
                        Recording(
                            title = buildTitle(src, nm, num, startedAt),
                            filePath = file.absolutePath,
                            createdAt = startedAt,
                            durationMs = duration,
                            isWhatsApp = src == Source.WHATSAPP,
                            source = src,
                            phoneNumber = num,
                            contactName = nm,
                        )
                    )
                }
            } finally {
                launch(Dispatchers.Main) {
                    pendingSaves--
                    finishIfIdle()
                }
            }
        }
    }

    private fun finishIfIdle() {
        if (recorder == null && pendingSaves == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (recorder != null) {
            val file = outputFile
            val duration = SystemClock.elapsedRealtime() - startElapsed
            val ok = releaseRecorder()
            if (file != null && ok && duration >= MIN_DURATION_MS) {
                val rec = Recording(
                    title = buildTitle(source, name, number, startWallClock),
                    filePath = file.absolutePath, createdAt = startWallClock, durationMs = duration,
                    isWhatsApp = source == Source.WHATSAPP, source = source,
                    phoneNumber = number, contactName = name,
                )
                val db = AppDatabase.get(applicationContext)
                CoroutineScope(Dispatchers.IO).launch { db.recordingDao().insert(rec) }
            } else {
                file?.delete()
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RecordingService"
        private const val NOTIFICATION_ID = 42
        const val MIN_DURATION_MS = 500L

        const val ACTION_START = "com.kerim.seskayit.START"
        const val ACTION_STOP = "com.kerim.seskayit.STOP"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_NAME = "name"

        private val dateFormat get() = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("tr", "TR"))

        fun buildTitle(src: String, name: String?, number: String?, time: Long): String {
            val date = dateFormat.format(Date(time))
            val who = when {
                !name.isNullOrBlank() && !number.isNullOrBlank() -> "$name ($number)"
                !name.isNullOrBlank() -> name
                !number.isNullOrBlank() -> number
                else -> "Bilinmeyen"
            }
            return when (src) {
                Source.PHONE -> "Arama: $who - $date"
                Source.WHATSAPP -> "WhatsApp: $who - $date"
                else -> "Ses Kaydı - $date"
            }
        }

        fun start(context: Context, source: String, number: String? = null, name: String? = null) {
            val i = Intent(context, RecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SOURCE, source)
                .putExtra(EXTRA_NUMBER, number)
                .putExtra(EXTRA_NAME, name)
            try {
                ContextCompat.startForegroundService(context, i)
            } catch (e: Exception) {
                Log.e(TAG, "Kayıt servisi başlatılamadı (arka plan kısıtlaması olabilir)", e)
            }
        }

        /** @param onlySource null değilse yalnızca bu kaynaktan başlatılmış kayıt durdurulur. */
        fun stop(context: Context, onlySource: String? = null, number: String? = null) {
            val i = Intent(context, RecordingService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_SOURCE, onlySource)
                .putExtra(EXTRA_NUMBER, number)
            try {
                context.startService(i)
            } catch (e: Exception) {
                Log.e(TAG, "Kayıt servisi durdurulamadı", e)
            }
        }
    }
}
