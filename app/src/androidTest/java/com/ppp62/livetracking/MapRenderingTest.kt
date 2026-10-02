package com.ppp62.livetracking

import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ppp62.livetracking.data.CheckpointEntity
import com.ppp62.livetracking.ui.components.*
import com.ppp62.livetracking.ui.theme.VenzaTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.views.MapView
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.tileprovider.ExpirableBitmapDrawable
import kotlin.math.*

/** Map-only tests; do not create accounts or write session/evidence records. Requires Internet. */
@RunWith(AndroidJUnit4::class)
class MapRenderingTest {
    private fun find(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
        return null
    }
    @Test fun mapRetainsOverlaysAndViewportAcrossUiUpdatesAndReentry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("map_viewports", 0)
        val key = "map-performance-test"
        prefs.edit().remove("$key.lat").remove("$key.lon").remove("$key.zoom").commit()
        val revision = mutableIntStateOf(0)
        val show = mutableStateOf(true)
        val target = mutableStateOf<GeoPoint?>(null, referentialEqualityPolicy())
        val checkpoints = listOf(CheckpointEntity("map-test", "map-test", "Stop", -6.1751, 106.97, orderIndex=1, instructions=""))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val firstRequestAt=SystemClock.elapsedRealtime()
            scenario.onActivity { activity -> activity.setContent {
                VenzaTheme { Surface(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalGlassState provides remember { HazeState() }) {
                        if (show.value) OsmMap(Modifier.fillMaxSize(), checkpoints, target=target.value,
                            mapStyle=MapStyle.Satellite, layersTopPadding=(72+revision.intValue).dp, viewportKey=key)
                    }
                } }
            } }
            fun current(): MapView {
                var map: MapView? = null
                scenario.onActivity { map=find(it.window.decorView) }
                return requireNotNull(map)
            }
            instrumentation.waitForIdleSync(); SystemClock.sleep(500)
            val first=current()
            assertEquals("EOXSentinel2025", first.tileProvider.tileSource.name())
            Log.i("MapPerformance", "Test viewport: ${first.mapCenter.latitude},${first.mapCenter.longitude}; zoom=${first.zoomLevelDouble}; size=${first.width}x${first.height}")
            assertEquals(-6.1751,first.mapCenter.latitude,.0001)
            assertEquals(106.97,first.mapCenter.longitude,.0001)
            assertEquals(15.0,first.zoomLevelDouble,.01)
            val index=MapTileIndex.getTileIndex(15, floor((106.97+180)/360*32768).toInt(), floor((1-asinh(tan(Math.toRadians(-6.1751)))/PI)/2*32768).toInt())
            fun awaitTile(map: MapView): Long {
                val start=SystemClock.elapsedRealtime()
                while (SystemClock.elapsedRealtime()-start<30000) {
                    val tile=map.tileProvider.tileCache.getMapTile(index)
                    if (tile!=null && ExpirableBitmapDrawable.getState(tile)==ExpirableBitmapDrawable.UP_TO_DATE) return SystemClock.elapsedRealtime()-start
                    SystemClock.sleep(50)
                }
                error("Center satellite tile did not load")
            }
            val initial=awaitTile(first)
            val firstTileMs=SystemClock.elapsedRealtime()-firstRequestAt
            val overlays=first.overlays.toList()
            scenario.onActivity { revision.intValue++ }
            instrumentation.waitForIdleSync(); SystemClock.sleep(200)
            assertSame(first, current())
            assertEquals(overlays.size, first.overlays.size)
            overlays.forEachIndexed { i, overlay -> assertSame(overlay, first.overlays[i]) }
            // A fresh request for identical GPS coordinates must still recenter after panning.
            scenario.onActivity { target.value=GeoPoint(-6.1751,106.97) }
            SystemClock.sleep(1500)
            scenario.onActivity { first.controller.setCenter(GeoPoint(-6.18,106.975)) }
            scenario.onActivity { target.value=GeoPoint(-6.1751,106.97) }
            SystemClock.sleep(1500)
            assertEquals(-6.1751, first.mapCenter.latitude, .0001)
            assertEquals(106.97, first.mapCenter.longitude, .0001)
            scenario.onActivity { first.controller.setCenter(GeoPoint(-6.18,106.975)); show.value=false }
            instrumentation.waitForIdleSync(); SystemClock.sleep(200)
            val reopen=SystemClock.elapsedRealtime()
            scenario.onActivity { show.value=true }
            instrumentation.waitForIdleSync(); SystemClock.sleep(200)
            val second=current()
            assertNotSame(first, second)
            assertEquals(-6.18, second.mapCenter.latitude,.0001)
            assertEquals(106.975, second.mapCenter.longitude,.0001)
            val warm=awaitTile(second)
            val reopenMs=SystemClock.elapsedRealtime()-reopen
            Log.i("MapPerformance", "First center tile within=${firstTileMs}ms; initial tile wait=${initial}ms; reentry=${reopenMs}ms; warm tile wait=${warm}ms")
            java.io.File(instrumentation.targetContext.filesDir,"map-performance.json").writeText(
                """{"first_tile_ms":$firstTileMs,"return_to_map_ms":$reopenMs,"warm_tile_wait_ms":$warm}""")
        }
        prefs.edit().remove("$key.lat").remove("$key.lon").remove("$key.zoom").apply()
    }
}
