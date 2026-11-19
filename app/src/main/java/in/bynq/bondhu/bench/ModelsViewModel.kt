package `in`.bynq.bondhu.bench

import android.app.Application
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Everything the screen needs to know about ONE model. */
data class ModelUi(
    val info: ModelInfo,
    val serverSizeBytes: Long = -1L,   // real size from the server (-1 = not known yet)
    val downloaded: Boolean = false,
    val fileBytes: Long = 0L,          // size of the finished file on the phone
    val downloading: Boolean = false,
    val progressBytes: Long = 0L,
    val progressTotal: Long = -1L,
    val message: String? = null        // friendly message (error, cancelled, ...)
)

/** "This will use X MB of mobile data, continue?" dialog data. */
data class DataWarning(val modelId: String, val megabytes: Long)

/**
 * Handles model downloads: list, sizes, free-space check, mobile-data warning, resume, cancel,
 * delete. It lives as long as the activity, so a download keeps going when you switch tabs
 * or rotate the phone. (If Android kills the app, the .part file stays and the next
 * Download continues from there.)
 */
class ModelsViewModel(app: Application) : AndroidViewModel(app) {

    private val _models = MutableStateFlow(ModelCatalog.all.map { ModelUi(it) })
    val models: StateFlow<List<ModelUi>> = _models

    private val _warning = MutableStateFlow<DataWarning?>(null)
    val warning: StateFlow<DataWarning?> = _warning

    // One running download job per model id, so Cancel can stop it.
    private val jobs = mutableMapOf<String, Job>()

    init {
        // Read the disk and ask the server for sizes, both off the main thread.
        viewModelScope.launch(Dispatchers.IO) {
            refreshFromDisk()
            probeSizes()
        }
    }

    private fun context() = getApplication<Application>()

    private fun updateOne(id: String, change: (ModelUi) -> ModelUi) {
        _models.update { list -> list.map { if (it.info.id == id) change(it) else it } }
    }

    private fun find(id: String): ModelUi? = _models.value.firstOrNull { it.info.id == id }

    /** Checks which model files exist on the phone. Call from a background thread. */
    private fun refreshFromDisk() {
        for (model in ModelCatalog.all) {
            val file = ModelCatalog.finalFile(context(), model)
            val done = file.exists() && file.length() > 0
            updateOne(model.id) { it.copy(downloaded = done, fileBytes = if (done) file.length() else 0L) }
        }
    }

    /** Asks the server for the real size of every model we do not have yet (HEAD request only). */
    private suspend fun probeSizes() {
        if (!NetworkUtil.isOnline(context())) return
        for (model in ModelCatalog.all) {
            val ui = find(model.id) ?: continue
            if (ui.downloaded || ui.serverSizeBytes > 0) continue
            try {
                val size = HttpDownload.probeSize(model.url)
                if (size > 0) updateOne(model.id) { it.copy(serverSizeBytes = size) }
            } catch (_: Exception) {
                // Not important: the screen simply shows the approximate size.
            }
        }
    }

