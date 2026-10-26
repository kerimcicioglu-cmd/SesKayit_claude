package com.kerim.seskayit.ui

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kerim.seskayit.data.Prefs
import com.kerim.seskayit.data.Quality

@Composable
private fun SectionTitle(text: String) = Text(
    text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 8.dp),
)

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) { { Text(subtitle) } } else null,
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun SecretField(label: String, initial: String, onSave: (String) -> Unit) {
    val context = LocalContext.current
    var value by remember { mutableStateOf(initial) }
    var visible by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(label) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Göster")
                }
            },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            onSave(value)
            Toast.makeText(context, "Kaydedildi", Toast.LENGTH_SHORT).show()
        }) { Text("Kaydet") }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val prefs = vm.prefs
    val context = LocalContext.current
    val contacts by vm.autoContacts.collectAsStateWithLifecycle()

    var autoPhone by remember { mutableStateOf(prefs.autoRecordPhone) }
    var autoWa by remember { mutableStateOf(prefs.autoRecordWhatsApp) }
    var recordAll by remember { mutableStateOf(prefs.recordAllCalls) }
    var showNotif by remember { mutableStateOf(prefs.showNotification) }
    var quality by remember { mutableStateOf(prefs.quality) }
    var showAddDialog by remember { mutableStateOf(false) }
    var passwordDialog by remember { mutableStateOf<String?>(null) } // "set" | "change" | "reset"
    var hasPassword by remember { mutableStateOf(prefs.hasPassword) }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) vm.addContact(c.getString(0) ?: "", c.getString(1) ?: "")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Ayarlar", style = MaterialTheme.typography.titleLarge)

        SectionTitle("Otomatik Kayıt")
        SwitchRow("Telefon aramalarını kaydet", "Gelen/giden GSM aramaları", autoPhone) {
            autoPhone = it; prefs.autoRecordPhone = it
        }
        SwitchRow("WhatsApp aramalarını kaydet", "WhatsApp ve WhatsApp Business (bildirim erişimi gerekir)", autoWa) {
            autoWa = it; prefs.autoRecordWhatsApp = it
        }
        SwitchRow(
            "Tüm Çağrıları Otomatik Kaydet",
            if (recordAll) "Tüm aramalar kaydedilir" else "Yalnızca aşağıdaki listedeki kişiler kaydedilir",
            recordAll,
        ) { recordAll = it; prefs.recordAllCalls = it }
        SwitchRow(
            "Kayıt bildirimi göster",
            "Kapalıyken bildirim en düşük öncelikte, sessiz gösterilir (Android ön plan servisi için zorunlu tutar)",
            showNotif,
        ) { showNotif = it; prefs.showNotification = it }

        SectionTitle("Kaydedilecek Kişiler")
        Card(Modifier.fillMaxWidth()) {
            Column {
                if (contacts.isEmpty()) {
                    Text(
                        "Liste boş. \"Tüm Çağrıları Otomatik Kaydet\" kapalıysa yalnızca bu listedekiler kaydedilir.",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                    )
                }
                contacts.forEach { c ->
                    ListItem(
                        headlineContent = { Text(c.name) },
                        supportingContent = { if (c.number.isNotBlank()) Text(c.number) },
                        trailingContent = {
                            IconButton(onClick = { vm.removeContact(c) }) { Icon(Icons.Filled.Close, "Kaldır") }
                        },
                    )
                    HorizontalDivider()
                }
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        runCatching {
                            pickContact.launch(
                                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                            )
                        }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Contacts, null); Spacer(Modifier.width(6.dp)); Text("Rehberden")
                    }
                    OutlinedButton(onClick = { showAddDialog = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Elle Ekle")
                    }
                }
            }
        }

        SectionTitle("Kayıt Kalitesi")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Quality.entries.forEach { q ->
                FilterChip(
                    selected = quality == q,
                    onClick = { quality = q; prefs.quality = q },
                    label = { Text(q.label) },
                )
            }
        }
        Text(
            "${quality.bitRate / 1000} kbps • ${quality.sampleRate} Hz • ${if (quality.channels == 1) "mono" else "stereo"} • AAC / .m4a",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("Yapay Zekâ")
        Text(
            "Deşifre için Groq Whisper (whisper-large-v3-turbo) kullanılır; Groq başarısız olursa Gemini denenir. " +
                "Anahtarlar yalnızca bu cihazda saklanır. Groq anahtarı: console.groq.com, Gemini anahtarı: aistudio.google.com",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecretField("Groq API anahtarı", prefs.groqKey) { prefs.groqKey = it }
        SecretField("Gemini API anahtarı (yedek)", prefs.geminiKey) { prefs.geminiKey = it }
        ModelField("Groq sohbet modeli", prefs.groqChatModel, Prefs.DEFAULT_GROQ_CHAT_MODEL) { prefs.groqChatModel = it }
        ModelField("Gemini modeli", prefs.geminiModel, Prefs.DEFAULT_GEMINI_MODEL) { prefs.geminiModel = it }

        SectionTitle("Güvenlik")
        if (hasPassword) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { passwordDialog = "change" }, modifier = Modifier.weight(1f)) { Text("Şifreyi Değiştir") }
                OutlinedButton(onClick = { passwordDialog = "reset" }, modifier = Modifier.weight(1f)) { Text("Şifreyi Kaldır") }
            }
        } else {
            OutlinedButton(onClick = { passwordDialog = "set" }, modifier = Modifier.fillMaxWidth()) { Text("Uygulama Şifresi Belirle") }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var number by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Kişi / Numara Ekle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Ad (isteğe bağlı)") }, singleLine = true)
                    OutlinedTextField(
                        number, { number = it }, label = { Text("Telefon numarası") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addContact(name, number); showAddDialog = false
                }, enabled = number.isNotBlank() || name.isNotBlank()) { Text("Ekle") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("İptal") } },
        )
    }

    passwordDialog?.let { mode ->
        var current by remember { mutableStateOf("") }
        var pw by remember { mutableStateOf("") }
        var pw2 by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { passwordDialog = null },
            title = {
                Text(
                    when (mode) {
                        "set" -> "Şifre Belirle"
                        "change" -> "Şifreyi Değiştir"
                        else -> "Şifreyi Kaldır"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (mode != "set") PasswordField(current, { current = it; error = null }, "Mevcut şifre")
                    if (mode != "reset") {
                        PasswordField(pw, { pw = it; error = null }, "Yeni şifre")
                        PasswordField(pw2, { pw2 = it; error = null }, "Yeni şifre (tekrar)")
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    error = when {
                        mode != "set" && !prefs.checkPassword(current) -> "Mevcut şifre hatalı"
                        mode != "reset" && pw.length < 4 -> "Şifre en az 4 karakter olmalı"
                        mode != "reset" && pw != pw2 -> "Şifreler eşleşmiyor"
                        else -> null
                    }
                    if (error == null) {
                        if (mode == "reset") prefs.clearPassword() else prefs.setPassword(pw)
                        hasPassword = prefs.hasPassword
                        passwordDialog = null
                        Toast.makeText(context, "Şifre güncellendi", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { passwordDialog = null }) { Text("İptal") } },
        )
    }
}

@Composable
private fun ModelField(label: String, initial: String, default: String, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it; onSave(it) },
        label = { Text(label) },
        placeholder = { Text(default) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
