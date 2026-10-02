package com.ppp62.livetracking

import android.Manifest
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ppp62.livetracking.data.*
import com.ppp62.livetracking.data.remote.SessionRow
import com.ppp62.livetracking.service.LocationTrackingService
import com.ppp62.livetracking.ui.*
import com.ppp62.livetracking.ui.components.LocalGlassState
import com.ppp62.livetracking.ui.screens.StudentScreen
import com.ppp62.livetracking.ui.theme.VenzaTheme
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

/** Synthetic local fixture; run on an emulator, never on a student's live device. */
@RunWith(AndroidJUnit4::class)
class StudentJourneyTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun find(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.text?.toString() == text) return root
        for (i in 0 until root.childCount) find(root.getChild(i), text)?.let { return it }
        return null
    }
    private fun awaitText(text: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            find(instrumentation.uiAutomation.rootInActiveWindow, text)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Missing dashboard text: $text")
    }
    private fun click(text: String) {
        var node: AccessibilityNodeInfo? = awaitText(text)
        while (node != null) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            node = node.parent
        }
        error("Cannot click $text")
    }
    private fun map(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) map(view.getChildAt(i))?.let { return it }
        return null
    }
    @Test fun studentRecordsOnlyDepartureAndArrivalAndKeepsMapAndTimerAcrossTabs() {
        val app = instrumentation.targetContext.applicationContext as PPP62Application
        val session = "student-journey-test-${java.util.UUID.randomUUID()}"
        val user = "journey-student"
        val cps = listOf(CheckpointEntity("$session-end", session, "Arrival", -6.19, 106.84, orderIndex=3, instructions="End", requiresPhoto=false),
            CheckpointEntity("$session-start", session, "Departure", -6.175, 106.83, orderIndex=1, instructions="Start", requiresPhoto=false),
            CheckpointEntity("$session-middle", session, "Middle stop", -6.18, 106.835, orderIndex=2, instructions="Keep going", requiresPhoto=false))
        fun shell(command: String) { ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() } }
        shell("pm grant ${app.packageName} ${Manifest.permission.ACCESS_COARSE_LOCATION}")
        shell("pm grant ${app.packageName} ${Manifest.permission.ACCESS_FINE_LOCATION}")
        shell("pm grant ${app.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
        val vm = AppViewModel(app); val bvm = BackendViewModel(app)
        runBlocking { bvm.viewModelScope.coroutineContext.job.children.toList().forEach { it.cancelAndJoin() } }
        runBlocking {
            app.backendConfig.saveDisplayName("Test student"); app.backendConfig.saveTeam("Test team")
            app.database.dao().replaceCheckpoints(session, cps)
        }
        app.repository.selectSession(session)
        bvm.myUserId.value=user; bvm.onlineSession.value=SessionRow(session,"TEST62","Transport practical")
        vm.joinField("Test student","Test team")
        var requested: Pair<String,TransportPhase>? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.viewModelStore.clear()
                app.repository.selectSession(session)
                activity.setContent { VenzaTheme { Surface {
                CompositionLocalProvider(LocalGlassState provides remember { HazeState() }) {
                    StudentScreen(vm,bvm,{}, {}, { id, phase -> requested=id to phase })
                }
            } } } }
            fun currentMap(): MapView { var current:MapView?=null; scenario.onActivity { current=map(it.window.decorView) }; return requireNotNull(current) }
            try {
                awaitText("Start sharing"); awaitText("Journey time")
                val initial=currentMap()
                val deadline=SystemClock.uptimeMillis()+10_000
                while(initial.overlays.filterIsInstance<Polyline>().size!=2 && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(100)
                assertEquals(2, initial.overlays.filterIsInstance<Polyline>().size)
                click("Start sharing")
                instrumentation.waitForIdleSync()
                assertEquals("$session-start" to TransportPhase.START,requested)
                assertEquals(TrackingState.FINISHED,LocationTrackingService.state.value)
                val first=cps.first { it.orderIndex==1 }
                runBlocking { app.repository.submitCheckIn(first,"Test student","Test team",25.0,4.0,FishCondition.GOOD,"original",null,null,null,user,TransportPhase.START) }
                awaitText("Resume sharing"); click("Resume sharing"); awaitText("Sharing location")
                SystemClock.sleep(1200)
                assertTrue(JourneyStore(app).load(session,user).running)
                assertSame(initial,currentMap())
                instrumentation.uiAutomation.takeScreenshot().also { bitmap ->
                    val image=java.io.File(app.filesDir,"review/student-journey.png").apply { parentFile!!.mkdirs() }
                    image.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
                }
                click("Stops"); awaitText("2. Middle stop"); awaitText("Route stop · no transport form needed")
                assertNull(find(instrumentation.uiAutomation.rootInActiveWindow,"Check in"))
                click("Map"); awaitText("Sharing location"); assertSame(initial,currentMap())
                click("Finish"); instrumentation.waitForIdleSync()
                assertEquals("$session-end" to TransportPhase.END,requested)
                awaitText("Record ending conditions")
                val timing=JourneyStore(app).load(session,user)
                assertTrue(timing.finishedAt>0); assertFalse(timing.running)
                runBlocking {
                    app.repository.submitCheckIn(first,"Test student","Test team",99.0,9.0,FishCondition.MORTALITY,"retry",null,null,null,user,TransportPhase.START)
                    app.repository.submitCheckIn(cps.first { it.orderIndex==3 },"Test student","Test team",24.0,3.0,FishCondition.GOOD,"end",null,null,null,user,TransportPhase.END)
                    val saved=app.database.dao().checkInById(TransportJourney.recordId(session,user,TransportPhase.START))!!
                    assertEquals(25.0,saved.temperatureC!!,.01); assertTrue(saved.notes.contains("original"))
                }
                assertNotNull(app.database.dao().checkInById(TransportJourney.recordId(session,user,TransportPhase.END)))
                assertEquals(timing,JourneyStore(app).load(session,user))
            } finally {
                app.stopService(android.content.Intent(app,LocationTrackingService::class.java))
                vm.viewModelScope.coroutineContext.cancelChildren(); bvm.viewModelScope.coroutineContext.cancelChildren()
            }
        }
    }
}