    /** User tapped Download. We check everything first; the real download starts in [startDownload]. */
    fun requestDownload(id: String) {
        val model = ModelCatalog.byId(id) ?: return
        if (jobs[id]?.isActive == true) return
        updateOne(id) { it.copy(message = null) }
        if (!NetworkUtil.isOnline(context())) {
            updateOne(id) { it.copy(message = "No internet connection.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Real size.
                var size = find(id)?.serverSizeBytes ?: -1L
                if (size <= 0) size = HttpDownload.probeSize(model.url)
                if (size <= 0) throw DownloadException("The server did not tell the file size")
                updateOne(id) { it.copy(serverSizeBytes = size) }

                // 2. Free space (keep 300 MB spare so the phone does not choke).
                val already = ModelCatalog.partFile(context(), model).let { if (it.exists()) it.length() else 0L }
                val needed = (size - already).coerceAtLeast(0L)
                val free = StatFs(context().filesDir.path).availableBytes
                if (free < needed + SPARE_BYTES) {
                    updateOne(id) {
                        it.copy(message = "Not enough free space. Need about ${(needed + SPARE_BYTES) / MB} MB, " +
                            "free: ${free / MB} MB.")
                    }
                    FailureLog.log("MODEL_DOWNLOAD_FAILED", "${model.id}: not enough free space")
                    return@launch
                }

                // 3. Mobile data warning, or go.
                if (NetworkUtil.isMetered(context())) {
                    _warning.value = DataWarning(id, needed / MB)
                } else {
                    startDownload(id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                reportFailure(model, e)
            }
        }
    }

    /** User said "continue" in the mobile-data dialog. */
    fun confirmDataWarning() {
        val id = _warning.value?.modelId ?: return
        _warning.value = null
        startDownload(id)
    }

    fun dismissDataWarning() {
        _warning.value = null
    }

    /** Runs the real download in the background. */
    private fun startDownload(id: String) {
        val model = ModelCatalog.byId(id) ?: return
        if (jobs[id]?.isActive == true) return
        val job = viewModelScope.launch(Dispatchers.IO) {
            val part = ModelCatalog.partFile(context(), model)
            val finalFile = ModelCatalog.finalFile(context(), model)
            val expected = find(id)?.serverSizeBytes ?: -1L
            updateOne(id) {
                it.copy(
                    downloading = true, message = null,
                    progressBytes = if (part.exists()) part.length() else 0L, progressTotal = expected
                )
            }
            try {
                HttpDownload.downloadTo(model.url, part, expected) { done, total ->
                    updateOne(id) { it.copy(progressBytes = done, progressTotal = total) }
                }
                // downloadTo already checked that the size equals the server's Content-Length.
                if (finalFile.exists()) finalFile.delete()
                if (!part.renameTo(finalFile)) throw DownloadException("Could not save the finished file")
                refreshFromDisk()
                updateOne(id) { it.copy(downloading = false, message = "Download complete.") }
            } catch (e: CancellationException) {
                updateOne(id) { it.copy(downloading = false, message = "Stopped. Tap Download to continue later.") }
                throw e
            } catch (e: Throwable) {
                reportFailure(model, e)
            } finally {
                jobs.remove(id)
            }
        }
        jobs[id] = job
    }

    /** Cancel button. The .part file stays, so a later Download resumes. */
    fun cancel(id: String) {
        jobs[id]?.cancel()
    }

    /** Delete button: removes the model and any half-downloaded part. */
    fun delete(id: String) {
        val model = ModelCatalog.byId(id) ?: return
        jobs[id]?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ModelCatalog.finalFile(context(), model).delete()
                ModelCatalog.partFile(context(), model).delete()
            } catch (e: Throwable) {
                FailureLog.logError("MODEL_DELETE_FAILED", e)
            }
            refreshFromDisk()
            updateOne(id) { it.copy(downloading = false, message = "Deleted.") }
            probeSizes()
        }
    }

    /** Logs the technical error (events only) and shows a friendly message. */
    private fun reportFailure(model: ModelInfo, e: Throwable) {
        FailureLog.log("MODEL_DOWNLOAD_FAILED", "${model.id}: ${e.javaClass.simpleName}: ${e.message ?: "no message"}")
        val friendly = when (e) {
            is UnknownHostException, is ConnectException, is SocketTimeoutException ->
                "Network problem. Check your internet, then tap Download: it continues where it stopped."
            is DownloadException -> e.message ?: "Download failed."
            is IOException ->
                if ((e.message ?: "").contains("ENOSPC")) "The phone storage is full."
                else "Download interrupted. Tap Download to continue."
            else -> "Download failed. Tap Download to try again."
        }
        updateOne(model.id) { it.copy(downloading = false, message = friendly) }
    }

    private companion object {
        const val MB = 1024L * 1024L
        const val SPARE_BYTES = 300L * MB
    }
}
