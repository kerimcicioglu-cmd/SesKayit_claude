package com.kerim.seskayit.ai

import android.util.Base64
import com.kerim.seskayit.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Groq (Whisper + sohbet modeli) birincil, Gemini yedek servis.
 */
class AiClient(private val prefs: Prefs) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    // ---------------------------------------------------------------- Genel işlemler

    /** Ses dosyasını Türkçe metne çevirir ve konuşmacıları ayırır. */
    suspend fun transcribeWithSpeakers(file: File, otherName: String?): String {
        val other = otherName?.takeIf { it.isNotBlank() } ?: "Karşı Taraf"
        val errors = mutableListOf<String>()

        if (prefs.groqKey.isNotBlank()) {
            try {
                val raw = groqTranscribe(file)
                if (raw.isBlank()) throw IOException("Boş deşifre döndü")
                return runCatching { diarize(raw, other) }.getOrElse { raw }
            } catch (e: Exception) {
                errors += "Groq: ${e.message}"
            }
        }
        if (prefs.geminiKey.isNotBlank()) {
            try {
                return geminiAudio(file, diarizedTranscriptPrompt(other))
            } catch (e: Exception) {
                errors += "Gemini: ${e.message}"
            }
        }
        throw IOException(
            if (errors.isEmpty()) "Ayarlar ekranından Groq (veya Gemini) API anahtarı girin."
            else errors.joinToString("\n")
        )
    }

    suspend fun summarize(transcript: String, otherName: String?): String {
        val other = otherName?.takeIf { it.isNotBlank() } ?: "karşı taraf"
        return chat(
            system = "Sen Türkçe konuşan profesyonel bir asistansın. Görüşme metinlerinden kısa, net ve " +
                "profesyonel özetler çıkarırsın. Uydurma bilgi eklemezsin.",
            user = """
                Aşağıdaki görüşme kaydı metnini Türkçe olarak özetle. Görüşme "Ben" ile $other arasında geçmektedir.

                Biçim:
                **Kısa Özet:** (2-4 cümle)
                **Önemli Hususlar:**
                - madde madde önemli bilgiler, kararlar, tarihler, tutarlar
                **Yapılacaklar / Takip:**
                - varsa (yoksa "Belirtilmedi")

                Görüşme metni:
                $transcript
            """.trimIndent()
        )
    }

    suspend fun bulkAnalyze(contact: String, range: String, items: List<Pair<String, String>>): String {
        val perItem = (MAX_BULK_CHARS / items.size.coerceAtLeast(1)).coerceAtLeast(1500)
        val body = items.joinToString("\n\n") { (header, text) ->
            "### $header\n${text.take(perItem)}"
        }
        return chat(
            system = "Sen Türkçe konuşan, görüşme kayıtlarını analiz eden profesyonel bir asistansın. " +
                "Yalnızca verilen metinlere dayanırsın.",
            user = """
                "$contact" ile $range tarihleri arasında yapılan ${items.size} görüşmenin metinleri aşağıdadır.
                Bunları birlikte analiz ederek "Toplu Görüşme Analiz Raporu" hazırla.

                Biçim:
                **Genel Değerlendirme:**
                **Görüşmelerin Kronolojik Özeti:** (her görüşme için tarih + 1-2 cümle)
                **Öne Çıkan Konular:**
                **Verilen Sözler / Kararlar:**
                **Çelişkiler veya Değişen Tutumlar:** (varsa)
                **Açık Kalan Konular ve Öneriler:**

                $body
            """.trimIndent()
        )
    }

    // ---------------------------------------------------------------- Groq

    private suspend fun groqTranscribe(file: File): String = withContext(Dispatchers.IO) {
        if (file.length() > 25L * 1024 * 1024) throw IOException("Dosya 25 MB'tan büyük")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-large-v3-turbo")
            .addFormDataPart("language", "tr")
            .addFormDataPart("response_format", "json")
            .addFormDataPart("temperature", "0")
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mp4".toMediaType()))
            .build()
        val req = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer ${prefs.groqKey}")
            .post(body)
            .build()
        JSONObject(execute(req)).optString("text").trim()
    }

    private suspend fun groqChat(system: String, user: String): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("model", prefs.groqChatModel)
            .put("temperature", 0.2)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user))
            )
        val req = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer ${prefs.groqKey}")
            .post(payload.toString().toRequestBody(json))
            .build()
        JSONObject(execute(req))
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content").trim()
    }

    // ---------------------------------------------------------------- Gemini

    private suspend fun geminiAudio(file: File, prompt: String): String = withContext(Dispatchers.IO) {
        if (file.length() > 18L * 1024 * 1024) throw IOException("Dosya Gemini için çok büyük (18 MB üstü)")
        val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "audio/mp4").put("data", b64)))
        geminiGenerate(parts)
    }

    private suspend fun geminiText(system: String, user: String): String = withContext(Dispatchers.IO) {
        geminiGenerate(JSONArray().put(JSONObject().put("text", "$system\n\n$user")))
    }

    private fun geminiGenerate(parts: JSONArray): String {
        val payload = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("temperature", 0.2))
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${prefs.geminiModel}:generateContent")
            .header("x-goog-api-key", prefs.geminiKey)
            .post(payload.toString().toRequestBody(json))
            .build()
        val res = JSONObject(execute(req))
        val outParts = res.getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        return buildString {
            for (i in 0 until outParts.length()) append(outParts.getJSONObject(i).optString("text"))
        }.trim().ifBlank { throw IOException("Gemini boş yanıt döndü") }
    }

    // ---------------------------------------------------------------- Yardımcılar

    private suspend fun chat(system: String, user: String): String {
        val errors = mutableListOf<String>()
        if (prefs.groqKey.isNotBlank()) {
            try {
                return groqChat(system, user)
            } catch (e: Exception) {
                errors += "Groq: ${e.message}"
            }
        }
        if (prefs.geminiKey.isNotBlank()) {
            try {
                return geminiText(system, user)
            } catch (e: Exception) {
                errors += "Gemini: ${e.message}"
            }
        }
        throw IOException(
            if (errors.isEmpty()) "Ayarlar ekranından Groq (veya Gemini) API anahtarı girin."
            else errors.joinToString("\n")
        )
    }

    private suspend fun diarize(raw: String, other: String): String = chat(
        system = "Sen telefon görüşmesi deşifrelerini düzenleyen bir asistansın. Metne ekleme yapmaz, " +
            "anlamını değiştirmezsin; yalnızca konuşmacılara göre ayırırsın.",
        user = """
            Aşağıda bir görüşmenin ham Türkçe deşifresi var. Kaydı yapan kişi "Ben", diğer kişi "$other".
            Metni bağlamdan yola çıkarak konuşmacılara ayır. Her konuşma sırasını yeni satırda
            "Ben:" veya "$other:" ile başlat. Yazım hatalarını düzelt ama içerik ekleme/çıkarma yapma.
            Yalnızca düzenlenmiş metni döndür, açıklama yazma.

            Ham metin:
            $raw
        """.trimIndent()
    )

    private fun diarizedTranscriptPrompt(other: String) = """
        Bu ses kaydını eksiksiz ve kelimesi kelimesine Türkçe metne dönüştür.
        Konuşmacıları ayır: kaydı yapan kişi "Ben", diğer kişi "$other".
        Her konuşma sırasını yeni satırda "Ben:" veya "$other:" ile başlat.
        Yalnızca deşifre metnini döndür, özet veya açıklama ekleme.
    """.trimIndent()

    private fun execute(req: Request): String {
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").optString("message") }
                    .getOrNull()?.takeIf { it.isNotBlank() } ?: text.take(300)
                throw IOException("HTTP ${res.code}: $msg")
            }
            return text
        }
    }

    companion object {
        private const val MAX_BULK_CHARS = 60_000
    }
}
