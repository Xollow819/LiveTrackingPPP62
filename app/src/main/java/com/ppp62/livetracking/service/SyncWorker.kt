package com.ppp62.livetracking.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.work.*
import com.ppp62.livetracking.PPP62Application
import com.ppp62.livetracking.data.SyncState
import com.ppp62.livetracking.data.remote.SubmissionRow
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PPP62Application
        if (!app.backend.isConfigured()) return Result.retry()
        val uid = app.backend.ensureSignedIn() ?: return Result.retry()
        var failed = false
        for(row in app.database.dao().pendingPositions()) {
            if(row.userId!=uid) continue
            try {
                val session=app.backend.findSessionById(row.sessionId)
                if(session!=null && session.isActive) app.backend.publishPosition(row.sessionId,row.userId,row.displayName,row.latitude,row.longitude,row.accuracy,row.recordedAt,row.team,row.trackingState,row.eventAt)
                else if(session==null) error("Session unavailable")
                app.database.dao().acknowledgePosition(row.sessionId,row.userId,row.eventAt)
            } catch(e:CancellationException){throw e} catch(_:Exception){failed=true}
        }
        for (record in app.database.dao().pendingCheckIns()) {
            // Never attribute an old anonymous user's record to a new identity.
            if (record.userId != uid) continue
            try {
                val existing=app.backend.submissionById(record.id)
                if(existing!=null) {
                    require(existing.userId==uid && existing.sessionId==record.sessionId) {"Record identity conflict"}
                    app.database.dao().setCheckInSyncState(record.id,SyncState.SYNCED)
                    continue
                }
                val session=app.backend.findSessionById(record.sessionId)
                if(session!=null && !session.isActive) {app.database.dao().failCheckIn(record.id,"Session completed before upload; contact your lecturer");continue}
                val cp = app.database.dao().checkpoint(record.checkpointId)
                if(cp==null){app.database.dao().failCheckIn(record.id,"Checkpoint no longer available");continue}
                val path = record.photoUri?.let {
                    val bytes = evidenceJpeg(applicationContext, Uri.parse(it))
                    app.backend.uploadEvidence(record.sessionId, uid, record.id, bytes)
                }
                app.backend.submitEvidence(SubmissionRow(
                    id = record.id, sessionId = record.sessionId, userId = uid,
                    displayName = record.studentName, team = record.team,
                    checkpointId = record.checkpointId, checkpointName = cp.name,
                    exceptionReason = record.exceptionReason, note = record.notes, photoPath = path,
                    lat = record.latitude, lng = record.longitude, temperatureC = record.temperatureC,
                    weightKg = record.weightKg, condition = record.condition.name,
                    createdAt = java.time.Instant.ofEpochMilli(record.createdAt).toString()
                ))
                app.database.dao().setCheckInSyncState(record.id, SyncState.SYNCED)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true }
        }
        val remaining=app.database.dao().pendingCheckIns().any{it.userId==uid} || app.database.dao().pendingPositions().any{it.userId==uid}
        return if (failed || remaining) Result.retry() else Result.success()
    }
    companion object {
        private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("evidence-upload", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("ppp62-sync", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints()).build())
        }
        fun evidenceJpeg(context: Context, uri: Uri): ByteArray {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Photo unavailable" }
            var sample = 1
            while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("Photo unavailable")
            val orientation=context.contentResolver.openInputStream(uri)?.use {ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}
            val matrix=Matrix().apply {
                when(orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f,1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f,-1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {setRotate(90f);postScale(-1f,1f)}
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> {setRotate(270f);postScale(-1f,1f)}
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            val oriented=if(matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true).also{bitmap.recycle()}
            return ByteArrayOutputStream().use { output ->
                try { check(oriented.compress(Bitmap.CompressFormat.JPEG, 85, output)); output.toByteArray() }
                finally { oriented.recycle() }
            }
        }
    }
}
