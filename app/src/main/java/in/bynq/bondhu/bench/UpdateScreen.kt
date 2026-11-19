package `in`.bynq.bondhu.bench

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Tab 3: check GitHub for a newer build and install it. */
@Composable
fun UpdateScreen(vm: UpdateViewModel = viewModel()) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()

    // When the user returns from the Android settings page, re-check the install permission.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshPermissionState() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Installed version: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Looks at the public GitHub release of this app. Only a few GET requests are made; " +
                "nothing about you is sent. Android will always ask you to confirm an install.",
            style = MaterialTheme.typography.bodySmall
        )

        Button(
            onClick = { vm.check() },
            enabled = !ui.checking && !ui.downloading,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (ui.checking) "Checking..." else "Check for update") }

        ui.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        val info = ui.info
        if (info != null && ui.updateAvailable) {
            Text(
                "New: ${info.versionName} (build ${info.versionCode}), " +
                    if (info.apkSizeBytes > 0) "${info.apkSizeBytes / (1024 * 1024)} MB" else "size unknown",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = { vm.startUpdate() },
                enabled = !ui.downloading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Download and install") }
        }

        if (ui.downloading) {
            val fraction = if (ui.progressTotal > 0) {
                (ui.progressBytes.toFloat() / ui.progressTotal.toFloat()).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { vm.cancel() }) { Text("Cancel") }
        }

        if (ui.needsInstallPermission) {
            // Sends the user to: Settings > Install unknown apps > Bondhu Bench.
            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } catch (e: Throwable) {
                        FailureLog.logError("UPDATE_INSTALL_FAILED", e)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Allow this app to install updates") }
        }
    }
}
