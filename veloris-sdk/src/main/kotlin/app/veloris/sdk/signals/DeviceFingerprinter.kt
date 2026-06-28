// DeviceFingerprinter.kt
// Veloris Sentinel Android SDK — Device Fingerprinting

package app.veloris.sdk.signals

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.util.UUID

internal class DeviceFingerprinter(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app.veloris.sdk.prefs", Context.MODE_PRIVATE
    )
    private val prefKey = "device_id"

    /** Stable, privacy-preserving device identifier. */
    val deviceId: String by lazy {
        prefs.getString(prefKey, null) ?: generateAndStore(context)
    }

    private fun generateAndStore(context: Context): String {
        // Use Android ID as base (stable per app signing key + device)
        val androidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        )
        val id = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            androidId
        } else {
            UUID.randomUUID().toString()
        }
        prefs.edit().putString(prefKey, id).apply()
        return id
    }
}
