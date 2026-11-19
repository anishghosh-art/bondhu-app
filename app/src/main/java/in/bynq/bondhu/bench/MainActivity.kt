package `in`.bynq.bondhu.bench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FailureLog.init(this)
        // Did the last model bench die in the middle (native crash / low memory)? Check off the main thread.
        thread { ModelBenchStore.checkLastRunDied(applicationContext) }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

/** Title + three tabs: Test / Models / Update. */
@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableStateOf(0) }
    val titles = listOf("Test", "Models", "Update")

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Bondhu Bench  v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
        )
        Text(
            "বন্ধু বেঞ্চ — আপনার ফোনটা কেমন, দেখে নিন",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> TestScreen()
            1 -> ModelsScreen()
            else -> UpdateScreen()
        }
    }
}
