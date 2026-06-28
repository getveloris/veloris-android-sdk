// VelorisSDKTest.kt
// Veloris Sentinel Android SDK — Unit Tests

package io.veloris.sdk

import io.veloris.sdk.models.*
import org.junit.Assert.*
import org.junit.Test

class VelorisSDKTest {

    // MARK: - Model mapping tests

    @Test
    fun `evaluation result maps PASS verdict correctly`() {
        val response = EvaluateSessionResponse(
            session_id        = "sess-001",
            trust_score       = 850,
            risk_band         = "GREEN",
            verdict           = "PASS",
            risk_factors      = emptyList(),
            recommended_action = "Allow",
            step_up_method    = null,
            evaluation_id     = "eval-001",
            latency_ms        = 55
        )

        val result = VelorisEvaluationResult.from(response)

        assertEquals(850, result.trustScore)
        assertEquals(VelorisVerdict.PASS, result.verdict)
        assertEquals(RiskBand.GREEN, result.riskBand)
        assertFalse(result.requiresStepUp)
        assertFalse(result.isBlocked)
        assertNull(result.stepUpMethod)
    }

    @Test
    fun `evaluation result maps STEP_UP verdict correctly`() {
        val response = EvaluateSessionResponse(
            session_id        = "sess-002",
            trust_score       = 490,
            risk_band         = "AMBER",
            verdict           = "STEP_UP",
            risk_factors      = listOf(
                RiskFactorPayload("NEW_DEVICE", "HIGH", "Unrecognised device")
            ),
            recommended_action = "OTP required",
            step_up_method    = "OTP",
            evaluation_id     = "eval-002",
            latency_ms        = 80
        )

        val result = VelorisEvaluationResult.from(response)

        assertTrue(result.requiresStepUp)
        assertFalse(result.isBlocked)
        assertEquals(StepUpMethod.OTP, result.stepUpMethod)
        assertEquals(1, result.riskFactors.size)
        assertEquals("NEW_DEVICE", result.riskFactors[0].signal)
    }

    @Test
    fun `evaluation result maps BLOCK verdict correctly`() {
        val response = EvaluateSessionResponse(
            session_id        = "sess-003",
            trust_score       = 90,
            risk_band         = "RED",
            verdict           = "BLOCK",
            risk_factors      = emptyList(),
            recommended_action = "Block",
            step_up_method    = null,
            evaluation_id     = "eval-003",
            latency_ms        = 120
        )

        val result = VelorisEvaluationResult.from(response)

        assertTrue(result.isBlocked)
        assertFalse(result.requiresStepUp)
    }

    // MARK: - Enum tests

    @Test
    fun `VelorisVerdict falls back to STEP_UP on unknown value`() {
        assertEquals(VelorisVerdict.STEP_UP, VelorisVerdict.from("UNKNOWN"))
    }

    @Test
    fun `RiskBand falls back to AMBER on unknown value`() {
        assertEquals(RiskBand.AMBER, RiskBand.from("UNKNOWN"))
    }

    @Test
    fun `StepUpMethod returns null for null input`() {
        assertNull(StepUpMethod.from(null))
    }

    @Test
    fun `StepUpMethod maps BIOMETRIC correctly`() {
        assertEquals(StepUpMethod.BIOMETRIC, StepUpMethod.from("BIOMETRIC"))
    }

    // MARK: - TransactionPayload tests

    @Test
    fun `TransactionPayload maps correctly from VelorisTransaction`() {
        val tx = VelorisTransaction(
            type         = TransactionType.BENEFICIARY_ADD,
            amount       = 10000,
            currency     = "EUR",
            beneficiaryId = "ben-42"
        )

        val payload = TransactionPayload.from(tx)

        assertEquals("beneficiary_add", payload.type)
        assertEquals(10000, payload.amount)
        assertEquals("EUR", payload.currency)
        assertEquals("ben-42", payload.beneficiary_id)
    }

    // MARK: - Exception tests

    @Test
    fun `VelorisException messages are descriptive`() {
        assertNotNull(VelorisException.SessionAlreadyActive.message)
        assertNotNull(VelorisException.NoActiveSession.message)
        assertNotNull(VelorisException.Unauthorized.message)
        assertNotNull(VelorisException.ServerError.message)
        assertNotNull(VelorisException.RateLimited(30).message)
        assertTrue(VelorisException.RateLimited(30).message!!.contains("30"))
    }
}
