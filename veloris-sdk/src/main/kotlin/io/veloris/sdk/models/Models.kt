// Models.kt
// Veloris Sentinel Android SDK — Models

package io.veloris.sdk.models

import android.content.Context
import android.os.Build

// MARK: - Configuration

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

// MARK: - Public enums

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
    PASS("PASS"),
    STEP_UP("STEP_UP"),
    BLOCK("BLOCK");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.value == value } ?: STEP_UP
    }
}

enum class StepUpMethod(val value: String) {
    OTP("OTP"),
    BIOMETRIC("BIOMETRIC"),
    AGENT_REVIEW("AGENT_REVIEW");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.value == value }
    }
}

enum class RiskBand(val value: String) {
    GREEN("GREEN"),
    AMBER("AMBER"),
    RED("RED");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.value == value } ?: AMBER
    }
}

enum class TransactionOutcome(val value: String) {
    APPROVED("APPROVED"),
    DECLINED("DECLINED"),
    STEP_UP_PASSED("STEP_UP_PASSED"),
    STEP_UP_FAILED("STEP_UP_FAILED"),
    FRAUD_CONFIRMED("FRAUD_CONFIRMED")
}

enum class FraudType(val value: String) {
    ATO("ATO"),
    APP("APP"),
    IMPERSONATION("IMPERSONATION"),
    OTHER("OTHER")
}

// MARK: - Public models

/**
 * Represents a transaction to evaluate.
 * @param amount in minor currency units (pence/cents).
 */
data class VelorisTransaction(
    val type: TransactionType,
    val amount: Int? = null,
    val currency: String? = null,
    val beneficiaryId: String? = null
)

/**
 * Result of a Sentinel session evaluation.
 */
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
    /** True when the bank should prompt step-up authentication. */
    val requiresStepUp: Boolean get() = verdict == VelorisVerdict.STEP_UP

    /** True when the transaction should be hard-blocked. */
    val isBlocked: Boolean get() = verdict == VelorisVerdict.BLOCK

    companion object {
        fun from(r: EvaluateSessionResponse) = VelorisEvaluationResult(
            sessionId         = r.sessionId,
            trustScore        = r.trustScore,
            riskBand          = RiskBand.from(r.riskBand),
            verdict           = VelorisVerdict.from(r.verdict),
            riskFactors       = r.riskFactors.map { RiskFactor(it.signal, it.severity, it.description) },
            recommendedAction = r.recommendedAction,
            stepUpMethod      = StepUpMethod.from(r.stepUpMethod),
            evaluationId      = r.evaluationId,
            latencyMs         = r.latencyMs
        )
    }
}

data class RiskFactor(
    val signal: String,
    val severity: String,
    val description: String
)

data class SessionMetadata(
    val appVersion: String,
    val osVersion: String,
    val locale: String
) {
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

// MARK: - Exceptions

sealed class VelorisException(message: String) : Exception(message) {
    object SessionAlreadyActive : VelorisException(
        "A Veloris session is already active. Call endSession() first."
    )
    object NoActiveSession : VelorisException(
        "No active Veloris session. Call startSession() first."
    )
    data class NetworkError(val code: Int, override val message: String) :
        VelorisException("Network error $code: $message")
    data class RateLimited(val retryAfterSeconds: Int) :
        VelorisException("Rate limited. Retry after $retryAfterSeconds seconds.")
    object Unauthorized : VelorisException("Invalid or missing Veloris API key.")
    object ServerError : VelorisException("Veloris server error. Retry with exponential backoff.")
    data class DecodingError(override val message: String) :
        VelorisException("Decoding error: $message")
}

// MARK: - Request / Response types (internal)

internal data class CreateSessionRequest(
    val user_id: String,
    val tenant_id: String,
    val channel: String,
    val ip_address: String,
    val device_id: String?,
    val metadata: SessionMetadata
)

internal data class CreateSessionResponse(
    val session_id: String,
    val session_token: String,
    val expires_at: String,
    val baseline_ready: Boolean
) {
    val sessionId    get() = session_id
    val sessionToken get() = session_token
}

internal data class EvaluateSessionRequest(
    val transaction: TransactionPayload,
    val current_ip: String,
    val timestamp: String
)

internal data class TransactionPayload(
    val type: String,
    val amount: Int?,
    val currency: String?,
    val beneficiary_id: String?
) {
    companion object {
        fun from(t: VelorisTransaction) = TransactionPayload(
            type           = t.type.value,
            amount         = t.amount,
            currency       = t.currency,
            beneficiary_id = t.beneficiaryId
        )
    }
}

internal data class EvaluateSessionResponse(
    val session_id: String,
    val trust_score: Int,
    val risk_band: String,
    val verdict: String,
    val risk_factors: List<RiskFactorPayload>,
    val recommended_action: String,
    val step_up_method: String?,
    val evaluation_id: String,
    val latency_ms: Int
) {
    val sessionId         get() = session_id
    val trustScore        get() = trust_score
    val riskBand          get() = risk_band
    val riskFactors       get() = risk_factors
    val recommendedAction get() = recommended_action
    val stepUpMethod      get() = step_up_method
    val evaluationId      get() = evaluation_id
    val latencyMs         get() = latency_ms
}

internal data class RiskFactorPayload(
    val signal: String,
    val severity: String,
    val description: String
)

internal data class OutcomeRequest(
    val evaluation_id: String,
    val outcome: String,
    val fraud_type: String?
)
