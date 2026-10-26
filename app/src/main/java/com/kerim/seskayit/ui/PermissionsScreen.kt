package com.kerim.seskayit.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kerim.seskayit.call.CallUtils

private data class PermItem(
    val title: String,
    val description: String,
    val granted: Boolean?, // null = denetlenemiyor
    val onRequest: () -> Unit,
)

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun tryStart(context: Context, vararg intents: Intent) {
    for (i in intents) {
        try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
        }
    }
    Toast.makeText(context, "Ayar ekranı açılamadı", Toast.LENGTH_SHORT).show()
}

@SuppressLint("BatteryLife")
@Composable
fun PermissionsScreen() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        refresh++
        val activity = context.findActivity()
        val permanentlyDenied = result.filterValues { !it }.keys.any { p ->
            activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, p)
        }
        if (permanentlyDenied) {
            Toast.makeText(context, "İzin kalıcı olarak reddedilmiş; uygulama ayarlarından verin", Toast.LENGTH_LONG).show()
            openAppSettings(context)
        }
    }

    // refresh her ON_RESUME'da artar; böylece ayarlardan dönünce durumlar yeniden denetlenir.
    val items = remember(refresh) { buildList {
        add(PermItem("Mikrofon", "Ses kaydı için zorunlu",
            CallUtils.has(context, Manifest.permission.RECORD_AUDIO)) {
            launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        })
        add(PermItem("Telefon durumu", "Aramaların başladığını/bittiğini algılamak için",
            CallUtils.has(context, Manifest.permission.READ_PHONE_STATE)) {
            launcher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.PROCESS_OUTGOING_CALLS))
        })
        add(PermItem("Arama kayıtlarına erişim", "Görüşülen numarayı kayda eklemek için",
            CallUtils.has(context, Manifest.permission.READ_CALL_LOG)) {
            launcher.launch(arrayOf(Manifest.permission.READ_CALL_LOG))
        })
        add(PermItem("Rehber erişimi", "Numarayı kişi adıyla eşleştirmek için",
            CallUtils.has(context, Manifest.permission.READ_CONTACTS)) {
            launcher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
        })
        add(PermItem("Bildirim izni", "Kayıt bildirimi göstermek için",
            if (Build.VERSION.SDK_INT >= 33) CallUtils.has(context, Manifest.permission.POST_NOTIFICATIONS) else true) {
            if (Build.VERSION.SDK_INT >= 33) launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        })
        add(PermItem("Diğer uygulamaların üzerinde göster (ÖNEMLİ)",
            "Otomatik kayıt için en kritik izin. Android 12+ bu izin olmadan arama sırasında mikrofonu arka planda açmaya izin vermez.",
            Settings.canDrawOverlays(context)) {
            tryStart(
                context,
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            )
        })
        add(PermItem("WhatsApp için bildirim erişimi", "WhatsApp aramalarını bildirimlerden algılamak için",
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
            tryStart(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        add(PermItem("Pil optimizasyonundan muafiyet", "Arka planda aramaları kaçırmamak için",
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) {
            tryStart(
                context,
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
        })
        add(PermItem("Erişilebilirlik servisi (önerilir)",
            "Görüşme sırasında Android'in mikrofonu uygulamaya vermesine yardımcı olur. Ayarlar > Yüklü uygulamalar > Ses Kayıt",
            CallUtils.isAccessibilityEnabled(context)) {
            tryStart(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        add(PermItem("Xiaomi: Otomatik başlatma",
            "MIUI/HyperOS'ta arka planda çalışabilmek için açın (denetlenemez)", null) {
            tryStart(
                context,
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
            )
        })
        add(PermItem("Xiaomi: Arka planda açılır pencere (Diğer izinler)",
            "Uygulama bilgisi > Diğer izinler > \"Arka planda çalışırken açılır pencere göster\" açık olmalı (HyperOS, denetlenemez)", null) {
            tryStart(
                context,
                Intent("miui.intent.action.APP_PERM_EDITOR")
                    .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    .putExtra("extra_pkgname", context.packageName),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
            )
        })
        add(PermItem("Xiaomi: Pil tasarrufu \"Kısıtlama yok\"",
            "Uygulama bilgisi > Pil tasarrufu > Kısıtlama yok (denetlenemez)", null) {
            openAppSettings(context)
        })
    } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("İzin Kontrolü", style = MaterialTheme.typography.titleLarge)
        val missing = items.count { it.granted == false }
        Text(
            if (missing == 0) "Gerekli tüm izinler verilmiş." else "$missing izin eksik.",
            color = if (missing == 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
        )
        Button(onClick = {
            launcher.launch(
                buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    add(Manifest.permission.READ_PHONE_STATE)
                    add(Manifest.permission.PROCESS_OUTGOING_CALLS)
                    add(Manifest.permission.READ_CALL_LOG)
                    add(Manifest.permission.READ_CONTACTS)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }.toTypedArray()
            )
        }, modifier = Modifier.fillMaxWidth()) { Text("Tüm Uygulama İzinlerini İste") }

        Card(Modifier.fillMaxWidth()) {
            Column {
                items.forEach { item ->
                    ListItem(
                        leadingContent = {
                            when (item.granted) {
                                true -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
                                false -> Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
                                null -> Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        headlineContent = { Text(item.title) },
                        supportingContent = { Text(item.description) },
                        trailingContent = {
                            if (item.granted != true) {
                                TextButton(onClick = item.onRequest) { Text(if (item.granted == null) "Aç" else "Ver") }
                            }
                        },
                    )
                }
            }
        }

        Text(
            "Not: Android 10 ve üzeri, üçüncü taraf uygulamaların görüşme sesini doğrudan almasını engeller. " +
                "Uygulama mikrofonu kaydeder; karşı tarafın sesinin net çıkması için hoparlör kullanmanız veya " +
                "erişilebilirlik servisini açmanız önerilir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
