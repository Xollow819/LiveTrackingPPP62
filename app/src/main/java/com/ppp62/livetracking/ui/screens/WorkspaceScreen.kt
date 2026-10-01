package com.ppp62.livetracking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ppp62.livetracking.ui.BackendViewModel
import com.ppp62.livetracking.ui.components.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(bvm:BackendViewModel,onBack:()->Unit,onNew:()->Unit) {
    val history by bvm.history.collectAsState()
    LaunchedEffect(Unit){bvm.refreshHistory()}
    Scaffold(topBar={TopAppBar(title={Text("Your workspace")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")}})}){pad->
        LazyColumn(Modifier.fillMaxSize().padding(pad),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            item{Text("Field sessions",style=MaterialTheme.typography.headlineLarge);Spacer(Modifier.height(12.dp));Button(onClick=onNew,modifier=Modifier.fillMaxWidth().height(56.dp)){Icon(Icons.Default.Add,null);Text("  New session")}}
            bvm.connectionMessage.value?.let{message->item{Text(message,color=MaterialTheme.colorScheme.error)}}
            if(history.isEmpty()) item{Text("Create a route for your next practical.")}
            items(history,key={it.id}){session->GlassCard(Modifier.fillMaxWidth(),onClick={bvm.openHistory(session)}){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(session.title,style=MaterialTheme.typography.titleLarge)
                Text("${session.code} · ${if(session.isActive) "Active" else "Completed"}",style=MaterialTheme.typography.labelMedium)
                Text("Open session",color=MaterialTheme.colorScheme.primary)
            }}}
        }
    }
}
