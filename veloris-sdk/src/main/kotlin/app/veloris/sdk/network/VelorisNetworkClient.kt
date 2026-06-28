// VelorisNetworkClient.kt
// Veloris Sentinel Android SDK — Network Layer (OkHttp + coroutines)

package io.veloris.sdk.network

import io.veloris.sdk.models.VelorisConfig
import io.veloris.sdk.models.VelorisException
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

    // MARK: - Generic POST with response

    suspend inline fun <reified Req : Any, reified Res : Any> post(
        path: String,
        body: Req
    ): Res = withContext(Dispatchers.IO) {
        val requestBody = toJson(body).toRequestBody(JSON)
        val request = buildRequest(path, requestBody)
        val response = client.newCall(request).execute()
        validate(response)
        val raw = response.body?.string() ?: "{}"
        fromJson(raw, Res::class.java)
    }

    // MARK: - POST without response body

    suspend inline fun <reified Req : Any> postNoResponse(
        path: String,
        body: Req
    ) = withContext(Dispatchers.IO) {
        val requestBody = toJson(body).toRequestBody(JSON)
        val request = buildRequest(path, requestBody)
        val response = client.newCall(request).execute()
        validate(response)
    }

    // MARK: - Helpers

    private fun buildRequest(path: String, body: RequestBody): Request {
        return Request.Builder()
            .url(config.environment.baseUrl + path)
            .post(body)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .header("X-Veloris-Version", "2024-06-01")
            .header("X-Request-ID", java.util.UUID.randomUUID().toString())
            .header("User-Agent", "VelorisSDK-Android/1.0.0")
            .build()
    }

    private fun validate(response: Response) {
        when (response.code) {
            in 200..204 -> return
            401 -> throw VelorisException.Unauthorized
            429 -> {
                val retry = response.header("X-Retry-After")?.toIntOrNull() ?: 60
                throw VelorisException.RateLimited(retry)
            }
            in 500..503 -> throw VelorisException.ServerError
            else -> {
                val message = extractErrorMessage(response.body?.string()) ?: "HTTP ${response.code}"
                throw VelorisException.NetworkError(response.code, message)
            }
        }
    }

    private fun extractErrorMessage(body: String?): String? {
        return try {
            JSONObject(body ?: "").getJSONObject("error").getString("message")
        } catch (_: Exception) { null }
    }

    // MARK: - JSON serialisation (lightweight, no external lib needed)

    private fun toJson(obj: Any): String {
        val json = JSONObject()
        obj.javaClass.declaredFields.forEach { field ->
            field.isAccessible = true
            val value = field.get(obj)
            if (value != null) json.put(field.name, value)
        }
        return json.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> fromJson(raw: String, clazz: Class<T>): T {
        val json = JSONObject(raw)
        val instance = clazz.getDeclaredConstructor().newInstance()
        clazz.declaredFields.forEach { field ->
            field.isAccessible = true
            if (json.has(field.name)) {
                try {
                    when (field.type) {
                        String::class.java  -> field.set(instance, json.getString(field.name))
                        Int::class.java,
                        Integer::class.java -> field.set(instance, json.getInt(field.name))
                        Boolean::class.java -> field.set(instance, json.getBoolean(field.name))
                        else                -> {} // nested objects handled by subclass overrides
                    }
                } catch (_: Exception) {}
            }
        }
        return instance
    }

    // MARK: - IP resolution

    suspend fun fetchClientIP(): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("https://api.ipify.org").build()
            client.newCall(request).execute().body?.string()?.trim() ?: ""
        } catch (_: Exception) { "" }
    }
}
