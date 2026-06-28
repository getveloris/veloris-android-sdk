# Veloris Sentinel iOS SDK

Swift SDK for integrating Veloris Sentinel continuous behavioural authentication into iOS banking apps.

## Requirements

- iOS 14.0+
- Swift 5.9+
- Xcode 15+

## Installation

### Swift Package Manager (recommended)

In Xcode: **File → Add Package Dependencies**, enter:
```
https://github.com/veloris/veloris-ios-sdk
```

Or add to `Package.swift`:
```swift
dependencies: [
    .package(url: "https://github.com/veloris/veloris-ios-sdk", from: "1.0.0")
]
```

### CocoaPods
```ruby
pod 'VelorisSDK', '~> 1.0'
```

---

## Quick Start

### 1. Initialise the SDK

Initialise once, typically in `AppDelegate` or a dependency container:

```swift
import VelorisSDK

let veloris = VelorisSDK(
    apiKey: "sk_live_YOUR_API_KEY",
    tenantId: "YOUR_TENANT_UUID"
)
```

### 2. Start a session on login

```swift
func userDidLogin(userId: String) async {
    do {
        try await veloris.startSession(userId: userId)
        // Session is now active; signal collection begins automatically
    } catch {
        print("Veloris session error: \(error.localizedDescription)")
        // Non-fatal — your app continues normally; Sentinel degrades gracefully
    }
}
```

### 3. Evaluate before a sensitive transaction

```swift
func userTappedTransfer(amount: Int) async {
    let transaction = VelorisTransaction(
        type: .fundsTransfer,
        amount: amount,       // in minor units (pence/cents)
        currency: "GBP"
    )

    do {
        let result = try await veloris.evaluate(transaction: transaction)

        switch result.verdict {
        case .pass:
            proceedWithTransfer()

        case .stepUp:
            // Use result.stepUpMethod to pick the right challenge
            switch result.stepUpMethod {
            case .otp:       promptOTPChallenge()
            case .biometric: promptFaceID()
            default:         promptOTPChallenge()
            }

        case .block:
            showBlockedMessage()
        }

    } catch {
        // On SDK error, fall through to your existing fraud checks
        proceedWithTransfer()
    }
}
```

### 4. Report the transaction outcome (important for model accuracy)

```swift
try await veloris.reportOutcome(
    evaluationId: result.evaluationId,
    outcome: .approved
)

// If fraud was confirmed later:
try await veloris.reportOutcome(
    evaluationId: result.evaluationId,
    outcome: .fraudConfirmed,
    fraudType: .ato
)
```

### 5. End the session on logout

```swift
func userDidLogout() {
    veloris.endSession()
}
```

---

## Enhanced Signal Collection (Optional)

The SDK collects tilt/orientation signals automatically. For higher-fidelity keystroke and scroll signals, add these lightweight hooks:

### Keystroke dynamics

In your `UITextFieldDelegate`:

```swift
var lastKeyTime: Date?

func textField(_ textField: UITextField,
               shouldChangeCharactersIn range: NSRange,
               replacementString string: String) -> Bool {
    let now = Date()
    let interKeyInterval = lastKeyTime.map { now.timeIntervalSince($0) } ?? 0
    veloris.signalCollector.recordKeystroke(
        keyDuration: 0.08,        // typical key press duration
        interKeyInterval: interKeyInterval
    )
    lastKeyTime = now
    return true
}
```

### Scroll velocity

In your `UIScrollViewDelegate`:

```swift
func scrollViewDidScroll(_ scrollView: UIScrollView) {
    let velocity = scrollView.panGestureRecognizer.velocity(in: scrollView)
    VelorisSDK.postScrollEvent(velocity: velocity)  // no import needed; extension on SDK
}
```

### Touch pressure

In your `UIView` subclass:

```swift
override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
    super.touchesMoved(touches, with: event)
    if let touch = touches.first {
        veloris.signalCollector.recordTouch(
            force: touch.force,
            area: touch.majorRadius
        )
    }
}
```

---

## Environments

```swift
// Sandbox (for development/testing — no API key required)
let veloris = VelorisSDK(apiKey: "sk_test_...", tenantId: "...", environment: .sandbox)

// Staging
let veloris = VelorisSDK(apiKey: "sk_test_...", tenantId: "...", environment: .staging)

// Production (default)
let veloris = VelorisSDK(apiKey: "sk_live_...", tenantId: "...", environment: .production)
```

---

## Trust Score Reference

| Score | Band | Default Action |
|-------|------|----------------|
| 750–1000 | 🟢 Green | Allow — no friction |
| 400–749  | 🟡 Amber | Step-up (OTP / biometric) |
| 0–399    | 🔴 Red   | Block — human review |

---

## Performance

- **CPU overhead:** < 2% (motion sampled at 10 Hz; signals batched)
- **Memory:** < 2 MB steady-state
- **Network:** Signals uploaded in background batches every 10 seconds
- **Verdict latency:** < 150ms P99 (server-side SLA)

---

## Privacy & Data

- Raw biometric signals are transmitted to Veloris servers over TLS 1.3
- Only derived feature vectors are stored; raw signals purged within 30 days
- Full GDPR compliance: users can request erasure via `DELETE /sentinel/users/{user_id}/profile`
- No data sold or shared with third parties

See the [Veloris Data Processing Agreement](https://veloris.app/legal/dpa) for full details.

---

## Support

- Docs: https://docs.veloris.app/sentinel/ios
- Email: support@veloris.app
- Sentinel client portal: https://sentinel.veloris.app
