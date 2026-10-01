package com.ppp62.livetracking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ppp62.livetracking.PPP62Application
import com.ppp62.livetracking.ui.BackendViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackendSettingsScreen(bvm: BackendViewModel = viewModel(), onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by bvm.backendEnabled
    val testing by bvm.testingConnection
    val message by bvm.connectionMessage

    var url by rememberSaveable { mutableStateOf("") }
    var key by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var loaded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!loaded) {
            val app = context.applicationContext as PPP62Application
            url = app.backendConfig.currentUrl()
            key = app.backendConfig.currentAnonKey()
            loaded = true
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Online backend", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (enabled) Icons.Default.CloudDone else Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(if (enabled) "Online mode ready" else "Offline mode", fontWeight = FontWeight.Bold)
                        Text(
                            if (enabled) "Positions, sessions and photos sync through your free Supabase project."
                            else "The app works fully offline. Add your free Supabase project below to enable live multi-device tracking.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Text("Supabase project", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(url, { url = it }, label = { Text("Project URL") }, placeholder = { Text("https://xyzcompany.supabase.co") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !testing)
            OutlinedTextField(
                key, { key = it }, label = { Text("Anon public key") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !testing,
                visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } }
            )

            Button(
                onClick = {
                    scope.launch {
                        bvm.saveConfig(url, key) { _, _ -> }
                    }
                },
                modifier = Modifier.fillMaxWidth(), enabled = !testing && url.isNotBlank() && key.isNotBlank()
            ) {
                if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.CloudUpload, null)
                Spacer(Modifier.width(8.dp)); Text(if (testing) "Testing…" else "Save & test connection")
            }

            if (enabled) {
                OutlinedButton(onClick = { bvm.clearConfig() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CloudOff, null); Spacer(Modifier.width(8.dp)); Text("Disconnect backend")
                }
            }

            message?.let {
                AssistChip(onClick = { bvm.clearConnectionMessage() }, label = { Text(it) }, leadingIcon = { Icon(Icons.Default.Info, null) })
            }

            HorizontalDivider()
            Text("How to get these (free, no card):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "1. Create a free project at supabase.com\n" +
                "2. Open backend/supabase/schema.sql from this repo and run it in the Supabase SQL Editor\n" +
                "3. In Authentication → Providers, enable “Allow anonymous sign-ins”\n" +
                "4. Copy the Project URL and anon public key from Project Settings → API",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
