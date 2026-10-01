package com.ppp62.livetracking

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ppp62.livetracking.ui.components.SatelliteTiles
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.config.Configuration
import org.osmdroid.util.MapTileIndex
import java.net.HttpURLConnection
import java.net.URL

/** Network acceptance check for the keyless imagery provider; requires Internet. */
@RunWith(AndroidJUnit4::class)
class SatelliteProviderTest {
    @Test fun imageryEndpointReturnsADecodableTileOnAndroid(){
        val metadata=runBlocking{SatelliteTiles.metadata()}
        val source=SatelliteTiles.source(metadata)
        val url=source.getTileURLString(MapTileIndex.getTileIndex(15,26079,16940))
        val connection=URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=15000;connection.readTimeout=15000
            connection.setRequestProperty("User-Agent",Configuration.getInstance().userAgentValue)
            assertEquals(200,connection.responseCode)
            val image=connection.inputStream.use{BitmapFactory.decodeStream(it)}
            assertNotNull(image);assertEquals(256,image.width);assertEquals(256,image.height);image.recycle()
        } finally {connection.disconnect()}
    }
}
