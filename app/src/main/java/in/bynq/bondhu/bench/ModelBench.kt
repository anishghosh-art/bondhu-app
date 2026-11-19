package `in`.bynq.bondhu.bench

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One fixed, NON-personal test question. */
data class BenchPrompt(val id: String, val language: String, val text: String)

/**
 * The model benchmark. For one downloaded model it:
 *   1. measures RAM / battery temperature / thermal status,
 *   2. loads the model (LiteRT-LM Engine) and times it,
 *   3. asks 9 fixed questions (3 Bengali, 3 Hindi, 2 English, 1 "extract intent as JSON"),
 *      timing the first token and the whole answer,
 *   4. measures RAM / temperature again, and ALWAYS closes the engine at the end.
 *
 * The result is a JSON object that goes into the shareable report, including the model's
 * replies, so a native speaker can judge the Bengali/Hindi quality.
 *
 * LiteRT-LM API used (library version 0.16.1, docs: https://ai.google.dev/edge/litert-lm/android):
 *   EngineConfig(modelPath, backend, cacheDir) -> Engine(config).initialize()
 *   engine.createConversation(ConversationConfig(maxOutputToken = N))
 *   conversation.sendMessageAsync(text) : Flow<Message>      (streaming)
 *   conversation.getBenchmarkInfo()                          (token counts, if available)
 */
object ModelBench {

    const val LIBRARY_VERSION = "0.16.1"
    const val MAX_OUTPUT_TOKENS = 120
    const val PROMPT_TIMEOUT_MS = 90_000L

    // Fixed test prompts. They contain no personal data.
    val prompts: List<BenchPrompt> = listOf(
        BenchPrompt("bn_greeting", "Bengali", "বন্ধু, তোমার আজকের দিনটা কেমন কাটছে?"),
        BenchPrompt("bn_upi", "Bengali", "UPI কী? ২ লাইনে সহজ ভাষায় বুঝিয়ে বলো।"),
        BenchPrompt("bn_friend_low", "Bengali", "আমার এক বন্ধু বলছে ওর মন ভালো নেই। ওকে একটা ছোট, আন্তরিক উত্তর লিখে দাও।"),
        BenchPrompt("hi_greeting", "Hindi", "दोस्त, आज तुम्हारा दिन कैसा चल रहा है?"),
        BenchPrompt("hi_upi", "Hindi", "UPI क्या है? 2 लाइन में आसान भाषा में समझाओ।"),
        BenchPrompt("hi_friend_low", "Hindi", "मेरा एक दोस्त कह रहा है कि उसका मन ठीक नहीं है। उसे एक छोटा, प्यार भरा जवाब लिख दो।"),
        BenchPrompt("en_upi", "English", "What is UPI? Explain in 2 simple lines."),
        BenchPrompt("en_friend_low", "English", "My friend says he is feeling low. Write a short, kind reply I can send him."),
        BenchPrompt(
            "intent_json", "Bengali+JSON",
            "Read the sentence and answer ONLY with JSON like {\"intent\": \"...\", \"amount\": ...} " +
                "and nothing else.\nSentence: রহিম কে ২০০ টাকা পাঠাতে হবে"
        )
    )

