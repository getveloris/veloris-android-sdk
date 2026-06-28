package app.veloris.sdk.signals

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal object SignalUploader {

    private val client = OkHttpClient()
    private val JSON   = "application/json; charset=utf-8".toMediaType()

    suspend fun upload(batch: SignalBatch, sessionToken: String, baseUrl: String) {
        try {
            val obj = JSONObject()
            obj.put("session_id", batch.sessionId)

            val keystrokes = JSONArray()
            for (k in batch.keystrokes) {
                keystrokes.put(JSONObject().apply {
                    put("key_duration", k.keyDuration)
                    put("inter_key_interval", k.interKeyInterval)
                    put("timestamp", k.timestamp)
                })
            }
            obj.put("keystrokes", keystrokes)

            val scrolls = JSONArray()
            for (s in batch.scrolls) {
                scrolls.put(JSONObject().apply {
                    put("velocity_x", s.velocityX)
                    put("velocity_y", s.velocityY)
                    put("timestamp", s.timestamp)
                })
            }
            obj.put("scrolls", scrolls)

            val tilts = JSONArray()
            for (t in batch.tilts) {
                tilts.put(JSONObject().apply {
                    put("pitch", t.pitch)
                    put("roll", t.roll)
                    put("yaw", t.yaw)
                    put("timestamp", t.timestamp)
                })
            }
            obj.put("tilts", tilts)

            val touches = JSONArray()
            for (t in batch.touches) {
                touches.put(JSONObject().apply {
                    put("pressure", t.pressure)
                    put("size", t.size)
                    put("timestamp", t.timestamp)
                })
            }
            obj.put("touches", touches)

            val request = Request.Builder()
                .url("$baseUrl/sentinel/signals")
                .post(obj.toString().toRequestBody(JSON))
                .header("Authorization", "Bearer $sessionToken")
                .header("Content-Type", "application/json")
                .header("X-Veloris-Version", "2024-06-01")
                .build()
            client.newCall(request).execute().close()
        } catch (_: Exception) { }
    }
}
