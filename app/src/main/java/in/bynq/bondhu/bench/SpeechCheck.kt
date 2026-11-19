package `in`.bynq.bondhu.bench

import android.content.Context
import android.os.Build
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Checks what speech features this phone has for Bengali, Hindi and Indian English.
 * We only ASK Android what is available. We never record audio and never speak anything.
 */
object SpeechCheck {

    // Language tag + friendly name.
    private val LANGUAGES = listOf(
        "bn-IN" to "Bengali (India)",
        "hi-IN" to "Hindi (India)",
        "en-IN" to "English (India)"
    )

    suspend fun run(context: Context): JSONObject {
        val json = JSONObject()

        // --- Speech to text (listening) ---
        try {
            val available = SpeechRecognizer.isRecognitionAvailable(context)
            json.put("sttRecognitionAvailable", available)
            if (!available) FailureLog.log("STT_UNAVAILABLE", "No speech recognition service found")
        } catch (e: Throwable) {
            json.put("sttRecognitionAvailable", JSONObject.NULL)
            FailureLog.logError("STT_CHECK_FAILED", e)
        }

        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val onDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                json.put("sttOnDeviceAvailable", onDevice)
                if (!onDevice) FailureLog.log("STT_ON_DEVICE_UNAVAILABLE", "No on-device recogniser")
            } else {
                json.put("sttOnDeviceAvailable", "needs Android 12+")
            }
        } catch (e: Throwable) {
            json.put("sttOnDeviceAvailable", JSONObject.NULL)
            FailureLog.logError("STT_ON_DEVICE_CHECK_FAILED", e)
        }

        // --- Text to speech (talking) ---
        json.put("tts", checkTts(context))
        return json
    }

    /** Starts the TTS engine, asks about each language, then shuts the engine down. */
    private suspend fun checkTts(context: Context): JSONObject {
        val result = JSONObject()
        // TextToSpeech wants to be created on the main thread.
        return withContext(Dispatchers.Main) {
            var tts: TextToSpeech? = null
            try {
                // Wait for the engine's init callback, but give up after 8 seconds.
                val status = withTimeoutOrNull(8_000L) {
                    suspendCancellableCoroutine<Int> { cont ->
                        tts = TextToSpeech(context.applicationContext) { code ->
                            if (cont.isActive) cont.resume(code)
                        }
                        cont.invokeOnCancellation { tts?.shutdown() }
                    }
                }

                if (status == null) {
                    result.put("init", "TIMEOUT")
                    FailureLog.log("TTS_INIT_TIMEOUT", "TextToSpeech did not start in 8 seconds")
                } else if (status != TextToSpeech.SUCCESS) {
                    result.put("init", "ERROR")
                    FailureLog.log("TTS_INIT_FAILED", "TextToSpeech init status $status")
                } else {
                    result.put("init", "SUCCESS")
                    val engine = tts!!
                    result.put("defaultEngine", engine.defaultEngine ?: JSONObject.NULL)
                    val langs = JSONArray()
                    for ((tag, label) in LANGUAGES) {
                        langs.put(checkOneLanguage(engine, tag, label))
                    }
                    result.put("languages", langs)
                }
            } catch (e: Throwable) {
                result.put("init", "EXCEPTION")
                FailureLog.logError("TTS_CHECK_FAILED", e)
            } finally {
                // Always release the engine so we do not leak it.
                try {
                    tts?.shutdown()
                } catch (_: Throwable) {
                }
            }
            result
        }
    }

    private fun checkOneLanguage(engine: TextToSpeech, tag: String, label: String): JSONObject {
        val item = JSONObject().put("tag", tag).put("name", label)
        try {
            val code = engine.isLanguageAvailable(Locale.forLanguageTag(tag))
            item.put("resultCode", code)
            item.put("resultName", languageResultName(code))
            // 0, 1 and 2 all mean "a voice for this language is installed and usable".
            val installed = code >= TextToSpeech.LANG_AVAILABLE
            item.put("voiceInstalled", installed)
            if (!installed) FailureLog.log("TTS_LANG_MISSING", "$tag: ${languageResultName(code)}")
        } catch (e: Throwable) {
            item.put("resultName", "EXCEPTION")
            item.put("voiceInstalled", false)
            FailureLog.logError("TTS_LANG_CHECK_FAILED", e)
        }
        return item
    }

    private fun languageResultName(code: Int): String = when (code) {
        TextToSpeech.LANG_AVAILABLE -> "LANG_AVAILABLE"
        TextToSpeech.LANG_COUNTRY_AVAILABLE -> "LANG_COUNTRY_AVAILABLE"
        TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "LANG_COUNTRY_VAR_AVAILABLE"
        TextToSpeech.LANG_MISSING_DATA -> "LANG_MISSING_DATA"
        TextToSpeech.LANG_NOT_SUPPORTED -> "LANG_NOT_SUPPORTED"
        else -> "UNKNOWN($code)"
    }
}
