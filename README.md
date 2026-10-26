# Ses Kayıt (Android)

Manuel ses kaydı, telefon ve WhatsApp aramalarını otomatik kaydetme, Groq Whisper / Gemini ile
Türkçe deşifre, konuşmacı ayırma, özet ve toplu görüşme analizi yapan Android uygulaması.

**Teknoloji:** Kotlin · Jetpack Compose (Material 3) · Room · OkHttp · minSdk 26 (Android 8) · targetSdk 34

## APK nasıl alınır?

### Yol 1 — GitHub Actions (bilgisayara bir şey kurmadan)
1. Bu klasörü bir GitHub deposuna yükleyin.
2. **Actions** sekmesinde “APK Derle” iş akışı otomatik çalışır (ya da *Run workflow*).
3. Bittiğinde *Artifacts* altındaki **SesKayit-apk** dosyasını indirin, zip’i açın, `app-debug.apk`’yı telefona atıp kurun
   (Xiaomi: *Ayarlar > Ek ayarlar > Gizlilik > Bilinmeyen kaynaklar* izni gerekebilir).

### Yol 2 — Android Studio
Klasörü Android Studio ile açın → *Build > Build APK(s)*. Komut satırından: `./gradlew assembleDebug`
→ `app/build/outputs/apk/debug/app-debug.apk`

## İlk kurulum (Xiaomi / HyperOS için önemli)
Uygulamada **İzinler** sekmesine girip tüm maddeleri yeşile çevirin:
- Mikrofon, Telefon durumu, Arama kayıtları, Rehber, Bildirim
- **WhatsApp için bildirim erişimi** (WhatsApp aramalarını algılamak için şart)
- **Pil optimizasyonundan muafiyet** + Xiaomi **Otomatik başlatma** + Pil tasarrufu **“Kısıtlama yok”**
- **Erişilebilirlik servisi** (önerilir — görüşme sırasında mikrofon erişimini kolaylaştırır)

Ardından **Ayarlar** sekmesinden Groq API anahtarınızı (console.groq.com) ve isteğe bağlı Gemini anahtarınızı
(aistudio.google.com) girin.

## Özellikler ve nerede oldukları
| Özellik | Dosya |
|---|---|
| Kayıt servisi (AAC/.m4a, 64/128/256 kbps, <0,5 sn silme, başlık üretimi) | `recording/RecordingService.kt` |
| GSM arama algılama (gelen/giden, numara, call log) | `call/PhoneCallReceiver.kt`, `call/CallUtils.kt` |
| WhatsApp / WhatsApp Business arama algılama + mesaj filtresi | `call/WhatsAppListener.kt` |
| Groq Whisper → Gemini yedekli deşifre, “Ben / Karşı Taraf” ayırma, özet, toplu analiz | `ai/AiClient.kt` |
| Room veritabanı (başlık, yol, tarih, süre, metin, özet, WhatsApp, senkron durumu…) | `data/` |
| Şifre, ayarlar, API anahtarları | `data/Prefs.kt`, `ui/SettingsScreen.kt`, `ui/LockScreens.kt` |
| Liste/oynatma, ayrıntı, yeniden adlandırma, silme (onaylı), paylaşma (FileProvider) | `ui/RecordingsScreen.kt`, `ui/DetailScreen.kt` |
| Toplu Görüşme Analiz Raporu | `ui/AnalysisScreen.kt` |
| İzin kontrol ekranı | `ui/PermissionsScreen.kt` |

## Bilinmesi gereken sınırlamalar
- **Android 10+ görüşme sesi kısıtı:** Google, üçüncü taraf uygulamaların arama sesini (VOICE_CALL) doğrudan
  almasını engeller. Uygulama mikrofonu kaydeder; kendi sesiniz net çıkar, karşı tarafın sesi için
  **hoparlör** kullanmak veya **erişilebilirlik servisini** açmak gerekir. Bazı MIUI/HyperOS sürümlerinde
  WhatsApp görüşmesi sırasında mikrofon sessiz kayıt verebilir.
- **Bildirim gizleme:** Android, ön plan servisini bildirimsiz çalıştırmaya izin vermez; “bildirim kapalı” seçeneği
  bildirimi en düşük öncelikli/sessiz kanala alır.
- **Groq dosya sınırı:** 25 MB (orta kalitede ~25 dakika). Gemini yedeği için ~18 MB.
- AI model adları (`llama-3.3-70b-versatile`, `gemini-2.5-flash`) Ayarlar’dan değiştirilebilir; sağlayıcı bir modeli
  kaldırırsa yenisini yazmanız yeterli.
- Telefon görüşmesi kaydı bazı ülkelerde karşı tarafın rızasını gerektirir; yasal sorumluluk kullanıcıdadır.
