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
import com.ppp62.livetracking.data.JourneyStore
import com.ppp62.livetracking.service.LocationTrackingService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackingServiceTest {
    @Test fun serviceReportsRealStartPauseResumeFinishBeforeFirstFix() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val session="service-test-${java.util.UUID.randomUUID()}"
        val store=JourneyStore(context)
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
                .putExtra(LocationTrackingService.EXTRA_SESSION,session)
                .putExtra(LocationTrackingService.EXTRA_USER,"service-student")
                .putExtra(LocationTrackingService.EXTRA_NAME,"Ayu").putExtra(LocationTrackingService.EXTRA_TEAM,"Team A"))}
            try {
                awaitState(TrackingState.LIVE)
                val started=store.load(session,"service-student")
                assertTrue(started.startedAt>0); assertTrue(started.running)
                SystemClock.sleep(1100)
                context.startService(Intent(context,LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_PAUSE));awaitState(TrackingState.PAUSED)
                val paused=store.load(session,"service-student")
                assertFalse(paused.running);assertTrue(paused.accumulatedMillis>=1000)
                SystemClock.sleep(300)
                assertEquals(paused,store.load(session,"service-student"))
                context.startService(Intent(context,LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_RESUME));awaitState(TrackingState.LIVE)
                assertEquals(started.startedAt,store.load(session,"service-student").startedAt)
                context.startService(Intent(context,LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_FINISH));awaitState(TrackingState.FINISHED)
                val ended=store.load(session,"service-student")
                assertTrue(ended.finishedAt>0);assertFalse(ended.running)
                assertEquals(ended,JourneyStore(context).load(session,"service-student"))
                assertEquals(0,store.load(session,"another-student").startedAt)
                assertFalse(context.getSharedPreferences("tracking_service",0).getBoolean("sharing",true))
            } finally {context.stopService(Intent(context,LocationTrackingService::class.java))}
        }
    }
}
