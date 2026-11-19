package `in`.bynq.bondhu.bench

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the Update tab shows. */
data class UpdateUi(
    val checking: Boolean = false,
    val info: UpdateInfo? = null,          // set after a successful check
    val updateAvailable: Boolean = false,
    val downloading: Boolean = false,
    val progressBytes: Long = 0L,
    val progressTotal: Long = -1L,
    val needsInstallPermission: Boolean = false, // user must allow "install unknown apps" first
    val message: String? = null
)

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(UpdateUi())
    val ui: StateFlow<UpdateUi> = _ui

    private var job: Job? = null

    private fun context() = getApplication<Application>()

    init {
        // Show messages that the install receiver posts (confirm / success / failure).
        viewModelScope.launch {
            InstallEvents.message.collect { text ->
                if (text != null) _ui.update { it.copy(message = text, downloading = false) }
            }
        }
    }

    /** "Check for update" button. */
    fun check() {
        if (_ui.value.checking || _ui.value.downloading) return
        if (!NetworkUtil.isOnline(context())) {
            _ui.update { it.copy(message = "No internet connection.") }
            return
        }
        _ui.update { it.copy(checking = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = Updater.fetchLatest()
                val newer = Updater.isNewer(info)
                _ui.update {
                    it.copy(
                        checking = false, info = info, updateAvailable = newer,
                        message = if (newer) "Update available: ${info.versionName} (build ${info.versionCode})"
                        else "You are up to date."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                FailureLog.logError("UPDATE_CHECK_FAILED", e)
                _ui.update { it.copy(checking = false, message = "Could not check for updates. Try again later.") }
            }
        }
    }

    /** "Download and install" button. */
    fun startUpdate() {
        val info = _ui.value.info ?: return
        if (_ui.value.downloading) return

        // Android must allow this app to install apps ("Install unknown apps").
        if (!context().packageManager.canRequestPackageInstalls()) {
            _ui.update {
                it.copy(
                    needsInstallPermission = true,
                    message = "First allow this app to install updates (button below), then tap Update again."
                )
            }
            return
        }
        if (!NetworkUtil.isOnline(context())) {
            _ui.update { it.copy(message = "No internet connection.") }
            return
        }
        InstallEvents.post(null)
        _ui.update {
            it.copy(
                downloading = true, needsInstallPermission = false, message = "Downloading...",
                progressBytes = 0L, progressTotal = info.apkSizeBytes
            )
        }
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val apk = Updater.downloadApk(context(), info) { done, total ->
                    _ui.update { it.copy(progressBytes = done, progressTotal = total) }
                }
                _ui.update { it.copy(message = "Download checked (SHA-256 OK). Opening installer...") }
                Updater.install(context(), apk)
                // From here the result arrives in InstallResultReceiver.
                _ui.update { it.copy(downloading = false) }
            } catch (e: CancellationException) {
                _ui.update { it.copy(downloading = false, message = "Stopped.") }
                throw e
            } catch (e: Throwable) {
                FailureLog.logError("UPDATE_INSTALL_FAILED", e)
                _ui.update {
                    it.copy(
                        downloading = false,
                        message = (e as? DownloadException)?.message ?: "Update failed. Try again later."
                    )
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Called when the user comes back from the Android settings page. */
    fun refreshPermissionState() {
        if (context().packageManager.canRequestPackageInstalls()) {
            _ui.update { it.copy(needsInstallPermission = false) }
        }
    }
}
