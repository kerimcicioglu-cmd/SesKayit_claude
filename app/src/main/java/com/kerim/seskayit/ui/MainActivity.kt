package com.kerim.seskayit.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SesKayitTheme {
                AppRoot(vm)
            }
        }
    }
}

@Composable
fun SesKayitTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        // Android 12+ : telefonun duvar kağıdından türeyen Material You renkleri (Xiaomi'de şık durur)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = Color(0xFFB0C6FF), onPrimary = Color(0xFF002D6E),
            primaryContainer = Color(0xFF26457C), onPrimaryContainer = Color(0xFFD9E2FF),
            secondary = Color(0xFF7FD3C4), tertiary = Color(0xFFF0B8D0), error = Color(0xFFFFB4AB),
        )
        else -> lightColorScheme(
            primary = Color(0xFF2B5CB8), onPrimary = Color.White,
            primaryContainer = Color(0xFFD9E2FF), onPrimaryContainer = Color(0xFF001A43),
            secondary = Color(0xFF006A60), tertiary = Color(0xFF8B4A68), error = Color(0xFFC62828),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private enum class Stage { FIRST_RUN, LOCKED, MAIN }

@Composable
fun AppRoot(vm: AppViewModel) {
    val prefs = vm.prefs
    var stage by rememberSaveable {
        mutableStateOf(
            when {
                !prefs.firstRunDone -> Stage.FIRST_RUN
                prefs.hasPassword -> Stage.LOCKED
                else -> Stage.MAIN
            }
        )
    }
    val context = LocalContext.current
    val message by vm.messages.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.consumeMessage()
        }
    }

    when (stage) {
        Stage.FIRST_RUN -> SetPasswordScreen(
            title = "Hoş geldiniz",
            subtitle = "Kayıtlarınızı korumak için bir uygulama şifresi belirleyin.",
            allowSkip = true,
            onDone = { pw ->
                if (pw != null) prefs.setPassword(pw)
                prefs.firstRunDone = true
                stage = Stage.MAIN
            },
        )
        Stage.LOCKED -> UnlockScreen(check = prefs::checkPassword, onUnlocked = { stage = Stage.MAIN })
        Stage.MAIN -> MainScaffold(vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }

    val openId = detailId
    if (openId != null) {
        BackHandler { detailId = null }
        DetailScreen(vm, openId, onBack = { detailId = null })
        return
    }

    val tabs = listOf(
        "Kayıtlar" to Icons.Filled.Mic,
        "Analiz" to Icons.Filled.Assessment,
        "Ayarlar" to Icons.Filled.Settings,
        "İzinler" to Icons.Filled.VerifiedUser,
    )
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tabs[tab].first, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        alwaysShowLabel = false,
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> RecordingsScreen(vm, onOpen = { detailId = it })
                1 -> AnalysisScreen(vm)
                2 -> SettingsScreen(vm)
                else -> PermissionsScreen()
            }
        }
    }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
