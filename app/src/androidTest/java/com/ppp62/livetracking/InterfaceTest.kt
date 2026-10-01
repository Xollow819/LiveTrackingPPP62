package com.ppp62.livetracking

import android.graphics.Rect
import java.io.File
import java.io.FileOutputStream
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ppp62.livetracking.data.*
import com.ppp62.livetracking.data.remote.SessionRow
import com.ppp62.livetracking.ui.*
import com.ppp62.livetracking.ui.components.LocalGlassState
import com.ppp62.livetracking.ui.screens.*
import com.ppp62.livetracking.ui.theme.VenzaTheme
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses public UiAutomation APIs: older Espresso injection uses hidden APIs removed in Android 17. */
@RunWith(AndroidJUnit4::class)
class InterfaceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val automation get()=instrumentation.uiAutomation
    private val app get()=instrumentation.targetContext.applicationContext as PPP62Application
    private fun render(content:@Composable ()->Unit):ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java).also {scenario->
        scenario.onActivity{activity->activity.setContent{VenzaTheme{androidx.compose.material3.Surface{CompositionLocalProvider(LocalGlassState provides remember{HazeState()}){content()}}}}}
    }
    private fun find(root:AccessibilityNodeInfo?,predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo? {
        if(root==null)return null
        if(predicate(root))return root
        for(i in 0 until root.childCount) find(root.getChild(i),predicate)?.let{return it}
        return null
    }
    private fun node(value:String,description:Boolean=false):AccessibilityNodeInfo {
        val deadline=SystemClock.uptimeMillis()+10000
        while(SystemClock.uptimeMillis()<deadline){
            find(automation.rootInActiveWindow){n->
                if(description) n.contentDescription?.contains(value)==true
                else n.text?.toString()?.trim()==value || n.text?.toString()?.split('\n')?.any{it.trim()==value}==true || n.hintText?.toString()==value
            }?.let{return it}
            SystemClock.sleep(100)
        }
        error("Accessible node not found: $value")
    }
    private fun click(value:String,description:Boolean=false) {
        val deadline=SystemClock.uptimeMillis()+10000
        while(SystemClock.uptimeMillis()<deadline) {
            val item=find(automation.rootInActiveWindow){n ->
                val matches=if(description) n.contentDescription?.contains(value)==true else n.text?.toString()?.split('\n')?.any{it.trim()==value}==true
                var parent:AccessibilityNodeInfo?=n;var clickable=false
                while(parent!=null){if(parent.isClickable){clickable=true;break};parent=parent.parent}
                matches&&clickable
            }
            var parent=item
            while(parent!=null){if(parent.isClickable&&parent.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;parent=parent.parent}
            SystemClock.sleep(100)
        }
        error("Node is not clickable: $value")
    }
    private fun text(label:String,value:String) {
        var item:AccessibilityNodeInfo?=node(label)
        val args=Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)}
        while(item!=null){if(item.isEditable&&item.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))return;item=item.parent}
        error("Node is not editable: $label")
    }
    private fun swipeUp(){
        val rect=Rect();automation.rootInActiveWindow.getBoundsInScreen(rect)
        val start=SystemClock.uptimeMillis();val x=rect.centerX().toFloat();val from=rect.height()*.8f;val to=rect.height()*.35f
        for(i in 0..10){val event=MotionEvent.obtain(start,start+i*20,if(i==0)MotionEvent.ACTION_DOWN else if(i==10)MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE,x,from+(to-from)*i/10,0);automation.injectInputEvent(event,true);event.recycle()}
    }
    private fun screenshot(name:String){
        instrumentation.waitForIdleSync();SystemClock.sleep(300)
        val file=File(app.filesDir,"review/$name.png").apply{parentFile!!.mkdirs()}
        val bitmap=automation.takeScreenshot()
        FileOutputStream(file).use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
        bitmap.recycle();assertTrue(file.length()>0)

    }
    @Test fun welcomeOffersRolesAndPreferences(){
        var selected:UserRole?=null
        render{WelcomeScreen({selected=it})}.use{
            node("Every stop.\nIn view.")
            node("Preferences",true);screenshot("welcome")
            click("Join as a student");instrumentation.waitForIdleSync();assertEquals(UserRole.STUDENT,selected)
        }
    }
    @Test fun joinRequiresCodeNameAndTeam(){
        val vm=AppViewModel(app);val bvm=BackendViewModel(app)
        render{StudentScreen(vm,bvm,{},{})}.use{
            click("Join session");node("Enter code, name and team");screenshot("student-join")
        }
    }
    @Test fun studentShowsMapStopsAndQueuedEvidence(){
        val vm=AppViewModel(app);val bvm=BackendViewModel(app);val session="11111111-1111-1111-1111-111111111111"
        runBlocking{
            app.backendConfig.saveDisplayName("Ayu");app.backendConfig.saveTeam("Team A")
            app.database.dao().replaceCheckpoints(session,listOf(CheckpointEntity("test-stop",session,"Harbour",-6.1,106.8,75.0,1,"Check oxygen")))
            app.database.dao().upsertLocation(LocationEntity("test-student",session,"Ayu","Team A",-6.1,106.8,8f,0f,0f,System.currentTimeMillis(),TrackingState.LIVE,80))
            app.database.dao().upsertCheckIn(CheckInEntity("test-record","test-stop",session,"Ayu","Team A",25.0,4.0,FishCondition.GOOD,"",null,null,null,null,System.currentTimeMillis(),SyncState.PENDING,userId="test-student"))
        }
        app.repository.selectSession(session);bvm.myUserId.value="test-student";bvm.onlineSession.value=SessionRow(session,"ABC234","Morning practical");vm.joinField("Ayu","Team A")
        render{StudentScreen(vm,bvm,{},{})}.use{
            node("Search places",true);node("Find my location",true);screenshot("student-map-standard")
            click("Choose map type",true);node("Satellite");screenshot("map-layers")
            click("Satellite");SystemClock.sleep(5000);screenshot("student-map-satellite")
            click("Stops");node("Check oxygen");screenshot("student-stops")
            click("Records");node("Upload queued");screenshot("student-records")
        }
    }
    @Test fun lecturerAccountIsReadableAndPasswordIsProtected(){
        val bvm=BackendViewModel(app)
        render{LecturerAccountScreen(bvm,{})}.use{node("Welcome back.");node("Password");screenshot("lecturer-account")}
    }
    @Test fun checkpointFormRejectsNonFiniteAndNegativeMeasurements(){
        val vm=AppViewModel(app);app.repository.selectSession("form")
        vm.setSessionCheckpoints(listOf(CheckpointEntity("form-stop","form","Harbour",-6.1,106.8,75.0,1,"Check oxygen",requiresPhoto=false)))
        render{CheckInScreen(vm,"form-stop",{})}.use{
            text("Water temperature (°C)","NaN");text("Consignment weight (kg)","-4")
            swipeUp();click("Save check-in")
            assertEquals(0,runBlocking{app.database.dao().pendingCheckIns().count{it.checkpointId=="form-stop"}})
            screenshot("check-in")
        }
    }
    @Test fun preferencesSupportOpaquePanels(){
        val bvm=BackendViewModel(app)
        render{PreferencesScreen(bvm,{})}.use{node("Reduce transparency");node("Connection");screenshot("preferences")}
    }
    @Test fun lecturerWorkspaceProvidesSessionSetupAndRouteEditing(){
        val vm=AppViewModel(app);val bvm=BackendViewModel(app)
        bvm.lecturerAuthenticated.value=true
        render{LecturerScreen(vm,bvm,{},{})}.use{
            node("Field sessions");screenshot("lecturer-workspace")
            click("New session");node("Session title");screenshot("session-details")
            text("Session title","Morning practical");click("Continue to map");screenshot("route-setup")
        }
    }
    @Test fun lecturerMapOffersRouteEditing(){
        val vm=AppViewModel(app);val bvm=BackendViewModel(app)
        bvm.lecturerAuthenticated.value=true;bvm.myRole.value="lecturer"
        bvm.onlineSession.value=SessionRow("11111111-1111-1111-1111-111111111111","ABC234","Morning practical")
        render{LecturerScreen(vm,bvm,{},{})}.use{
            node("Edit route",true);screenshot("lecturer-map")
            click("Students");node("Your group");screenshot("lecturer-roster")
            click("Map");click("Edit route",true);node("Route checkpoints");screenshot("route-editor")
        }
    }

}
