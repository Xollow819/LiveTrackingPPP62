package com.ppp62.livetracking.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ppp62.livetracking.ui.BackendViewModel
import com.ppp62.livetracking.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(bvm:BackendViewModel,onBack:()->Unit) {
    val context=LocalContext.current
    val prefs=remember{context.getSharedPreferences("appearance",Context.MODE_PRIVATE)}
    var reduced by remember { mutableStateOf(prefs.getBoolean("opaque",false)) }
    Scaffold(topBar={TopAppBar(title={Text("Preferences")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")}})}) {pad->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)){
            Text("Make it yours.",style=MaterialTheme.typography.headlineLarge)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text("Reduce transparency",style=MaterialTheme.typography.titleMedium);Text("Solid panels for easier reading",style=MaterialTheme.typography.bodySmall)};Switch(reduced,{reduced=it;prefs.edit().putBoolean("opaque",it).apply()})}
            if(bvm.lecturerAuthenticated.value) OutlinedButton(onClick={bvm.signOut()}){Text("Sign out of lecturer account")}
            HorizontalDivider()
            Text("Connection",style=MaterialTheme.typography.titleLarge)
            Text(if(bvm.backendEnabled.value) "Supabase is configured" else "The app builder has not configured Supabase for this installation.")
            bvm.connectionMessage.value?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            Text("Satellite imagery",style=MaterialTheme.typography.titleLarge)
            Text("EOxCloudless 2025 by EOX IT Services GmbH. Modified Copernicus Sentinel data. CC BY-NC-SA 4.0, for educational and non-commercial use.",style=MaterialTheme.typography.bodySmall)
            Text("Location sharing starts only when you choose Start sharing. Leave or complete a session to stop sharing.",style=MaterialTheme.typography.bodySmall)
            Text("Route planning",style=MaterialTheme.typography.titleLarge)
            Text("Road routes use OpenStreetMap through FOSSGIS. Only your lecturer’s route pins are sent for planning; your live student location is not sent to the routing service. A pin-to-pin path remains available offline.",style=MaterialTheme.typography.bodySmall)
        }
    }
}
