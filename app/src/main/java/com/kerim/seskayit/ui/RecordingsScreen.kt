package com.kerim.seskayit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kerim.seskayit.data.Recording
import com.kerim.seskayit.data.Source
import com.kerim.seskayit.recording.RecorderState
import com.kerim.seskayit.recording.RecordingService
import kotlinx.coroutines.delay
import java.util.Date

fun sourceIcon(rec: Recording): ImageVector = when (rec.source) {
    Source.PHONE -> Icons.Filled.Call
    Source.WHATSAPP -> Icons.Filled.Forum
    else -> Icons.Filled.Mic
}

@Composable
fun RecordingsScreen(vm: AppViewModel, onOpen: (Long) -> Unit) {
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
        item { RecorderCard() }
        if (recordings.isEmpty()) {
            item {
                Text(
                    "Henüz kayıt yok.",
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(recordings, key = { it.id }) { rec ->
            val playing = playback.id == rec.id
            ListItem(
                modifier = Modifier.clickable { vm.togglePlay(rec) },
                leadingContent = {
                    Icon(
                        if (playing) Icons.Filled.Pause else sourceIcon(rec),
                        contentDescription = null,
                        tint = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                headlineContent = { Text(rec.title, maxLines = 2) },
                supportingContent = {
                    val extra = buildString {
                        if (!rec.transcript.isNullOrBlank()) append(" • Metin")
                        if (!rec.summary.isNullOrBlank()) append(" • Özet")
                    }
                    val time = if (playing) "${AppViewModel.formatDuration(playback.positionMs.toLong())} / " else ""
                    Text(
                        "${AppViewModel.dateFmt.format(Date(rec.createdAt))} • $time${AppViewModel.formatDuration(rec.durationMs)}$extra"
                    )
                },
                trailingContent = {
                    IconButton(onClick = { onOpen(rec.id) }) {
                        Icon(
                            if (rec.transcript.isNullOrBlank()) Icons.Filled.ChevronRight else Icons.Filled.Description,
                            contentDescription = "Ayrıntılar"
                        )
                    }
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun RecorderCard() {
    val context = LocalContext.current
    val state by RecorderState.state.collectAsStateWithLifecycle()
    var elapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.isRecording, state.startedAt) {
        while (state.isRecording) {
            elapsed = SystemClock.elapsedRealtime() - state.startedAt
            delay(200)
        }
        elapsed = 0
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) RecordingService.start(context, Source.MANUAL)
        else Toast.makeText(context, "Kayıt için mikrofon izni gerekli", Toast.LENGTH_LONG).show()
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                AppViewModel.formatDuration(elapsed) + "." + ((elapsed % 1000) / 100),
                fontSize = 44.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light,
            )
            Text(
                when {
                    !state.isRecording -> "Kayda başlamak için dokunun"
                    state.source == Source.PHONE -> "Telefon görüşmesi kaydediliyor" + (state.label?.let { ": $it" } ?: "")
                    state.source == Source.WHATSAPP -> "WhatsApp araması kaydediliyor" + (state.label?.let { ": $it" } ?: "")
                    else -> "Kaydediliyor…"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Box {
                FilledIconButton(
                    onClick = {
                        if (state.isRecording) {
                            RecordingService.stop(context)
                        } else {
                            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED
                            ) RecordingService.start(context, Source.MANUAL)
                            else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (state.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                ) {
                    Icon(
                        if (state.isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (state.isRecording) "Durdur" else "Kaydet",
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun PlayIcon(playing: Boolean) = Icon(
    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
    contentDescription = if (playing) "Durdur" else "Oynat",
)
