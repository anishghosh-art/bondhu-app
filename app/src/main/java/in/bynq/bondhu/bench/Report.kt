package `in`.bynq.bondhu.bench

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Runs all checks and builds the final report as pretty JSON text. */
object Report {

    // Comes from the Gradle build (single place to change the version).
    val APP_VERSION: String = BuildConfig.VERSION_NAME

    suspend fun build(context: Context): String {
        val report = JSONObject()
        report.put("app", "Bondhu Bench")
        report.put("appVersion", APP_VERSION)
        report.put("appVersionCode", BuildConfig.VERSION_CODE)
        report.put("generatedAtMillis", System.currentTimeMillis())

        // Each step is independent: if one fails, the others still run.
        report.put("device", runStep("DEVICE_PROFILE_FAILED") { DeviceProfile.collect(context) })
        report.put("speech", runStep("SPEECH_CHECK_FAILED") { SpeechCheck.run(context) })
        report.put("cpuBenchmark", runStep("CPU_BENCH_STEP_FAILED") { CpuBench.run(context) })

        // Model bench results saved by the Models tab (replies included, so a human can judge
        // Bengali/Hindi quality). Empty list if no bench was run yet.
        try {
            report.put("modelBench", withContext(Dispatchers.IO) { ModelBenchStore.readAll(context) })
        } catch (e: Throwable) {
            FailureLog.logError("MODEL_BENCH_READ_FAILED", e)
            report.put("modelBench", JSONObject().put("error", "could not read, see failureLog"))
        }

        // The log is read LAST so it includes events from the steps above.
        // It goes in the report text only; the user sees it before sharing.
        report.put("failureLog", FailureLog.readAll())

        return report.toString(2) // 2 = indent with 2 spaces (pretty JSON)
    }

    private suspend fun runStep(code: String, block: suspend () -> JSONObject): JSONObject {
        return try {
            block()
        } catch (e: Throwable) {
            FailureLog.logError(code, e)
            JSONObject().put("error", "step failed, see failureLog")
        }
    }
}
