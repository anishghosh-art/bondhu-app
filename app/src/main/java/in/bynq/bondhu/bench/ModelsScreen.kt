package `in`.bynq.bondhu.bench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Tab 2: download models, then run the model bench on a downloaded one. */
@Composable
fun ModelsScreen(
    downloads: ModelsViewModel = viewModel(),
    bench: ModelBenchViewModel = viewModel()
) {
    val context = LocalContext.current
    val models by downloads.models.collectAsStateWithLifecycle()
    val dataWarning by downloads.warning.collectAsStateWithLifecycle()
    val benchUi by bench.ui.collectAsStateWithLifecycle()
    val ramWarning by bench.ramWarning.collectAsStateWithLifecycle()

    // Keep the screen on while a bench is running (a sleeping screen can pause the test).
    val view = LocalView.current
    val benchRunning = benchUi.runningModelId != null
    DisposableEffect(benchRunning) {
        view.keepScreenOn = benchRunning
        onDispose { view.keepScreenOn = false }
    }

    // Saved bench results, read from disk off the main thread. Re-read after every finished bench.
    val results by produceState(JSONArray(), benchUi.resultVersion) {
        value = withContext(Dispatchers.IO) { ModelBenchStore.readAll(context) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Download a model, then tap Run bench. The bench asks 9 fixed questions " +
                "(Bengali, Hindi, English) and measures speed, memory and heat. " +
                "Keep the app open and the phone plugged in or well charged.",
            style = MaterialTheme.typography.bodyMedium
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = benchUi.useGpu,
                onCheckedChange = { bench.setUseGpu(it) },
                enabled = !benchRunning
            )
            Text(
                "  Use GPU (experimental: may fail or crash the app). Default is CPU.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        for (m in models) {
            ModelCard(
                ui = m,
                benchUi = benchUi,
                latestResult = latestFor(results, m.info.id),
                onDownload = { downloads.requestDownload(m.info.id) },
                onCancelDownload = { downloads.cancel(m.info.id) },
                onDelete = { downloads.delete(m.info.id) },
                onRunBench = { bench.requestBench(m.info) },
                onCancelBench = { bench.cancelBench() }
            )
        }
    }

    // Warning 1: mobile data.
    dataWarning?.let { w ->
        AlertDialog(
            onDismissRequest = { downloads.dismissDataWarning() },
            title = { Text("Use mobile data?") },
            text = {
                Text(
                    "You are not on an unlimited WiFi connection. This will use about " +
                        "${w.megabytes} MB of your data. Continue?"
                )
            },
            confirmButton = { TextButton(onClick = { downloads.confirmDataWarning() }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { downloads.dismissDataWarning() }) { Text("Cancel") } }
        )
    }

    // Warning 2: model is big compared with free RAM. The user must tap a second time.
    ramWarning?.let { w ->
        AlertDialog(
            onDismissRequest = { bench.dismissRamWarning() },
            title = { Text("Low free memory") },
            text = {
                Text(
                    "The model file is ${w.modelMb} MB but only about ${w.availableMb} MB of RAM is free. " +
                        "The app may crash or be closed by Android. Continue anyway?"
                )
            },
            confirmButton = { TextButton(onClick = { bench.confirmRamWarning() }) { Text("Continue anyway") } },
            dismissButton = { TextButton(onClick = { bench.dismissRamWarning() }) { Text("Cancel") } }
        )
    }
}

/** Newest saved result for a model id (or null). */
private fun latestFor(results: JSONArray, modelId: String): JSONObject? {
    for (i in results.length() - 1 downTo 0) {
        val r = results.optJSONObject(i) ?: continue
        if (r.optString("modelId") == modelId) return r
    }
    return null
}

