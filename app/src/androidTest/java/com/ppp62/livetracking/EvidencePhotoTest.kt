package com.ppp62.livetracking

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ppp62.livetracking.service.SyncWorker
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EvidencePhotoTest {
    @Test fun uploadPreservesPortraitOrientationAndBoundsSize(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"rotated-evidence.jpg")
        val bitmap=Bitmap.createBitmap(4000,2000,Bitmap.Config.ARGB_8888)
        file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,95,it)};bitmap.recycle()
        ExifInterface(file).apply{setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString());saveAttributes()}
        val bytes=SyncWorker.evidenceJpeg(context,Uri.fromFile(file))
        val result=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        try {assertTrue(result.height>result.width);assertTrue(result.height<=2048);assertTrue(bytes.size<10*1024*1024)}finally{result.recycle();file.delete()}
    }
}
