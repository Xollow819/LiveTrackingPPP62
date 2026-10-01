package com.ppp62.livetracking

import android.Manifest
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ppp62.livetracking.data.TrackingState
import com.ppp62.livetracking.service.LocationTrackingService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackingServiceTest {
    @Test fun serviceReportsRealStartPauseResumeFinishBeforeFirstFix() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        fun shell(command:String){ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use{it.readBytes()}}
        shell("pm grant ${context.packageName} ${Manifest.permission.ACCESS_COARSE_LOCATION}")
        shell("pm grant ${context.packageName} ${Manifest.permission.ACCESS_FINE_LOCATION}")
        fun awaitState(expected:TrackingState){
            val deadline=SystemClock.uptimeMillis()+10000
            while(LocationTrackingService.state.value!=expected&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(50)
            assertEquals(expected,LocationTrackingService.state.value)
        }
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{activity->ContextCompat.startForegroundService(activity,Intent(activity,LocationTrackingService::class.java)
                .putExtra(LocationTrackingService.EXTRA_SESSION,"service-test")
                .putExtra(LocationTrackingService.EXTRA_USER,"service-student")
                .putExtra(LocationTrackingService.EXTRA_NAME,"Ayu").putExtra(LocationTrackingService.EXTRA_TEAM,"Team A"))}
            try {
                awaitState(TrackingState.LIVE)
                context.startService(Intent(context,LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_PAUSE));awaitState(TrackingState.PAUSED)
                context.startService(Intent(context,LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_RESUME));awaitState(TrackingState.LIVE)
                context.startService(Intent(context,LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_FINISH));awaitState(TrackingState.FINISHED)
                assertFalse(context.getSharedPreferences("tracking_service",0).getBoolean("sharing",true))
            } finally {context.stopService(Intent(context,LocationTrackingService::class.java))}
        }
    }
}
