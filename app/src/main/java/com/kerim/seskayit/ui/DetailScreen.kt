package com.kerim.seskayit.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kerim.seskayit.data.Recording
import com.kerim.seskayit.data.Source
import java.io.File
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit) {
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val busyMap by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val rec = recordings.firstOrNull { it.id == id }
    if (rec == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val busy = busyMap[rec.id]
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kayıt Ayrıntısı") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                },
                actions = {
                    IconButton(onClick = { showRename = true }) { Icon(Icons.Filled.Edit, "Yeniden adlandır") }
                    IconButton(onClick = { shareRecording(context, rec) }) { Icon(Icons.Filled.Share, "Paylaş") }
                    IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, "Sil") }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(rec.title, style = MaterialTheme.typography.titleLarge)
            Text(
                buildString {
                    append(AppViewModel.dateFmt.format(Date(rec.createdAt)))
                    append(" • ").append(AppViewModel.formatDuration(rec.durationMs))
                    append(" • ").append(
                        when (rec.source) {
                            Source.PHONE -> "Telefon araması"
                            Source.WHATSAPP -> "WhatsApp araması"
                            else -> "Manuel kayıt"
                        }
                    )
                    rec.phoneNumber?.let { append("\nNumara: ").append(it) }
                    rec.contactName?.let { append("\nKişi: ").append(it) }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Oynatıcı
            val playing = playback.id == rec.id
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(onClick = { vm.togglePlay(rec) }) { PlayIcon(playing) }
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = if (playing && playback.durationMs > 0) (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f) else 0f,
                        onValueChange = { if (playing) vm.seekTo(it) },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (playing) AppViewModel.formatDuration(playback.positionMs.toLong())
                        else AppViewModel.formatDuration(rec.durationMs)
                    )
                }
            }

            if (busy != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(busy)
                }
            }

            // Deşifre
            Section(
                title = "Tam Metin",
                icon = Icons.Filled.Description,
                text = rec.transcript,
                emptyAction = "Deşifre Et (Tam Metin)",
                redoAction = "Metni Yeniden Deşifre Et",
                enabled = busy == null,
                onAction = { vm.transcribe(rec) },
            )

            // Özet
            Section(
                title = "AI Özeti",
                icon = Icons.Filled.Article,
                text = rec.summary,
                emptyAction = "Görüşmeyi Özetle",
                redoAction = "Özeti Yeniden Oluştur",
                enabled = busy == null,
                onAction = { vm.summarize(rec) },
            )
        }
    }

    if (showRename) {
        var title by remember { mutableStateOf(rec.title) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Yeniden Adlandır") },
            text = { OutlinedTextField(title, { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = { vm.rename(rec, title); showRename = false }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("İptal") } },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Kaydı Sil") },
            text = { Text("\"${rec.title}\" kaydı ve ses dosyası cihazdan kalıcı olarak silinecek. Emin misiniz?") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.delete(rec)
                    onBack()
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun Section(
    title: String,
    icon: ImageVector,
    text: String?,
    emptyAction: String,
    redoAction: String,
    enabled: Boolean,
    onAction: () -> Unit,
) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!text.isNullOrBlank()) {
                    IconButton(onClick = { copyText(context, title, text) }) { Icon(Icons.Filled.ContentCopy, "Kopyala") }
                }
            }
            if (text.isNullOrBlank()) {
                Button(onClick = onAction, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(emptyAction) }
            } else {
                SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium) }
                OutlinedButton(onClick = onAction, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(redoAction)
                }
            }
        }
    }
}

fun copyText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Panoya kopyalandı", Toast.LENGTH_SHORT).show()
}

fun shareText(context: Context, title: String, text: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(i, title))
}

fun shareRecording(context: Context, rec: Recording) {
    val file = File(rec.filePath)
    if (!file.exists()) {
        Toast.makeText(context, "Ses dosyası bulunamadı", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val i = Intent(Intent.ACTION_SEND)
        .setType("audio/mp4")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, rec.title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(i, "Kaydı paylaş"))
}
