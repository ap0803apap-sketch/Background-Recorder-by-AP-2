package com.ap.background.recorder.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Helper class to retrieve telephony and device identification info.
 * Note: Modern Android versions (10+) strictly prohibit access to hardware identifiers 
 * like IMEI for non-system apps to protect user privacy.
 */
class TelephonyHelper(private val context: Context) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    fun getSimInfo(): String {
        return try {
            val operatorName = telephonyManager.networkOperatorName
            val countryIso = telephonyManager.networkCountryIso.uppercase()
            val phoneNum = getPhoneNumber()
            val simStr = if (operatorName.isNullOrEmpty()) "Unknown" else "$operatorName ($countryIso)"
            "SIM: $simStr | No: $phoneNum"
        } catch (e: Exception) {
            "SIM: Unavailable"
        }
    }

    @SuppressLint("HardwareIds")
    private fun getPhoneNumber(): String {
        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED) {
                // Returns the phone number string for line 1, e.g. the MSISDN for a GSM phone.
                // Note: This often returns null/empty if the number is not stored on the SIM.
                telephonyManager.line1Number ?: "Not Stored on SIM"
            } else {
                "No Permission"
            }
        } catch (e: Exception) {
            "Restricted"
        }
    }

    @SuppressLint("HardwareIds")
    fun getDeviceIdentifier(): String {
        // Since minSdk is 31, IMEI access is strictly forbidden by Android OS policy.
        // We use the full Android ID as the unique device identifier.
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            "ID: ${androidId.uppercase()} | IMEI: Restricted (API 29+)"
        } catch (e: Exception) {
            "ID: Unavailable"
        }
    }
}
