package com.kerim.seskayit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
private fun sourceTint(source: String): Color = when (source) {
    Source.PHONE -> MaterialTheme.colorScheme.primary
    Source.WHATSAPP -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

@Composable
fun RecordingsScreen(vm: AppViewModel, onOpen: (Long) -> Unit) {
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { RecorderCard() }
        if (recordings.isNotEmpty()) {
            item {
                Text(
                    "Kayıtlar (${recordings.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
        } else {
            item { EmptyState() }
        }
        items(recordings, key = { it.id }) { rec ->
            RecordingRow(
                rec = rec,
                playing = playback.id == rec.id,
                positionMs = if (playback.id == rec.id) playback.positionMs.toLong() else 0L,
                onTogglePlay = { vm.togglePlay(rec) },
                onOpen = { onOpen(rec.id) },
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.GraphicEq, null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Text(
            "Henüz kayıt yok",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Yukarıdaki butona dokunarak kayda başlayın.\nTelefon ve WhatsApp aramaları ayarlardan otomatik kaydedilebilir.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun RecordingRow(
    rec: Recording,
    playing: Boolean,
    positionMs: Long,
    onTogglePlay: () -> Unit,
    onOpen: () -> Unit,
) {
    val tint = sourceTint(rec.source)
    Surface(
        onClick = onTogglePlay,
        shape = RoundedCornerShape(18.dp),
        color = if (playing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else sourceIcon(rec),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    rec.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
                Spacer(Modifier.height(3.dp))
                val time = if (playing) "${AppViewModel.formatDuration(positionMs)} / " else ""
                Text(
                    "${AppViewModel.dateFmt.format(Date(rec.createdAt))}  ·  $time${AppViewModel.formatDuration(rec.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!rec.transcript.isNullOrBlank() || !rec.summary.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!rec.transcript.isNullOrBlank()) Badge("Metin", Icons.Filled.Description)
                        if (!rec.summary.isNullOrBlank()) Badge("Özet", Icons.Filled.Summarize)
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, "Ayrıntılar",
                modifier = Modifier.clip(CircleShape).clickable(onClick = onOpen).padding(6.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Badge(label: String, icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(icon, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
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
            delay(100)
        }
        elapsed = 0
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) RecordingService.start(context, Source.MANUAL)
        else Toast.makeText(context, "Kayıt için mikrofon izni gerekli", Toast.LENGTH_LONG).show()
    }

    val recording = state.isRecording
    val gradient = if (recording) {
        Brush.linearGradient(listOf(Color(0xFFC62F3B), Color(0xFF8E1B4E)))
    } else {
        Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
        )
    }

    Surface(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().background(gradient).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val centis = ((elapsed % 1000) / 100)
            Text(
                "${AppViewModel.formatDuration(elapsed)}.$centis",
                fontSize = 46.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Light,
                color = Color.White,
            )
            Text(
                when {
                    !recording -> "Kayda başlamak için dokunun"
                    state.source == Source.PHONE -> "Telefon görüşmesi" + (state.label?.let { ": $it" } ?: "")
                    state.source == Source.WHATSAPP -> "WhatsApp araması" + (state.label?.let { ": $it" } ?: "")
                    else -> "Kaydediliyor…"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(6.dp))
            RecordButton(recording = recording) {
                if (recording) {
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
            }
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (recording) 1.35f else 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val alpha by pulse.animateFloat(
        initialValue = if (recording) 0.35f else 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "alpha",
    )
    val btnColor by animateColorAsState(if (recording) Color.White else Color.White, label = "btn")

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
        // Kayıt sırasında dışa doğru genişleyen nabız halkası
        Box(
            Modifier.size(88.dp).scale(scale).clip(CircleShape)
                .background(Color.White.copy(alpha = alpha))
        )
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = btnColor,
            modifier = Modifier.size(80.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (recording) "Durdur" else "Kaydet",
                    modifier = Modifier.size(38.dp),
                    tint = if (recording) Color(0xFFC62F3B) else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun PlayIcon(playing: Boolean) = Icon(
    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
    contentDescription = if (playing) "Durdur" else "Oynat",
)
