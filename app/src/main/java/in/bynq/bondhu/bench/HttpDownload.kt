package `in`.bynq.bondhu.bench

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** A download problem with a short, friendly-safe message (never contains user data). */
class DownloadException(message: String) : Exception(message)

/**
 * Small download helper built on plain HttpURLConnection (no extra libraries).
 *
 *  - follows redirects by hand (Hugging Face and GitHub both redirect to a CDN),
 *  - can RESUME: if a ".part" file already exists we ask the server only for the missing bytes,
 *  - reports progress,
 *  - stops quickly when the coroutine is cancelled (the Cancel button).
 *
 * Everything here runs on Dispatchers.IO, never on the main thread.
 */
object HttpDownload {

    private const val MAX_REDIRECTS = 8
    private const val BUFFER_BYTES = 64 * 1024

    /**
     * Opens [url] and follows redirects. Returns a connection whose response has already arrived
     * (check responseCode yourself). [method] is "GET" or "HEAD". [rangeFrom] > 0 asks only for
     * the bytes from that position on (HTTP Range header).
     */
    private fun open(url: String, method: String, rangeFrom: Long, extraHeaders: Map<String, String>): HttpURLConnection {
        var current = url
        var redirects = 0
        while (true) {
            if (!current.startsWith("https://")) throw DownloadException("Only https links are allowed")
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false // we follow by hand so the Range header is kept
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.requestMethod = method
            // "identity" = please do not compress, so Content-Length matches the real file size.
            conn.setRequestProperty("Accept-Encoding", "identity")
            conn.setRequestProperty("User-Agent", "BondhuBench/${BuildConfig.VERSION_NAME}")
            for ((k, v) in extraHeaders) conn.setRequestProperty(k, v)
            if (rangeFrom > 0) conn.setRequestProperty("Range", "bytes=$rangeFrom-")

            val code = conn.responseCode
            if (code in 301..308 && code != 304) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw DownloadException("Redirect without a target")
                if (++redirects > MAX_REDIRECTS) throw DownloadException("Too many redirects")
                current = URL(URL(current), location).toString() // handles relative links too
                continue
            }
            return conn
        }
    }

    /** Reads a small text response (used for the GitHub release JSON and latest.json). */
    suspend fun getText(url: String, extraHeaders: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val conn = open(url, "GET", 0L, extraHeaders)
            try {
                if (conn.responseCode != 200) throw DownloadException("Server answered HTTP ${conn.responseCode}")
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }

    /**
     * Asks the server how big the file is. Returns -1 if it does not say.
     * Tries a cheap HEAD request first, then a 1-byte Range request as a fallback.
     */
    suspend fun probeSize(url: String): Long = withContext(Dispatchers.IO) {
        try {
            val head = open(url, "HEAD", 0L, emptyMap())
            try {
                if (head.responseCode == 200 && head.contentLengthLong > 0) return@withContext head.contentLengthLong
            } finally {
                head.disconnect()
            }
        } catch (_: Exception) {
            // fall through to the fallback below
        }
        val conn = open(url, "GET", 1L, emptyMap()) // asks for "bytes=1-"
        try {
            if (conn.responseCode == 206) totalFromContentRange(conn.getHeaderField("Content-Range")) else -1L
        } finally {
            conn.disconnect()
        }
    }

    /** "bytes 5-99/100" -> 100. Returns -1 if it cannot be read. */
    private fun totalFromContentRange(header: String?): Long {
        val total = header?.substringAfterLast('/')?.trim()
        return total?.toLongOrNull() ?: -1L
    }

    /**
     * Downloads [url] into [part] (resuming if [part] already has bytes).
     *
     * @param expectedTotal the full size we expect, or <= 0 if unknown.
     * @param onProgress called with (bytesSoFar, totalBytesOrMinus1), at most about 4 times a second.
     * @return the size of [part] when finished. The caller renames it when happy.
     * @throws DownloadException when the server misbehaves or the final size is wrong.
     */
    suspend fun downloadTo(
        url: String,
        part: File,
        expectedTotal: Long,
        onProgress: (Long, Long) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        part.parentFile?.mkdirs()
        var have = if (part.exists()) part.length() else 0L
        if (expectedTotal > 0 && have > expectedTotal) { // damaged leftover: start again
            part.delete()
            have = 0L
        }
        if (expectedTotal > 0 && have == expectedTotal) { // already complete, nothing to fetch
            onProgress(have, expectedTotal)
            return@withContext have
        }

        val conn = open(url, "GET", have, emptyMap())
        try {
            val code = conn.responseCode
            val append: Boolean
            var total: Long
            when (code) {
                206 -> { // server accepted our Range: continue where we stopped
                    append = true
                    total = totalFromContentRange(conn.getHeaderField("Content-Range"))
                }
                200 -> { // server ignored Range (or we started from 0): write from the beginning
                    append = false
                    have = 0L
                    total = conn.contentLengthLong
                }
                else -> {
                    if (code == 416) part.delete() // our .part file does not fit; next try starts fresh
                    throw DownloadException("Server answered HTTP $code")
                }
            }
            if (total <= 0) total = expectedTotal
            if (expectedTotal > 0 && total > 0 && total != expectedTotal) {
                throw DownloadException("Server file size changed")
            }

            var written = have
            var lastReport = 0L
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        coroutineContext.ensureActive() // throws if the user pressed Cancel
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        written += n
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 250) {
                            lastReport = now
                            onProgress(written, total)
                        }
                    }
                    output.flush()
                }
            }
            onProgress(written, total)

            val finalSize = part.length()
            if (total > 0 && finalSize != total) {
                throw DownloadException("Download incomplete ($finalSize of $total bytes)")
            }
            finalSize
        } finally {
            conn.disconnect()
        }
    }
}
