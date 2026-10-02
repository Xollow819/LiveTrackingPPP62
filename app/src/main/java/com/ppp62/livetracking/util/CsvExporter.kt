package com.ppp62.livetracking.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ppp62.livetracking.data.CheckInEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {
    fun share(context: Context, rows: List<CheckInEntity>) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "ppp62-checkins-${System.currentTimeMillis()}.csv")
        file.bufferedWriter().use { out ->
            out.appendLine("id,checkpoint_id,student,team,water_temperature_c,total_fish,ph,dissolved_oxygen_mg_l,condition,distance_m,status,exception,created_at,notes")
            rows.forEach { r -> out.appendLine(listOf(r.id,r.checkpointId,r.studentName,r.team,r.temperatureC ?: "",r.totalFish ?: "",r.ph ?: "",r.dissolvedOxygen ?: "",r.condition.name,r.distanceMeters ?: "",r.syncState.name,r.exceptionReason ?: "",SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date(r.createdAt)),r.notes).joinToString(",") { csv(it.toString()) }) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Export PPP62 report"))
    }
    private fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""
}
