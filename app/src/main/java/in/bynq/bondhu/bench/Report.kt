package `in`.bynq.bondhu.bench

import android.content.Context
import org.json.JSONObject

/** Runs all checks and builds the final report as pretty JSON text. */
object Report {

    const val APP_VERSION = "0.1.0"

    suspend fun build(context: Context): String {
        val report = JSONObject()
        report.put("app", "Bondhu Bench")
        report.put("appVersion", APP_VERSION)
        report.put("generatedAtMillis", System.currentTimeMillis())

        // Each step is independent: if one fails, the others still run.
        report.put("device", runStep("DEVICE_PROFILE_FAILED") { DeviceProfile.collect(context) })
        report.put("speech", runStep("SPEECH_CHECK_FAILED") { SpeechCheck.run(context) })
        report.put("cpuBenchmark", runStep("CPU_BENCH_STEP_FAILED") { CpuBench.run(context) })

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
