package com.kerim.seskayit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PasswordField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LockLayout(title: String, subtitle: String, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            content()
        }
    }
}

/** onDone(null) = şifresiz devam */
@Composable
fun SetPasswordScreen(title: String, subtitle: String, allowSkip: Boolean, onDone: (String?) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LockLayout(title, subtitle) {
        PasswordField(pw, { pw = it; error = null }, "Şifre")
        Spacer(Modifier.height(8.dp))
        PasswordField(pw2, { pw2 = it; error = null }, "Şifre (tekrar)", isError = error != null)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            error = when {
                pw.length < 4 -> "Şifre en az 4 karakter olmalı"
                pw != pw2 -> "Şifreler eşleşmiyor"
                else -> null
            }
            if (error == null) onDone(pw)
        }, modifier = Modifier.fillMaxWidth()) { Text("Şifreyi Belirle") }
        if (allowSkip) {
            TextButton(onClick = { onDone(null) }) { Text("Şifresiz devam et") }
        }
    }
}

@Composable
fun UnlockScreen(check: (String) -> Boolean, onUnlocked: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    LockLayout("Ses Kayıt", "Devam etmek için uygulama şifrenizi girin.") {
        PasswordField(pw, { pw = it; error = false }, "Şifre", isError = error)
        if (error) Text("Şifre hatalı", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(16.dp))
        Button(onClick = { if (check(pw)) onUnlocked() else error = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Giriş")
        }
    }
}
