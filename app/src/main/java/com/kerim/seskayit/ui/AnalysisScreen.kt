package com.kerim.seskayit.ui

import android.app.DatePickerDialog
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Calendar
import java.util.Date

private fun startOfDay(t: Long) = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun endOfDay(t: Long) = startOfDay(t) + 24L * 3600 * 1000 - 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val bulk by vm.bulk.collectAsStateWithLifecycle()

    var name by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableLongStateOf(startOfDay(System.currentTimeMillis() - 30L * 24 * 3600 * 1000)) }
    var end by rememberSaveable { mutableLongStateOf(endOfDay(System.currentTimeMillis())) }
    var menuOpen by remember { mutableStateOf(false) }

    val names = remember(recordings) {
        recordings.mapNotNull { it.contactName?.takeIf { n -> n.isNotBlank() } }.distinct().sorted()
    }
    val suggestions = names.filter { name.isBlank() || it.contains(name, ignoreCase = true) }
    val matches = remember(recordings, name, start, end) { vm.matchingRecordings(name, start, end) }

    fun pickDate(initial: Long, onPicked: (Long) -> Unit) {
        val c = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(context, { _, y, m, d ->
            onPicked(Calendar.getInstance().apply { set(y, m, d, 12, 0, 0) }.timeInMillis)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Kayıtları Analiz Et ve Özetle", style = MaterialTheme.typography.titleLarge)
        Text(
            "Belirli bir kişiyle belirli tarihler arasında yapılan görüşmeleri yapay zekâ ile birlikte analiz eder.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ExposedDropdownMenuBox(expanded = menuOpen && suggestions.isNotEmpty(), onExpandedChange = { menuOpen = it }) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; menuOpen = true },
                label = { Text("Kişi Adı (veya numara)") },
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
            )
            ExposedDropdownMenu(expanded = menuOpen && suggestions.isNotEmpty(), onDismissRequest = { menuOpen = false }) {
                suggestions.take(8).forEach { s ->
                    DropdownMenuItem(text = { Text(s) }, onClick = { name = s; menuOpen = false })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDate(start) { start = startOfDay(it) } }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.DateRange, null)
                Spacer(Modifier.width(6.dp))
                Column {
                    Text("Başlangıç Tarihi", style = MaterialTheme.typography.labelSmall)
                    Text(AppViewModel.dayFmt.format(Date(start)))
                }
            }
            OutlinedButton(onClick = { pickDate(end) { end = endOfDay(it) } }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.DateRange, null)
                Spacer(Modifier.width(6.dp))
                Column {
                    Text("Bitiş Tarihi", style = MaterialTheme.typography.labelSmall)
                    Text(AppViewModel.dayFmt.format(Date(end)))
                }
            }
        }

        Text(
            "Eşleşen kayıt: ${matches.size} (deşifresi olmayan: ${matches.count { it.transcript.isNullOrBlank() }})",
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(
            onClick = { vm.bulkAnalyze(name, start, end) },
            enabled = !bulk.running && matches.isNotEmpty() && start <= end,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Kayıtları Analiz Et ve Özetle") }

        if (bulk.running) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(bulk.progress ?: "Çalışıyor…")
            }
        }

        bulk.error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(it, Modifier.padding(16.dp))
            }
        }

        bulk.report?.let { report ->
            val title = "Toplu Görüşme Analiz Raporu"
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { copyText(context, title, report) }) { Icon(Icons.Filled.ContentCopy, "Kopyala") }
                        IconButton(onClick = { shareText(context, title, report) }) { Icon(Icons.Filled.Share, "Paylaş") }
                    }
                    Text(
                        "${name.ifBlank { "Tüm kişiler" }} • ${AppViewModel.dayFmt.format(Date(start))} – ${AppViewModel.dayFmt.format(Date(end))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer { Text(report, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}
