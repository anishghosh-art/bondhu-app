package `in`.bynq.bondhu.bench

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

/**
 * A light CPU test: multiply two small matrices again and again for about 2 seconds,
 * on ONE background thread. The score is "millions of float operations per second".
 * Higher is faster. It is only for comparing phones roughly, not a lab-grade number.
 */
object CpuBench {

    private const val SIZE = 64            // matrices are 64 x 64
    private const val DURATION_MS = 2_000L // run for about 2 seconds

    suspend fun run(context: Context): JSONObject {
        val json = JSONObject()
        try {
            val before = state(context)
            val result = withContext(Dispatchers.Default) { crunch() }
            val after = state(context)

            json.put("durationMs", result.elapsedMs)
            json.put("multiplications", result.multiplications)
            json.put("scoreMflops", result.mflops)
            json.put("checksum", result.checksum) // stops the compiler from skipping the work
            json.put("before", before)
            json.put("after", after)
        } catch (e: Throwable) {
            FailureLog.logError("CPU_BENCH_FAILED", e)
            json.put("error", "benchmark failed, see failureLog")
        }
        return json
    }

    private fun state(context: Context): JSONObject {
        val battery = DeviceProfile.batteryNow(context)
        return JSONObject()
            .put("batteryTempC", battery.opt("tempC") ?: JSONObject.NULL)
            .put("batteryPercent", battery.opt("percent") ?: JSONObject.NULL)
            .put("thermalStatus", DeviceProfile.thermalStatusName(context))
    }

    private class Result(
        val elapsedMs: Long,
        val multiplications: Int,
        val mflops: Double,
        val checksum: Double
    )

    private suspend fun crunch(): Result {
        val n = SIZE
        val a = FloatArray(n * n) { (it % 7) * 0.5f + 1f }
        val b = FloatArray(n * n) { (it % 5) * 0.25f + 1f }
        val c = FloatArray(n * n)

        val start = System.nanoTime()
        var count = 0
        var checksum = 0.0
        // Keep going until 2 seconds have passed (or the coroutine is cancelled).
        while (coroutineContext.isActive &&
            (System.nanoTime() - start) / 1_000_000L < DURATION_MS
        ) {
            multiply(a, b, c, n)
            checksum += c[0]
            count++
        }
        val elapsedNs = System.nanoTime() - start
        // One n x n multiply = n*n*n multiplies + n*n*n adds = 2*n^3 operations.
        val ops = count.toDouble() * 2.0 * n * n * n
        val mflops = ops / (elapsedNs / 1e9) / 1e6
        return Result(elapsedNs / 1_000_000L, count, Math.round(mflops * 10) / 10.0, checksum)
    }

    /** Plain triple loop: c = a * b. */
    private fun multiply(a: FloatArray, b: FloatArray, c: FloatArray, n: Int) {
        for (i in 0 until n) {
            for (j in 0 until n) {
                var sum = 0f
                for (k in 0 until n) {
                    sum += a[i * n + k] * b[k * n + j]
                }
                c[i * n + j] = sum
            }
        }
    }
}
