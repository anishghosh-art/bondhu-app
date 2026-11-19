package `in`.bynq.bondhu.bench

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A tiny "mailbox" the receiver uses to tell the Update screen what happened. */
object InstallEvents {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun post(text: String?) {
        _message.value = text
    }
}

/**
 * Android's PackageInstaller sends its progress to this receiver (see Updater.install).
 * Most important case: STATUS_PENDING_USER_ACTION = "the user must confirm". The system gives
 * us a ready-made confirmation screen (Intent) and we simply open it.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        FailureLog.init(context) // the process may be fresh; make sure logging works
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm == null) {
                    FailureLog.log("UPDATE_INSTALL_FAILED", "no confirmation screen was provided")
                    InstallEvents.post("Install failed: Android gave no confirmation screen.")
                } else {
                    try {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(confirm)
                        InstallEvents.post("Waiting for you to confirm the install on the Android screen...")
                    } catch (e: Throwable) {
                        FailureLog.logError("UPDATE_INSTALL_FAILED", e)
                        InstallEvents.post("Could not open the install screen.")
                    }
                }
            }

            PackageInstaller.STATUS_SUCCESS ->
                InstallEvents.post("Installed. The app will restart with the new version.")

            else -> {
                // STATUS_FAILURE, _BLOCKED, _CONFLICT (different signing key!), _INCOMPATIBLE, ...
                FailureLog.log("UPDATE_INSTALL_FAILED", "status=$status ${statusMessage.take(120)}")
                InstallEvents.post(
                    if (status == PackageInstaller.STATUS_FAILURE_CONFLICT) {
                        "Install failed: this build was signed with a different key. " +
                            "Uninstall the old app once, then install the new one."
                    } else {
                        "Install failed (code $status). See the failure log in the report."
                    }
                )
            }
        }
    }
}
