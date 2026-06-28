// DeviceFingerprinter.kt
// Veloris Sentinel Android SDK — Device Fingerprinting

package io.veloris.sdk.signals

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.util.UUID

internal class DeviceFingerprinter(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "io.veloris.sdk.prefs", Context.MODE_PRIVATE
    )
    private val prefKey = "device_id"

    /** Stable, privacy-preserving device identifier. */
    val deviceId: String by lazy {
        prefs.getString(prefKey, null) ?: generateAndStore(context)
    }

    private fun generateAndStore(context: Context): String {
        // Use Android ID as base (stable per app signing key + device)
        val androidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        )
        val id = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            androidId
        } else {
            UUID.randomUUID().toString()
        }
        prefs.edit().putString(prefKey, id).apply()
        return id
    }
}

// MARK: - SignalUploader.kt

package io.veloris.sdk.signals

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal object SignalUploader {

    private val client = OkHttpClient()
    private val JSON   = "application/json; charset=utf-8".toMediaType()

    /** Fire-and-forget signal upload. Failures are silently ignored. */
    suspend fun upload(batch: SignalBatch, sessionToken: String, baseUrl: String) {
        try {
            val body = buildJson(batch)
            val request = Request.Builder()
                .url("$baseUrl/sentinel/signals")
                .post(body.toString().toRequestBody(JSON))
                .header("Authorization", "Bearer $sessionToken")
                .header("Content-Type", "application/json")
                .header("X-Veloris-Version", "2024-06-01")
                .build()
            client.newCall(request).execute().close()
        } catch (_: Exception) {
            // Signals are best-effort; model degrades gracefully with partial data
        }
    }

    private fun buildJson(batch: SignalBatch): JSONObject {
        val obj = JSONObject()
        obj.put("session_id", batch.sessionId)

        val keystrokes = JSONArray()
        batch.keystrokes.forEach {
            keystrokes.put(JSONObject().apply {
                put("key_duration", it.keyDuration)
                put("inter_key_interval", it.interKeyInterval)
                put("timestamp", it.timestamp)
            })
        }
        obj.put("keystrokes", keystrokes)

        val scrolls = JSONArray()
        batch.scrolls.forEach {
            scrolls.put(JSONObject().apply {
                put("velocity_x", it.velocityX)
                put("velocity_y", it.velocityY)
                put("timestamp", it.timestamp)
            })
        }
        obj.put("scrolls", scrolls)

        val tilts = JSONArray()
        batch.tilts.forEach {
            tilts.put(JSONObject().apply {
                put("pitch", it.pitch)
                put("roll", it.roll)
                put("yaw", it.yaw)
                put("timestamp", it.timestamp)
            })
        }
        obj.put("tilts", tilts)

        val touches = JSONArray()
        batch.touches.forEach {
            touches.put(JSONObject().apply {
                put("pressure", it.pressure)
                put("size", it.size)
                put("timestamp", it.timestamp)
            })
        }
        obj.put("touches", touches)

        return obj
    }
}
