package `in`.bynq.bondhu.bench

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A tiny "failure log". When something goes wrong (a check throws an error, speech is not
 * available, ...) we write ONE line of JSON to a file inside the app's private storage.
 *
 * PRIVACY RULE (please keep this true forever):
 *  - EVENTS ONLY. We store a time, a short code like "STT_UNAVAILABLE", a short technical
 *    message, and the phone model + Android version. Nothing else.
 *  - NEVER store user content: no messages, contacts, audio, file names, names or numbers.
 *  - LOCAL ONLY. The file lives in app-private storage and is never uploaded
 *    (this app does not even have the INTERNET permission).
 *  - SHARED ONLY when the user taps "Share report". Then the log is added to the report text,
 *    and the user can read it before sending it to anyone.
 *
 * File format is "JSON lines": one JSON object per line, easy to append and easy to read.
 */
object FailureLog {

    private const val FILE_NAME = "failure_log.jsonl"

    // If the file grows bigger than this, we start fresh. Keeps storage tiny.
    private const val MAX_BYTES = 100_000L

    private var file: File? = null

    /** Call once at app start (MainActivity does this). */
    fun init(context: Context) {
        file = File(context.applicationContext.filesDir, FILE_NAME)
    }

    /**
     * Record one event. This function never throws: a logger that crashes the app
     * would defeat its purpose.
     */
    @Synchronized
    fun log(code: String, message: String) {
        try {
            val f = file ?: return
            if (f.exists() && f.length() > MAX_BYTES) f.delete()
            val event = JSONObject()
                .put("time", System.currentTimeMillis())
                .put("code", code)
                // Keep messages short so nobody can accidentally store big content.
                .put("message", message.take(200))
                .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("sdk", Build.VERSION.SDK_INT)
            f.appendText(event.toString() + "\n")
        } catch (_: Throwable) {
            // Nothing sensible left to do.
        }
    }

    /** Convenience: log an exception using only its class name and message. */
    fun logError(code: String, error: Throwable) {
        log(code, "${error.javaClass.simpleName}: ${error.message ?: "no message"}")
    }

    /** Read all events (newest last) as a JSON array, to include in the shared report. */
    @Synchronized
    fun readAll(): JSONArray {
        val result = JSONArray()
        try {
            val f = file ?: return result
            if (!f.exists()) return result
            f.readLines().forEach { line ->
                if (line.isNotBlank()) {
                    try {
                        result.put(JSONObject(line))
                    } catch (_: Throwable) {
                        // Skip a damaged line.
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return result
    }
}
