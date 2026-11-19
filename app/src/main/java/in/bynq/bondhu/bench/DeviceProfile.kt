package `in`.bynq.bondhu.bench

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import org.json.JSONArray
import org.json.JSONObject

/** Collects facts about the phone. Every part is wrapped so one failure cannot break the rest. */
object DeviceProfile {

    // Brands that are known to kill background apps aggressively.
    // The future Bondhu app uses this to show the right "battery optimisation" guide.
    private val AGGRESSIVE_OEMS = listOf(
        "xiaomi", "redmi", "poco", "oppo", "vivo", "realme", "oneplus", "samsung",
        "huawei", "honor", "infinix", "tecno", "itel"
    )

    fun collect(context: Context): JSONObject {
        val json = JSONObject()

        safely("DEVICE_BASIC") {
            json.put("manufacturer", Build.MANUFACTURER)
            json.put("brand", Build.BRAND)
            json.put("model", Build.MODEL)
            json.put("androidVersion", Build.VERSION.RELEASE)
            json.put("sdkInt", Build.VERSION.SDK_INT)
            json.put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            json.put("cpuCores", Runtime.getRuntime().availableProcessors())
        }

        safely("DEVICE_SOC") {
            // SOC_MANUFACTURER / SOC_MODEL only exist from Android 12 (API 31).
            if (Build.VERSION.SDK_INT >= 31) {
                json.put("socManufacturer", Build.SOC_MANUFACTURER)
                json.put("socModel", Build.SOC_MODEL)
            } else {
                // Older phones: Build.HARDWARE is the best simple hint we have.
                json.put("socManufacturer", "unknown (needs Android 12+)")
                json.put("socModel", Build.HARDWARE)
            }
        }

        safely("DEVICE_RAM") {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            json.put("totalRamMb", info.totalMem / MB)
            json.put("availableRamMb", info.availMem / MB)
            json.put("lowMemory", info.lowMemory)
        }

        safely("DEVICE_STORAGE") {
            val stat = StatFs(Environment.getDataDirectory().path)
            json.put("freeInternalStorageMb", stat.availableBytes / MB)
            json.put("totalInternalStorageMb", stat.totalBytes / MB)
        }

        safely("DEVICE_BATTERY") {
            val battery = batteryNow(context)
            json.put("batteryPercent", battery.optInt("percent", -1))
            json.put("batteryTempC", battery.opt("tempC") ?: JSONObject.NULL)
        }

        safely("DEVICE_THERMAL") {
            json.put("thermalStatus", thermalStatusName(context))
        }

        safely("DEVICE_OEM") {
            val name = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
            val match = AGGRESSIVE_OEMS.firstOrNull { name.contains(it) }
            json.put("aggressiveBackgroundKillBrand", match != null)
            json.put("oemMatched", match ?: JSONObject.NULL)
        }

        return json
    }

    /** Battery level (percent) and temperature (Celsius) right now. */
    fun batteryNow(context: Context): JSONObject {
        val result = JSONObject()
        try {
            // ACTION_BATTERY_CHANGED is a "sticky" broadcast: passing null as receiver
            // just returns the last value without registering anything.
            val intent: Intent? =
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) result.put("percent", level * 100 / scale)
                val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                // Android gives temperature in tenths of a degree Celsius.
                if (tempTenths != Int.MIN_VALUE) result.put("tempC", tempTenths / 10.0)
            }
        } catch (e: Throwable) {
            FailureLog.logError("BATTERY_READ_FAILED", e)
        }
        return result
    }

    /** Thermal status as a readable word. Needs Android 10 (API 29), which is our minSdk. */
    fun thermalStatusName(context: Context): String {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "NONE"
                PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
                PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
                PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                else -> "UNKNOWN"
            }
        } catch (e: Throwable) {
            FailureLog.logError("THERMAL_READ_FAILED", e)
            "UNAVAILABLE"
        }
    }

    private const val MB = 1024L * 1024L

    /** Runs a block; if it crashes, we log it instead of crashing the app. */
    private fun safely(code: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            FailureLog.logError(code, e)
        }
    }
}
