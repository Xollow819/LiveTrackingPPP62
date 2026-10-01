package com.ppp62.livetracking

import android.os.Bundle
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.ppp62.livetracking.service.SyncWorker
import com.ppp62.livetracking.ui.PPP62App
import com.ppp62.livetracking.ui.theme.VenzaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SyncWorker.schedule(this)
        lifecycleScope.launch { (application as PPP62Application).backend.handleAuthIntent(intent) }
        setContent { VenzaTheme { Surface { PPP62App() } } }
    }
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent);setIntent(intent)
        lifecycleScope.launch{(application as PPP62Application).backend.handleAuthIntent(intent)}
    }
}
