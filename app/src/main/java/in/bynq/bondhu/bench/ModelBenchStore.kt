package `in`.bynq.bondhu.bench

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Keeps the model bench results on the phone (private app storage) so they survive closing the app,
 * and so the "Test" tab's report can include them. Nothing is sent anywhere.
 *
 * It also keeps a tiny "bench started" marker file. If the app process dies in the middle of a
 * bench (a native crash cannot be caught in Kotlin, or Android killed us for low memory), the
 * marker is still there at the next start, and we record that as a failure event.
 */
object ModelBenchStore {

    private const val RESULTS_FILE = "model_bench_results.json"
    private const val MARKER_FILE = "model_bench_running.txt"
    private const val MAX_RESULTS = 12 // keep the file small

    @Synchronized
    fun readAll(context: Context): JSONArray {
        return try {
            val f = File(context.filesDir, RESULTS_FILE)
            if (f.exists()) JSONArray(f.readText()) else JSONArray()
        } catch (e: Throwable) {
            JSONArray()
        }
    }

    /** The newest result for a model, or null. */
    fun latestFor(context: Context, modelId: String): JSONObject? {
        val all = readAll(context)
        for (i in all.length() - 1 downTo 0) {
            val r = all.optJSONObject(i) ?: continue
            if (r.optString("modelId") == modelId) return r
        }
        return null
    }

    @Synchronized
    fun add(context: Context, result: JSONObject) {
        try {
            val all = readAll(context)
            all.put(result)
            // Keep only the newest MAX_RESULTS.
            val trimmed = JSONArray()
            val from = (all.length() - MAX_RESULTS).coerceAtLeast(0)
            for (i in from until all.length()) trimmed.put(all.get(i))
            File(context.filesDir, RESULTS_FILE).writeText(trimmed.toString())
        } catch (e: Throwable) {
            FailureLog.logError("BENCH_SAVE_FAILED", e)
        }
    }

    fun markStarted(context: Context, modelId: String) {
        try {
            File(context.filesDir, MARKER_FILE).writeText(modelId)
        } catch (_: Throwable) {
        }
    }

    fun markFinished(context: Context) {
        try {
            File(context.filesDir, MARKER_FILE).delete()
        } catch (_: Throwable) {
        }
    }

    /** Call once at app start. If a marker is left over, the last bench died abnormally. */
    fun checkLastRunDied(context: Context) {
        try {
            val f = File(context.filesDir, MARKER_FILE)
            if (!f.exists()) return
            val modelId = f.readText().take(60)
            f.delete()
            FailureLog.log("MODEL_BENCH_PROCESS_DIED", "$modelId: app stopped during bench (native crash or low memory)")
            add(
                context,
                JSONObject()
                    .put("modelId", modelId)
                    .put("startedAtMillis", System.currentTimeMillis())
                    .put("error", "app process died during the bench (native crash or killed for low memory)")
            )
        } catch (_: Throwable) {
        }
    }
}