@Composable
private fun ModelCard(
    ui: ModelUi,
    benchUi: BenchUi,
    latestResult: JSONObject?,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit,
    onRunBench: () -> Unit,
    onCancelBench: () -> Unit
) {
    val context = LocalContext.current
    var showReplies by remember { mutableStateOf(false) }
    val thisBenchRunning = benchUi.runningModelId == ui.info.id
    val anyBenchRunning = benchUi.runningModelId != null

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(ui.info.displayName + if (ui.info.heavy) "  [heavy]" else "", style = MaterialTheme.typography.titleSmall)
            Text(ui.info.note, style = MaterialTheme.typography.bodySmall)
            Text(sizeLine(ui), style = MaterialTheme.typography.bodySmall)

            // ---- Download progress ----
            if (ui.downloading) {
                val fraction = if (ui.progressTotal > 0) {
                    (ui.progressBytes.toFloat() / ui.progressTotal.toFloat()).coerceIn(0f, 1f)
                } else 0f
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Text(
                    "${(fraction * 100).toInt()}%  (${formatMb(ui.progressBytes)} of ${formatMb(ui.progressTotal)})  " +
                        "Keep the app open.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = onCancelDownload) { Text("Cancel download") }
            }

            ui.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            // ---- Buttons ----
            if (!ui.downloading) {
                if (!ui.downloaded) {
                    Button(onClick = onDownload) { Text("Download") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRunBench, enabled = !anyBenchRunning) { Text("Run bench") }
                        OutlinedButton(onClick = onDelete, enabled = !thisBenchRunning) { Text("Delete model") }
                    }
                }
            }

            // ---- Bench progress ----
            if (thisBenchRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) // moving bar = "working"
                Text(benchUi.status, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onCancelBench) { Text("Cancel bench") }
            }

            // ---- Last result ----
            if (latestResult != null && !thisBenchRunning) {
                Text(resultSummary(latestResult), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showReplies = !showReplies }) {
                        Text(if (showReplies) "Hide replies" else "Show replies")
                    }
                    OutlinedButton(onClick = { shareReport(context, latestResult.toString(2)) }) { Text("Share") }
                    OutlinedButton(onClick = { copyReport(context, latestResult.toString(2)) }) { Text("Copy") }
                }
                if (showReplies) {
                    Text(repliesText(latestResult), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}

/** "Downloaded: 332 MB" or "Size: 613 MB (from server)" or "About 330 MB". */
private fun sizeLine(ui: ModelUi): String = when {
    ui.downloaded -> "Downloaded: ${formatMb(ui.fileBytes)}"
    ui.serverSizeBytes > 0 -> "Size: ${formatMb(ui.serverSizeBytes)} (read from the server)"
    else -> "Size: about ${ui.info.approxSizeMb} MB (approximate)"
}

private fun formatMb(bytes: Long): String {
    if (bytes <= 0) return "? MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.2f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}

/** A few lines about the newest result. */
private fun resultSummary(r: JSONObject): String {
    if (r.has("error") && !r.has("prompts")) return "Last bench failed: ${r.optString("error")}"
    val sb = StringBuilder()
    sb.append("Last bench (${r.optString("backend")}): ")
    sb.append("load ${r.optLong("engineLoadMs", -1)} ms")
    val s = r.optJSONObject("summary")
    if (s != null) {
        sb.append(", avg first token ${s.opt("avgFirstTokenMs")} ms")
        sb.append(", avg ${s.opt("avgTokensPerSecond")} tokens/s")
        sb.append(", ${s.optInt("promptsOk")}/${s.optInt("promptsTotal")} prompts ok")
    }
    if (r.has("error")) sb.append("\nProblem: ${r.optString("error")}")
    sb.append("\nTokens/s is real if the library reports it, otherwise an estimate (see tokensSource in the report).")
    return sb.toString()
}

/** The model's replies, so a human can judge the language quality. */
private fun repliesText(r: JSONObject): String {
    val prompts = r.optJSONArray("prompts") ?: return "No replies."
    val sb = StringBuilder()
    for (i in 0 until prompts.length()) {
        val p = prompts.optJSONObject(i) ?: continue
        sb.append("[${p.optString("id")}]\n")
        sb.append(p.optString("reply", p.optString("error", "")).ifBlank { "(empty)" })
        sb.append("\n\n")
    }
    return sb.toString()
}
