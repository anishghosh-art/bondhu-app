package `in`.bynq.bondhu.bench

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** What the release says about the newest build. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkName: String,
    val sha256: String,       // expected SHA-256 of the APK, lowercase hex
    val apkUrl: String,       // where to download the APK
    val apkSizeBytes: Long    // from the GitHub asset list (-1 if unknown)
)

/**
 * In-app update logic. The CI build publishes two files to the GitHub Release "bench-latest":
 *   - the APK
 *   - latest.json  {"versionCode":N,"versionName":"x","apkName":"...","sha256":"..."}
 * We read the release, then latest.json, compare versionCode with our own, download the APK,
 * check its SHA-256 and hand it to Android's PackageInstaller. Android always asks the user to
 * confirm the install: that is normal and cannot be skipped.
 *
 * Only GET requests to api.github.com / the release assets are made. No tracking.
 */
object Updater {

    // /releases/latest would skip pre-releases (our release is a pre-release), so we use the tag.
    private const val RELEASE_URL =
        "https://api.github.com/repos/anishghosh-art/bondhu-app/releases/tags/bench-latest"

    /** Reads the release + latest.json. Throws if anything is missing or unreadable. */
    suspend fun fetchLatest(): UpdateInfo {
        val release = JSONObject(
            HttpDownload.getText(RELEASE_URL, mapOf("Accept" to "application/vnd.github+json"))
        )
        val assets = release.getJSONArray("assets")

        // Find an asset by its file name.
        fun assetUrl(name: String): String? {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") == name) return a.optString("browser_download_url")
            }
            return null
        }
        fun assetSize(name: String): Long {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") == name) return a.optLong("size", -1L)
            }
            return -1L
        }

        val jsonUrl = assetUrl("latest.json") ?: throw DownloadException("latest.json is not in the release")
        val latest = JSONObject(HttpDownload.getText(jsonUrl))
        val apkName = latest.getString("apkName")
        val apkUrl = assetUrl(apkName) ?: throw DownloadException("The APK is not in the release")
        return UpdateInfo(
            versionCode = latest.getInt("versionCode"),
            versionName = latest.optString("versionName", "?"),
            apkName = apkName,
            sha256 = latest.getString("sha256").trim().lowercase(),
            apkUrl = apkUrl,
            apkSizeBytes = assetSize(apkName)
        )
    }

    /** True if the release is newer than the app that is running now. */
    fun isNewer(info: UpdateInfo): Boolean = info.versionCode > BuildConfig.VERSION_CODE

    /**
     * Downloads the APK into the app's cache folder and verifies its SHA-256.
     * Returns the finished file. Deletes the file and throws if the hash does not match.
     */
    suspend fun downloadApk(context: Context, info: UpdateInfo, onProgress: (Long, Long) -> Unit): File {
        val dir = File(context.cacheDir, "update")
        withContext(Dispatchers.IO) {
            // Always start clean: remove old update files (the APK is small, no resume needed).
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
        }
        val part = File(dir, info.apkName + ".part")
        val done = File(dir, info.apkName)
        HttpDownload.downloadTo(info.apkUrl, part, info.apkSizeBytes, onProgress)

        val actual = withContext(Dispatchers.IO) { sha256Of(part) }
        if (actual != info.sha256) {
            withContext(Dispatchers.IO) { part.delete() }
            throw DownloadException("The downloaded file does not match its checksum. Not installing.")
        }
        withContext(Dispatchers.IO) {
            if (done.exists()) done.delete()
            if (!part.renameTo(done)) throw DownloadException("Could not save the update file")
        }
        return done
    }

    /** SHA-256 of a file as lowercase hex. */
    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Gives the APK to Android's PackageInstaller. Android then sends a result to
     * [InstallResultReceiver]; for the normal case ("please confirm") that receiver opens
     * the system confirmation screen.
     */
    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setSize(apk.length())
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            // Copy the APK into the install session.
            apk.inputStream().use { input ->
                session.openWrite("bondhu-update.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            // The system fills in the result details itself, so the PendingIntent must be MUTABLE
            // (required on Android 12+). The Intent is explicit (names our receiver class), which
            // Android 14+ requires for mutable PendingIntents.
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }
}
