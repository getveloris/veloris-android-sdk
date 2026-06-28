package app.veloris.sdk.models

import android.content.Context
import android.os.Build

// ── Configuration ─────────────────────────────────────────────────────────────

enum class VelorisEnvironment(val baseUrl: String) {
    PRODUCTION("https://api.veloris.app/v1"),
    STAGING("https://api-staging.veloris.io/v1"),
    SANDBOX("https://sandbox.veloris.io/v1")
}

data class VelorisConfig(
    val apiKey: String,
    val tenantId: String,
    val environment: VelorisEnvironment
)

// ── Public enums ───────────────────────────────────────────────────────────────

enum class VelorisChannel(val value: String) {
    MOBILE_IOS("mobile_ios"),
    MOBILE_ANDROID("mobile_android"),
    WEB("web")
}

enum class TransactionType(val value: String) {
    FUNDS_TRANSFER("funds_transfer"),
    BENEFICIARY_ADD("beneficiary_add"),
    LOGIN("login"),
    LIMIT_CHANGE("limit_change")
}

enum class VelorisVerdict(val value: String) {
    PASS("PASS"), STEP_UP("STEP_UP"), BLOCK("BLOCK");
    companion object { fun from(v: String) = entries.firstOrNull { it.value == v } ?: STEP_UP }
}

enum class StepUpMethod(val value: String) {
    OTP("OTP"), BIOMETRIC("BIOMETRIC"), AGENT_REVIEW("AGENT_REVIEW");
    companion object { fun from(v: String?) = entries.firstOrNull { it.value == v } }
}

enum class RiskBand(val value: String) {
    GREEN("GREEN"), AMBER("AMBER"), RED("RED");
    companion object { fun from(v: String) = entries.firstOrNull { it.value == v } ?: AMBER }
}

enum class TransactionOutcome(val value: String) {
    APPROVED("APPROVED"), DECLINED("DECLINED"),
    STEP_UP_PASSED("STEP_UP_PASSED"), STEP_UP_FAILED("STEP_UP_FAILED"),
    FRAUD_CONFIRMED("FRAUD_CONFIRMED")
}

enum class FraudType(val value: String) {
    ATO("ATO"), APP("APP"), IMPERSONATION("IMPERSONATION"), OTHER("OTHER")
}

// ── Public models ──────────────────────────────────────────────────────────────

data class VelorisTransaction(
    val type: TransactionType,
    val amount: Int? = null,
    val currency: String? = null,
    val beneficiaryId: String? = null
)

data class VelorisEvaluationResult(
    val sessionId: String,
    val trustScore: Int,
    val riskBand: RiskBand,
    val verdict: VelorisVerdict,
    val riskFactors: List<RiskFactor>,
    val recommendedAction: String,
    val stepUpMethod: StepUpMethod?,
    val evaluationId: String,
    val latencyMs: Int
) {
    val requiresStepUp: Boolean get() = verdict == VelorisVerdict.STEP_UP
    val isBlocked: Boolean get() = verdict == VelorisVerdict.BLOCK
}

data class RiskFactor(val signal: String, val severity: String, val description: String)

data class SessionMetadata(val appVersion: String, val osVersion: String, val locale: String) {
    companion object {
        fun fromDevice(context: Context): SessionMetadata {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(context.packageName, 0)
            return SessionMetadata(
                appVersion = pi.versionName ?: "unknown",
                osVersion  = Build.VERSION.RELEASE,
                locale     = java.util.Locale.getDefault().toLanguageTag()
            )
        }
    }
}

// ── Exceptions ────────────────────────────────────────────────────────────────

sealed class VelorisException(message: String) : Exception(message) {
    object SessionAlreadyActive : VelorisException("A session is already active. Call endSession() first.")
    object NoActiveSession : VelorisException("No active session. Call startSession() first.")
    data class NetworkError(val code: Int, override val message: String) : VelorisException("Network error $code: $message")
    data class RateLimited(val retryAfterSeconds: Int) : VelorisException("Rate limited. Retry after $retryAfterSeconds seconds.")
    object Unauthorized : VelorisException("Invalid or missing Veloris API key.")
    object ServerError : VelorisException("Server error. Retry with exponential backoff.")
    data class DecodingError(override val message: String) : VelorisException("Decoding error: $message")
}

// ── Internal request / response types ─────────────────────────────────────────

internal data class CreateSessionRequest(
    val userId: String,
    val tenantId: String,
    val channel: String,
    val ipAddress: String,
    val deviceId: String?,
    val metadata: SessionMetadata
)

internal data class CreateSessionResponse(
    val sessionId: String,
    val sessionToken: String,
    val expiresAt: String,
    val baselineReady: Boolean
)

internal data class EvaluateSessionRequest(
    val transaction: TransactionPayload,
    val currentIp: String,
    val timestamp: String
)

internal data class TransactionPayload(
    val type: String,
    val amount: Int?,
    val currency: String?,
    val beneficiaryId: String?
) {
    companion object {
        fun from(t: VelorisTransaction) = TransactionPayload(
            type          = t.type.value,
            amount        = t.amount,
            currency      = t.currency,
            beneficiaryId = t.beneficiaryId
        )
    }
}

internal data class EvaluateSessionResponse(
    val sessionId: String,
    val trustScore: Int,
    val riskBand: String,
    val verdict: String,
    val riskFactors: List<RiskFactorPayload>,
    val recommendedAction: String,
    val stepUpMethod: String?,
    val evaluationId: String,
    val latencyMs: Int
) {
    fun toResult() = VelorisEvaluationResult(
        sessionId         = sessionId,
        trustScore        = trustScore,
        riskBand          = RiskBand.from(riskBand),
        verdict           = VelorisVerdict.from(verdict),
        riskFactors       = riskFactors.map { RiskFactor(it.signal, it.severity, it.description) },
        recommendedAction = recommendedAction,
        stepUpMethod      = StepUpMethod.from(stepUpMethod),
        evaluationId      = evaluationId,
        latencyMs         = latencyMs
    )
}

internal data class RiskFactorPayload(val signal: String, val severity: String, val description: String)

internal data class OutcomeRequest(
    val evaluationId: String,
    val outcome: String,
    val fraudType: String?
)
