// VelorisSDK.kt
// Veloris Sentinel Android SDK
// Version 1.0.0
//
// Passive behavioural biometrics collection SDK for bank integration.
// Mirrors the iOS SDK API surface for consistent cross-platform integration.
//
// Usage:
//   val sdk = VelorisSDK.getInstance(context, apiKey = "sk_live_...", tenantId = "...")
//   sdk.startSession(userId = "bank-user-id")
//   val result = sdk.evaluate(VelorisTransaction(type = TransactionType.FUNDS_TRANSFER, amount = 50000, currency = "GBP"))
//   sdk.endSession()

package app.veloris.sdk

import android.content.Context
import app.veloris.sdk.models.*
import app.veloris.sdk.network.VelorisNetworkClient
import app.veloris.sdk.signals.DeviceFingerprinter
import app.veloris.sdk.signals.SignalCollector

/**
 * Main entry point for the Veloris Sentinel SDK.
 *
 * Use [VelorisSDK.getInstance] to obtain a singleton instance.
 * All public suspend functions must be called from a coroutine scope.
 */
class VelorisSDK private constructor(
    private val context: Context,
    private val config: VelorisConfig
) {

    // MARK: - Public state

    /** Current session ID, non-null while a session is active. */
    var sessionId: String? = null
        private set

    /** True if a session is currently active. */
    val isSessionActive: Boolean get() = sessionId != null

    // MARK: - Private dependencies

    private val networkClient = VelorisNetworkClient(config)
    private val signalCollector = SignalCollector(context, config)
    private val deviceFingerprinter = DeviceFingerprinter(context)

    // MARK: - Session lifecycle

    /**
     * Starts a new Sentinel session. Call this when the user logs in.
     *
     * @param userId    Your bank's internal user identifier.
     * @param channel   The channel (defaults to [VelorisChannel.MOBILE_ANDROID]).
     * @param metadata  Optional metadata; auto-populated from device if null.
     * @return          The session token (also retained internally).
     * @throws VelorisException if a session is already active or network fails.
     */
    suspend fun startSession(
        userId: String,
        channel: VelorisChannel = VelorisChannel.MOBILE_ANDROID,
        metadata: SessionMetadata? = null
    ): String {
        if (sessionId != null) throw VelorisException.SessionAlreadyActive

        val ipAddress = networkClient.fetchClientIP()
        val deviceId  = deviceFingerprinter.deviceId

        val request = CreateSessionRequest(
            userId    = userId,
            tenantId  = config.tenantId,
            channel   = channel.value,
            ipAddress = ipAddress,
            deviceId  = deviceId,
            metadata  = metadata ?: SessionMetadata.fromDevice(context)
        )

        val response = networkClient.post<CreateSessionRequest, CreateSessionResponse>(
            path  = "/sentinel/sessions",
            body  = request
        )

        sessionId = response.sessionId
        signalCollector.start(sessionId = response.sessionId, token = response.sessionToken)

        return response.sessionToken
    }

    /**
     * Evaluates the current session's risk before a sensitive transaction.
     *
     * @param transaction The transaction being attempted.
     * @return            [VelorisEvaluationResult] with trust score, verdict, and risk factors.
     */
    suspend fun evaluate(transaction: VelorisTransaction): VelorisEvaluationResult {
        val sid = sessionId ?: throw VelorisException.NoActiveSession

        val ipAddress = networkClient.fetchClientIP()

        val request = EvaluateSessionRequest(
            transaction = TransactionPayload.from(transaction),
            currentIp   = ipAddress,
            timestamp   = java.time.Instant.now().toString()
        )

        val response = networkClient.post<EvaluateSessionRequest, EvaluateSessionResponse>(
            path = "/sentinel/sessions/$sid/evaluate",
            body = request
        )

        return VelorisEvaluationResult.from(response)
    }

    /**
     * Reports the outcome of a transaction for model feedback.
     * Should be called within 24 hours of [evaluate].
     */
    suspend fun reportOutcome(
        evaluationId: String,
        outcome: TransactionOutcome,
        fraudType: FraudType? = null
    ) {
        val sid = sessionId ?: throw VelorisException.NoActiveSession

        val request = OutcomeRequest(
            evaluationId = evaluationId,
            outcome      = outcome.value,
            fraudType    = fraudType?.value
        )

        networkClient.postNoResponse(
            path = "/sentinel/sessions/$sid/outcome",
            body = request
        )
    }

    /**
     * Ends the current session and stops signal collection.
     * Call this on logout or when the user exits the authenticated area.
     */
    fun endSession() {
        signalCollector.stop()
        sessionId = null
    }

    /**
     * Call when the app enters the background (e.g. from onPause / ProcessLifecycleOwner).
     */
    fun onAppBackground() {
        signalCollector.pause()
    }

    /**
     * Call when the app returns to the foreground (e.g. from onResume / ProcessLifecycleOwner).
     */
    fun onAppForeground() {
        signalCollector.resume()
    }

    // MARK: - Signal recording helpers (call from your UI layer)

    /**
     * Record a keystroke event. Call from a TextWatcher on sensitive input fields.
     * @param keyDuration       How long the key was held (ms).
     * @param interKeyInterval  Time since last key event (ms).
     */
    fun recordKeystroke(keyDuration: Long, interKeyInterval: Long) {
        signalCollector.recordKeystroke(keyDuration, interKeyInterval)
    }

    /**
     * Record a scroll event. Call from RecyclerView.OnScrollListener or NestedScrollView.
     * @param velocityX  Horizontal scroll velocity (px/s).
     * @param velocityY  Vertical scroll velocity (px/s).
     */
    fun recordScroll(velocityX: Float, velocityY: Float) {
        signalCollector.recordScroll(velocityX, velocityY)
    }

    /**
     * Record a touch event. Call from View.OnTouchListener.
     * @param pressure  MotionEvent.getPressure()
     * @param size      MotionEvent.getSize()
     */
    fun recordTouch(pressure: Float, size: Float) {
        signalCollector.recordTouch(pressure, size)
    }

    // MARK: - Companion (singleton factory)

    companion object {
        @Volatile
        private var instance: VelorisSDK? = null

        /**
         * Returns the singleton SDK instance, creating it on first call.
         *
         * @param context     Application context.
         * @param apiKey      Your Veloris API key.
         * @param tenantId    Your bank's Veloris tenant UUID.
         * @param environment [VelorisEnvironment.PRODUCTION] (default), STAGING, or SANDBOX.
         */
        fun getInstance(
            context: Context,
            apiKey: String,
            tenantId: String,
            environment: VelorisEnvironment = VelorisEnvironment.PRODUCTION
        ): VelorisSDK {
            return instance ?: synchronized(this) {
                instance ?: VelorisSDK(
                    context.applicationContext,
                    VelorisConfig(apiKey, tenantId, environment)
                ).also { instance = it }
            }
        }
    }
}
