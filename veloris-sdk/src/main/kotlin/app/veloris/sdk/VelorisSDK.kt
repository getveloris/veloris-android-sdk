package app.veloris.sdk

import android.content.Context
import app.veloris.sdk.models.*
import app.veloris.sdk.network.VelorisNetworkClient
import app.veloris.sdk.signals.DeviceFingerprinter
import app.veloris.sdk.signals.SignalCollector
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class VelorisSDK private constructor(
    private val context: Context,
    private val config: VelorisConfig
) {
    var sessionId: String? = null
        private set

    val isSessionActive: Boolean get() = sessionId != null

    private val networkClient = VelorisNetworkClient(config)
    private val signalCollector = SignalCollector(context, config)
    private val deviceFingerprinter = DeviceFingerprinter(context)

    suspend fun startSession(
        userId: String,
        channel: VelorisChannel = VelorisChannel.MOBILE_ANDROID,
        metadata: SessionMetadata? = null
    ): String {
        if (sessionId != null) throw VelorisException.SessionAlreadyActive

        val ipAddress = networkClient.fetchClientIP()
        val deviceId  = deviceFingerprinter.deviceId
        val meta      = metadata ?: SessionMetadata.fromDevice(context)

        val bodyJson = JSONObject().apply {
            put("user_id",   userId)
            put("tenant_id", config.tenantId)
            put("channel",   channel.value)
            put("ip_address", ipAddress)
            put("device_id", deviceId)
            put("metadata", JSONObject().apply {
                put("app_version", meta.appVersion)
                put("os_version",  meta.osVersion)
                put("locale",      meta.locale)
            })
        }.toString()

        val raw  = networkClient.post("/sentinel/sessions", bodyJson)
        val json = JSONObject(raw)

        val sid   = json.getString("session_id")
        val token = json.getString("session_token")

        sessionId = sid
        signalCollector.start(sessionId = sid, token = token)

        return token
    }

    suspend fun evaluate(transaction: VelorisTransaction): VelorisEvaluationResult {
        val sid = sessionId ?: throw VelorisException.NoActiveSession
        val ip  = networkClient.fetchClientIP()

        val txJson = JSONObject().apply {
            put("type", transaction.type.value)
            transaction.amount?.let { put("amount", it) }
            transaction.currency?.let { put("currency", it) }
            transaction.beneficiaryId?.let { put("beneficiary_id", it) }
        }

        val bodyJson = JSONObject().apply {
            put("transaction", txJson)
            put("current_ip",  ip)
            put("timestamp",   Instant.now().toString())
        }.toString()

        val raw  = networkClient.post("/sentinel/sessions/$sid/evaluate", bodyJson)
        val json = JSONObject(raw)

        val factors = mutableListOf<RiskFactor>()
        val arr = json.optJSONArray("risk_factors") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val f = arr.getJSONObject(i)
            factors.add(RiskFactor(f.getString("signal"), f.getString("severity"), f.getString("description")))
        }

        return VelorisEvaluationResult(
            sessionId         = json.getString("session_id"),
            trustScore        = json.getInt("trust_score"),
            riskBand          = RiskBand.from(json.getString("risk_band")),
            verdict           = VelorisVerdict.from(json.getString("verdict")),
            riskFactors       = factors,
            recommendedAction = json.getString("recommended_action"),
            stepUpMethod      = StepUpMethod.from(json.optString("step_up_method", null)),
            evaluationId      = json.getString("evaluation_id"),
            latencyMs         = json.getInt("latency_ms")
        )
    }

    suspend fun reportOutcome(
        evaluationId: String,
        outcome: TransactionOutcome,
        fraudType: FraudType? = null
    ) {
        val sid = sessionId ?: throw VelorisException.NoActiveSession
        val bodyJson = JSONObject().apply {
            put("evaluation_id", evaluationId)
            put("outcome", outcome.value)
            fraudType?.let { put("fraud_type", it.value) }
        }.toString()
        networkClient.postNoResponse("/sentinel/sessions/$sid/outcome", bodyJson)
    }

    fun endSession() {
        signalCollector.stop()
        sessionId = null
    }

    fun onAppBackground() { signalCollector.pause() }
    fun onAppForeground() { signalCollector.resume() }
    fun recordKeystroke(keyDuration: Long, interKeyInterval: Long) = signalCollector.recordKeystroke(keyDuration, interKeyInterval)
    fun recordScroll(velocityX: Float, velocityY: Float) = signalCollector.recordScroll(velocityX, velocityY)
    fun recordTouch(pressure: Float, size: Float) = signalCollector.recordTouch(pressure, size)

    companion object {
        @Volatile private var instance: VelorisSDK? = null

        fun getInstance(
            context: Context,
            apiKey: String,
            tenantId: String,
            environment: VelorisEnvironment = VelorisEnvironment.PRODUCTION
        ): VelorisSDK = instance ?: synchronized(this) {
            instance ?: VelorisSDK(
                context.applicationContext,
                VelorisConfig(apiKey, tenantId, environment)
            ).also { instance = it }
        }
    }
}
