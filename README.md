# Veloris Sentinel Android SDK

Passive behavioural biometrics for Android banking apps. Collects typing rhythm, scroll dynamics, tilt, and touch pressure to build a continuous trust score — without friction for the end user.

**[Full documentation →](https://docs.veloris.app/sentinel/android)**

---

## Requirements

- Android API 24+
- Kotlin 1.9+

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("app.veloris:sdk-android:1.0.0")
}
```

Available on [Maven Central](https://central.sonatype.com/artifact/app.veloris/sdk-android).

## Quick Start

```kotlin
import app.veloris.sdk.VelorisSDK
import app.veloris.sdk.models.*

// 1. Initialise once (Application class)
val sdk = VelorisSDK.getInstance(
    context = applicationContext,
    apiKey = "sk_live_...",
    tenantId = "your-tenant-id"
)

// 2. Start a session when the user authenticates
val sessionToken = sdk.startSession(userId = "bank-user-id")

// 3. Evaluate before a high-risk action
val result = sdk.evaluate(
    VelorisTransaction(type = TransactionType.FUNDS_TRANSFER, amount = 50000, currency = "GBP")
)

when (result.verdict) {
    VelorisVerdict.PASS    -> proceedWithTransaction()
    VelorisVerdict.STEP_UP -> requestOTP()
    VelorisVerdict.BLOCK   -> blockTransaction()
}

// 4. End the session on logout
sdk.endSession()
```

## Trust Score

| Band | Score | Meaning |
|------|-------|---------|
| 🟢 GREEN | ≥ 750 | Behavioural match — low risk |
| 🟡 AMBER | 400–749 | Partial match — consider step-up |
| 🔴 RED | < 400 | Mismatch — block or step-up required |

Scores are available after 5 sessions (baseline learning period).

## Links

- [Full Integration Guide](https://docs.veloris.app/sentinel/android)
- [API Reference](https://docs.veloris.app/sentinel/api)
- [iOS SDK](https://github.com/getveloris/veloris-ios-sdk)
- [Dashboard](https://sentinel.veloris.app)

## Support

support@veloris.app