    /**
     * Runs the whole benchmark. Never throws (except when the caller cancels it);
     * problems are written into the returned JSON under "error".
     *
     * @param onStatus short text for the screen, e.g. "Prompt 3 of 9".
     */
    suspend fun run(
        context: Context,
        model: ModelInfo,
        modelFile: File,
        useGpu: Boolean,
        onStatus: (String) -> Unit
    ): JSONObject = withContext(Dispatchers.Default) {
        val result = JSONObject()
        result.put("modelId", model.id)
        result.put("modelFile", model.fileName)
        result.put("modelFileMb", modelFile.length() / MB)
        result.put("backend", if (useGpu) "GPU (experimental)" else "CPU")
        result.put("litertlmVersion", LIBRARY_VERSION)
        result.put("maxOutputTokens", MAX_OUTPUT_TOKENS)
        result.put("promptTimeoutSeconds", PROMPT_TIMEOUT_MS / 1000)
        result.put("startedAtMillis", System.currentTimeMillis())

        var engine: Engine? = null
        try {
            result.put("before", snapshot(context))

            // ---- 1. Load the model ----
            onStatus("Loading model... (can take 10-60 seconds)")
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = if (useGpu) Backend.GPU() else Backend.CPU(),
                cacheDir = context.cacheDir.path // may make the 2nd load faster
            )
            val loadStart = System.nanoTime()
            val eng = Engine(config)
            engine = eng
            eng.initialize() // blocking; we are on a background thread
            result.put("engineLoadMs", (System.nanoTime() - loadStart) / 1_000_000)
            result.put("afterLoad", snapshot(context))

            // ---- 2. Ask the fixed prompts ----
            val promptResults = JSONArray()
            result.put("prompts", promptResults)
            var consecutiveErrors = 0
            for ((index, prompt) in prompts.withIndex()) {
                onStatus("Prompt ${index + 1} of ${prompts.size} (${prompt.language})")
                val one = runPrompt(eng, prompt)
                promptResults.put(one)
                consecutiveErrors = if (one.has("error")) consecutiveErrors + 1 else 0
                if (consecutiveErrors >= 3) {
                    result.put("note", "Stopped early: 3 prompts failed in a row.")
                    break
                }
            }
            result.put("summary", summarize(promptResults))
        } catch (e: CancellationException) {
            result.put("error", "cancelled by user")
            throw e
        } catch (e: Throwable) {
            // Includes OutOfMemoryError and LiteRT-LM's own exceptions.
            FailureLog.log("MODEL_BENCH_FAILED", "${model.id}: ${e.javaClass.simpleName}: ${e.message ?: "no message"}")
            result.put("error", "${e.javaClass.simpleName}: ${(e.message ?: "no message").take(200)}")
        } finally {
            // Always free the model's memory, whatever happened.
            try {
                engine?.close()
            } catch (_: Throwable) {
            }
            try {
                result.put("after", snapshot(context))
            } catch (_: Throwable) {
            }
        }
        result
    }

    /** Asks ONE prompt in a fresh conversation (so earlier answers cannot influence it). */
    private suspend fun runPrompt(engine: Engine, prompt: BenchPrompt): JSONObject {
        val out = JSONObject()
        out.put("id", prompt.id)
        out.put("language", prompt.language)
        out.put("prompt", prompt.text)

        val reply = StringBuilder()
        val channels = mutableMapOf<String, StringBuilder>() // e.g. "thought" text, kept separate
        var firstTokenMs = -1L
        var chunks = 0
        var timedOut = false
        var conversation: Conversation? = null
        val start = System.nanoTime()
        try {
            val conv = engine.createConversation(ConversationConfig(maxOutputToken = MAX_OUTPUT_TOKENS))
            conversation = conv
            try {
                withTimeout(PROMPT_TIMEOUT_MS) {
                    conv.sendMessageAsync(prompt.text).collect { message ->
                        if (firstTokenMs < 0) firstTokenMs = (System.nanoTime() - start) / 1_000_000
                        chunks++
                        // Each streamed Message holds the next piece of text.
                        for (part in message.contents.contents) {
                            if (part is Content.Text) reply.append(part.text)
                        }
                        for ((name, text) in message.channels) {
                            channels.getOrPut(name) { StringBuilder() }.append(text)
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                timedOut = true
                try {
                    conv.cancelProcess() // ask the model to stop generating
                } catch (_: Throwable) {
                }
            }
            val totalMs = (System.nanoTime() - start) / 1_000_000

            out.put("reply", reply.toString())
            out.put("replyChars", reply.length)
            out.put("timedOut", timedOut)
            out.put("firstTokenMs", if (firstTokenMs >= 0) firstTokenMs else JSONObject.NULL)
            out.put("totalMs", totalMs)
            out.put("streamedChunks", chunks)
            if (channels.isNotEmpty()) {
                val ch = JSONObject()
                for ((name, text) in channels) ch.put(name, text.toString())
                out.put("channels", ch)
            }
            addTokenStats(out, conv, reply.length, firstTokenMs, totalMs)

            if (prompt.id == "intent_json") out.put("intentCheck", checkIntentJson(reply.toString()))
        } catch (e: Throwable) {
            if (e is CancellationException) throw e // the user pressed Cancel
            FailureLog.log("MODEL_BENCH_PROMPT_FAILED", "${prompt.id}: ${e.javaClass.simpleName}: ${e.message ?: "no message"}")
            out.put("error", "${e.javaClass.simpleName}: ${(e.message ?: "no message").take(200)}")
        } finally {
            try {
                conversation?.close()
            } catch (_: Throwable) {
            }
        }
        return out
    }

    /**
     * Token numbers. If the library reports real token counts we use them; otherwise we make
     * an ESTIMATE (about 3 characters per token) and say so in "tokensSource".
     */
    // getBenchmarkInfo() is marked experimental by the library, so we must opt in explicitly.
    @OptIn(ExperimentalApi::class)
    private fun addTokenStats(out: JSONObject, conv: Conversation, chars: Int, firstTokenMs: Long, totalMs: Long) {
        var tokens = -1
        var tokensPerSec = -1.0
        try {
            val info = conv.getBenchmarkInfo()
            if (info.lastDecodeTokenCount > 0) {
                tokens = info.lastDecodeTokenCount
                tokensPerSec = info.lastDecodeTokensPerSecond
                out.put("prefillTokens", info.lastPrefillTokenCount)
                out.put("prefillTokensPerSecond", round1(info.lastPrefillTokensPerSecond))
                out.put("libraryTimeToFirstTokenMs", (info.timeToFirstTokenInSecond * 1000).toLong())
            }
        } catch (_: Throwable) {
            // Benchmark info not available: fall back to the estimate below.
        }
        if (tokens > 0 && tokensPerSec > 0) {
            out.put("tokensSource", "litertlm benchmarkInfo (real)")
        } else {
            tokens = (chars / 3.0).toInt() // ESTIMATE
            val decodeMs = if (firstTokenMs in 0 until totalMs) totalMs - firstTokenMs else totalMs
            tokensPerSec = if (decodeMs > 0) tokens * 1000.0 / decodeMs else 0.0
            out.put("tokensSource", "estimate (chars/3)")
        }
        out.put("outputTokens", tokens)
        out.put("tokensPerSecond", round1(tokensPerSec))
    }

    /** Looks at the intent answer: is it valid JSON, and does it say amount = 200? */
    private fun checkIntentJson(reply: String): JSONObject {
        val check = JSONObject()
        try {
            val s = reply.indexOf('{')
            val e = reply.lastIndexOf('}')
            if (s < 0 || e <= s) {
                check.put("validJson", false)
                return check
            }
            val json = JSONObject(reply.substring(s, e + 1))
            check.put("validJson", true)
            check.put("intent", json.opt("intent") ?: JSONObject.NULL)
            check.put("amount", json.opt("amount") ?: JSONObject.NULL)
            check.put("amountIs200", json.optDouble("amount", -1.0) == 200.0)
        } catch (_: Throwable) {
            check.put("validJson", false)
        }
        return check
    }

    /** Averages over the prompts that worked. */
    private fun summarize(results: JSONArray): JSONObject {
        var ok = 0
        var ttft = 0.0
        var ttftCount = 0
        var tps = 0.0
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            if (r.has("error") || r.optBoolean("timedOut")) continue
            ok++
            val first = r.optLong("firstTokenMs", -1L)
            if (first >= 0) {
                ttft += first
                ttftCount++
            }
            tps += r.optDouble("tokensPerSecond", 0.0)
        }
        val s = JSONObject()
        s.put("promptsOk", ok)
        s.put("promptsTotal", results.length())
        s.put("avgFirstTokenMs", if (ttftCount > 0) (ttft / ttftCount).toLong() else JSONObject.NULL)
        s.put("avgTokensPerSecond", if (ok > 0) round1(tps / ok) else JSONObject.NULL)
        return s
    }

    /** RAM, battery temperature and thermal status right now. */
    private fun snapshot(context: Context): JSONObject {
        val j = JSONObject()
        try {
            val mi = Debug.MemoryInfo()
            Debug.getMemoryInfo(mi) // this app's own memory use, in kilobytes
            j.put("nativePssMb", mi.nativePss / 1024)
            j.put("totalPssMb", mi.totalPss / 1024)
        } catch (e: Throwable) {
            FailureLog.logError("BENCH_PSS_FAILED", e)
        }
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            j.put("availableRamMb", info.availMem / MB)
        } catch (e: Throwable) {
            FailureLog.logError("BENCH_RAM_FAILED", e)
        }
        val battery = DeviceProfile.batteryNow(context)
        j.put("batteryTempC", battery.opt("tempC") ?: JSONObject.NULL)
        j.put("batteryPercent", battery.opt("percent") ?: JSONObject.NULL)
        j.put("thermalStatus", DeviceProfile.thermalStatusName(context))
        return j
    }

    /** Free RAM in bytes right now (used for the "model may not fit" warning). */
    fun availableRamBytes(context: Context): Long {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.availMem
        } catch (e: Throwable) {
            -1L
        }
    }

    private fun round1(x: Double): Double = Math.round(x * 10.0) / 10.0

    private const val MB = 1024L * 1024L
}
