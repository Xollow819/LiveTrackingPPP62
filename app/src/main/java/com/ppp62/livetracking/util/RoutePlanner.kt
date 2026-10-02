package com.ppp62.livetracking.util

import android.content.Context
import android.os.SystemClock
import com.ppp62.livetracking.BuildConfig
import com.ppp62.livetracking.data.CheckpointEntity
import com.ppp62.livetracking.data.TransportJourney
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

@Serializable data class RouteCoordinate(val latitude: Double, val longitude: Double)
@Serializable data class RoutePlan(val points: List<RouteCoordinate>, val road: Boolean, val distanceMeters: Double)

/** Cache by lecturer coordinates, never by the student's live position or timer. */
object RoutePlanner {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var lastRequest = 0L
    private val memory = LinkedHashMap<String, Pair<Long, RoutePlan>>()
    private const val MAX_BYTES = 512 * 1024
    fun coordinates(checkpoints: List<CheckpointEntity>) = TransportJourney.ordered(checkpoints)
        .map { RouteCoordinate(it.latitude, it.longitude) }
    fun direct(points: List<RouteCoordinate>) = RoutePlan(points, false, points.zipWithNext().sumOf { (a, b) ->
        LocationUtils.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
    })
    fun parseResponse(body: String): RoutePlan {
        val root = json.parseToJsonElement(body).jsonObject
        require(root["code"]?.jsonPrimitive?.content == "Ok")
        val route = root.getValue("routes").jsonArray.first().jsonObject
        val points = route.getValue("geometry").jsonObject.getValue("coordinates").jsonArray.map {
            val coordinate = it.jsonArray
            RouteCoordinate(coordinate[1].jsonPrimitive.double, coordinate[0].jsonPrimitive.double).also { point ->
                require(point.latitude.isFinite() && point.latitude in -90.0..90.0 && point.longitude.isFinite() && point.longitude in -180.0..180.0)
            }
        }
        val distance = route.getValue("distance").jsonPrimitive.double
        require(points.size in 2..20_000 && distance.isFinite() && distance >= 0)
        return RoutePlan(points, true, distance)
    }
    suspend fun load(context: Context, points: List<RouteCoordinate>, retry: Boolean = false): RoutePlan = withContext(Dispatchers.IO) {
        val fallback = direct(points)
        if (points.size !in 2..100 || points.any { !it.latitude.isFinite() || it.latitude !in -90.0..90.0 || !it.longitude.isFinite() || it.longitude !in -180.0..180.0 }) return@withContext fallback
        val coordinates = points.joinToString(";") { "${it.longitude},${it.latitude}" }
        val key = MessageDigest.getInstance("SHA-256").digest(coordinates.toByteArray()).joinToString("") { "%02x".format(it) }
        val directory = File(context.cacheDir, "routes").apply { mkdirs() }
        val cache = File(directory, "$key.json")
        lock.withLock {
            val now = System.currentTimeMillis()
            memory[key]?.takeIf { !retry && now - it.first in 0..(if (it.second.road) 7 * 86_400_000L else 60_000L) }?.let { return@withLock it.second }
            if (!retry && cache.isFile && cache.length() <= MAX_BYTES && now - cache.lastModified() in 0..7 * 86_400_000L) {
                runCatching { json.decodeFromString<RoutePlan>(cache.readText()) }.getOrNull()?.takeIf { it.road }?.let {
                    memory[key] = now to it; return@withLock it
                }
            }
            // The public OSRM service permits at most one request per second.
            delay((1100 - (SystemClock.elapsedRealtime() - lastRequest)).coerceAtLeast(0))
            lastRequest = SystemClock.elapsedRealtime()
            val plan = try {
                val connection = URL("https://routing.openstreetmap.de/routed-car/route/v1/driving/$coordinates?overview=simplified&geometries=geojson&steps=false&generate_hints=false")
                    .openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 8000; connection.readTimeout = 8000
                    connection.setRequestProperty("User-Agent", "${context.packageName}/${BuildConfig.VERSION_NAME}")
                    connection.setRequestProperty("Accept", "application/json")
                    check(connection.responseCode == 200)
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(output.size() + count <= MAX_BYTES)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    parseResponse(bytes.toString(Charsets.UTF_8))
                } finally { connection.disconnect() }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { fallback }
            memory[key] = now to plan
            while (memory.size > 12) memory.remove(memory.keys.first())
            if (plan.road) runCatching {
                cache.writeText(json.encodeToString(plan))
                directory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(12)?.forEach { it.delete() }
            }
            plan
        }
    }
}
