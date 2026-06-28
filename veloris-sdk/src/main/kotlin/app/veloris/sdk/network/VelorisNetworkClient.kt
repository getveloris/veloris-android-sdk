package app.veloris.sdk.network

import app.veloris.sdk.models.VelorisConfig
import app.veloris.sdk.models.VelorisException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal class VelorisNetworkClient(private val config: VelorisConfig) {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun post(path: String, bodyJson: String): String = withContext(Dispatchers.IO) {
        val requestBody = bodyJson.toRequestBody(JSON)
        val request = Request.Builder()
            .url(config.environment.baseUrl + path)
            .post(requestBody)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .header("X-Veloris-Version", "2024-06-01")
            .header("X-Request-ID", java.util.UUID.randomUUID().toString())
            .header("User-Agent", "VelorisSDK-Android/1.0.0")
            .build()

        val response = client.newCall(request).execute()
        validate(response)
        response.body?.string() ?: "{}"
    }

    suspend fun postNoResponse(path: String, bodyJson: String) = withContext(Dispatchers.IO) {
        val requestBody = bodyJson.toRequestBody(JSON)
        val request = Request.Builder()
            .url(config.environment.baseUrl + path)
            .post(requestBody)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .header("X-Veloris-Version", "2024-06-01")
            .build()
        val response = client.newCall(request).execute()
        validate(response)
    }

    private fun validate(response: Response) {
        when (response.code) {
            in 200..204 -> return
            401 -> throw VelorisException.Unauthorized
            429 -> throw VelorisException.RateLimited(response.header("X-Retry-After")?.toIntOrNull() ?: 60)
            in 500..503 -> throw VelorisException.ServerError
            else -> throw VelorisException.NetworkError(response.code, response.body?.string() ?: "HTTP ${response.code}")
        }
    }

    suspend fun fetchClientIP(): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("https://api.ipify.org").build()
            client.newCall(request).execute().body?.string()?.trim() ?: ""
        } catch (_: Exception) { "" }
    }

    // JSON helpers
    fun toJson(vararg pairs: Pair<String, Any?>): String {
        val obj = JSONObject()
        pairs.forEach { (k, v) -> if (v != null) obj.put(k, v) }
        return obj.toString()
    }

    fun parseJson(raw: String): JSONObject = JSONObject(raw)
}
