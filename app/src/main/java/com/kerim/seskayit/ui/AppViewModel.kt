package com.kerim.seskayit.ui

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kerim.seskayit.ai.AiClient
import com.kerim.seskayit.data.AppDatabase
import com.kerim.seskayit.data.AutoContact
import com.kerim.seskayit.data.Prefs
import com.kerim.seskayit.data.Recording
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)
    private val db = AppDatabase.get(app)
    private val recordingDao = db.recordingDao()
    private val contactDao = db.autoContactDao()
    private val ai = AiClient(prefs)

    val recordings: StateFlow<List<Recording>> =
        recordingDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val autoContacts: StateFlow<List<AutoContact>> =
        contactDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---------------------------------------------------------------- Oynatma

    data class Playback(val id: Long? = null, val positionMs: Int = 0, val durationMs: Int = 0)

    private val _playback = MutableStateFlow(Playback())
    val playback: StateFlow<Playback> = _playback.asStateFlow()
    private var player: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()
    fun consumeMessage() { _messages.value = null }
    private fun toast(msg: String) { _messages.value = msg }

    fun togglePlay(rec: Recording) {
        if (_playback.value.id == rec.id) {
            stopPlayback()
            return
        }
        stopPlayback()
        if (!File(rec.filePath).exists()) {
            toast("Ses dosyası bulunamadı")
            return
        }
        try {
            val mp = MediaPlayer().apply {
                setDataSource(rec.filePath)
                prepare()
                setOnCompletionListener { stopPlayback() }
                start()
            }
            player = mp
            _playback.value = Playback(rec.id, 0, mp.duration)
            progressJob = viewModelScope.launch {
                while (isActive) {
                    player?.let { p -> _playback.update { it.copy(positionMs = p.currentPosition) } }
                    delay(250)
                }
            }
        } catch (e: Exception) {
            toast("Oynatılamadı: ${e.message}")
            stopPlayback()
        }
    }

    fun seekTo(fraction: Float) {
        val p = player ?: return
        p.seekTo((p.duration * fraction).toInt())
    }

    fun stopPlayback() {
        progressJob?.cancel()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        _playback.value = Playback()
    }

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }

    // ---------------------------------------------------------------- Kayıt işlemleri

    fun rename(rec: Recording, newTitle: String) = viewModelScope.launch {
        if (newTitle.isNotBlank()) recordingDao.update(rec.copy(title = newTitle.trim()))
    }

    fun delete(rec: Recording) = viewModelScope.launch {
        if (_playback.value.id == rec.id) stopPlayback()
        File(rec.filePath).delete()
        recordingDao.delete(rec)
        toast("Kayıt silindi")
    }

    /** Kayıt kimliği -> devam eden işlem açıklaması */
    private val _busy = MutableStateFlow<Map<Long, String>>(emptyMap())
    val busy: StateFlow<Map<Long, String>> = _busy.asStateFlow()

    private fun setBusy(id: Long, text: String?) = _busy.update {
        if (text == null) it - id else it + (id to text)
    }

    fun transcribe(rec: Recording) {
        if (rec.id in _busy.value) return
        viewModelScope.launch {
            setBusy(rec.id, "Deşifre ediliyor…")
            try {
                val text = ai.transcribeWithSpeakers(File(rec.filePath), rec.contactName)
                recordingDao.getById(rec.id)?.let { recordingDao.update(it.copy(transcript = text)) }
            } catch (e: Exception) {
                toast("Deşifre başarısız:\n${e.message}")
            } finally {
                setBusy(rec.id, null)
            }
        }
    }

    fun summarize(rec: Recording) {
        if (rec.id in _busy.value) return
        viewModelScope.launch {
            try {
                val transcript: String = rec.transcript?.takeIf { it.isNotBlank() } ?: run {
                    setBusy(rec.id, "Önce deşifre ediliyor…")
                    val t = ai.transcribeWithSpeakers(File(rec.filePath), rec.contactName)
                    recordingDao.getById(rec.id)?.let { recordingDao.update(it.copy(transcript = t)) }
                    t
                }
                setBusy(rec.id, "Özetleniyor…")
                val summary = ai.summarize(transcript, rec.contactName)
                recordingDao.getById(rec.id)?.let { recordingDao.update(it.copy(summary = summary)) }
            } catch (e: Exception) {
                toast("Özetleme başarısız:\n${e.message}")
            } finally {
                setBusy(rec.id, null)
            }
        }
    }

    // ---------------------------------------------------------------- Toplu analiz

    data class BulkState(
        val running: Boolean = false,
        val progress: String? = null,
        val report: String? = null,
        val error: String? = null,
    )

    private val _bulk = MutableStateFlow(BulkState())
    val bulk: StateFlow<BulkState> = _bulk.asStateFlow()

    fun matchingRecordings(name: String, start: Long, end: Long): List<Recording> {
        val q = name.trim().lowercase(TR)
        return recordings.value.filter { r ->
            r.createdAt in start..end && (q.isEmpty() ||
                r.contactName?.lowercase(TR)?.contains(q) == true ||
                r.title.lowercase(TR).contains(q) ||
                (r.phoneNumber != null && r.phoneNumber.filter { it.isDigit() }.contains(q.filter { it.isDigit() }.ifEmpty { "-" })))
        }.sortedBy { it.createdAt }
    }

    fun bulkAnalyze(name: String, start: Long, end: Long) {
        if (_bulk.value.running) return
        val list = matchingRecordings(name, start, end)
        if (list.isEmpty()) {
            _bulk.value = BulkState(error = "Seçilen kriterlere uygun kayıt bulunamadı.")
            return
        }
        viewModelScope.launch {
            _bulk.value = BulkState(running = true, progress = "Hazırlanıyor…")
            try {
                val items = mutableListOf<Pair<String, String>>()
                list.forEachIndexed { i, rec ->
                    var text = rec.transcript
                    if (text.isNullOrBlank()) {
                        _bulk.update { it.copy(progress = "Deşifre ediliyor (${i + 1}/${list.size})…") }
                        text = runCatching { ai.transcribeWithSpeakers(File(rec.filePath), rec.contactName) }
                            .getOrNull()
                        if (text != null) recordingDao.update(rec.copy(transcript = text))
                    }
                    if (!text.isNullOrBlank()) {
                        items += "${dateFmt.format(Date(rec.createdAt))} — ${rec.title}" to text
                    }
                }
                if (items.isEmpty()) throw IllegalStateException("Hiçbir kayıt deşifre edilemedi. API anahtarını kontrol edin.")
                _bulk.update { it.copy(progress = "${items.size} görüşme analiz ediliyor…") }
                val range = "${dayFmt.format(Date(start))} – ${dayFmt.format(Date(end))}"
                val report = ai.bulkAnalyze(name.ifBlank { "Tüm kişiler" }, range, items)
                _bulk.value = BulkState(report = report)
            } catch (e: Exception) {
                _bulk.value = BulkState(error = e.message ?: "Bilinmeyen hata")
            }
        }
    }

    // ---------------------------------------------------------------- Otomatik kayıt listesi

    fun addContact(name: String, number: String) = viewModelScope.launch {
        if (number.isBlank() && name.isBlank()) return@launch
        contactDao.insert(AutoContact(name = name.trim().ifBlank { number.trim() }, number = number.trim()))
    }

    fun removeContact(c: AutoContact) = viewModelScope.launch { contactDao.delete(c) }

    companion object {
        val TR = Locale("tr", "TR")
        val dateFmt get() = SimpleDateFormat("d MMM yyyy, HH:mm", TR)
        val dayFmt get() = SimpleDateFormat("d MMM yyyy", TR)

        fun formatDuration(ms: Long): String {
            val s = ms / 1000
            return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
            else "%02d:%02d".format(s / 60, s % 60)
        }
    }
}
