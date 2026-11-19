package `in`.bynq.bondhu.bench

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Opens the Android share sheet with the report as plain text. */
internal fun shareReport(context: Context, text: String) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Bondhu Bench report")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Share report"))
    } catch (e: Throwable) {
        FailureLog.logError("SHARE_FAILED", e)
        Toast.makeText(context, "Could not open share menu", Toast.LENGTH_SHORT).show()
    }
}

/** Puts the report on the clipboard. */
internal fun copyReport(context: Context, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Bondhu Bench report", text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    } catch (e: Throwable) {
        FailureLog.logError("COPY_FAILED", e)
    }
}
