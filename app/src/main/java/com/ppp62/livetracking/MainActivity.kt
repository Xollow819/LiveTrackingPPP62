package com.ppp62.livetracking

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.ppp62.livetracking.service.SyncWorker
import com.ppp62.livetracking.ui.PPP62App
import com.ppp62.livetracking.ui.theme.PPP62Theme

class MainActivity : ComponentActivity() {
    private val backgroundLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) maybeRequestBackgroundLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissions.launch(buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray())
        SyncWorker.schedule(this)
        setContent { PPP62Theme { Surface { PPP62App() } } }
    }

    /**
     * Android 10+ requires ACCESS_BACKGROUND_LOCATION to be requested separately,
     * after foreground location is granted, for tracking to continue with the screen off.
     */
    private fun maybeRequestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) return
        AlertDialog.Builder(this)
            .setTitle("Background location")
            .setMessage("Choose \"Allow all the time\" so live tracking keeps working with the screen off during a practical session.")
            .setPositiveButton("Continue") { _, _ -> backgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
            .setNegativeButton("Not now", null)
            .show()
    }
}
