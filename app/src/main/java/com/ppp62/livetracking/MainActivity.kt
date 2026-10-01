package com.ppp62.livetracking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.ppp62.livetracking.service.SyncWorker
import com.ppp62.livetracking.ui.PPP62App
import com.ppp62.livetracking.ui.theme.VenzaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncWorker.schedule(this)
        setContent { VenzaTheme { Surface { PPP62App() } } }
    }
}
