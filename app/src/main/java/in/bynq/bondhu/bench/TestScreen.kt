package `in`.bynq.bondhu.bench

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The "Test" tab is always in one of these three states. */
sealed interface BenchState {
    data object Idle : BenchState
    data object Running : BenchState
    data class Done(val reportText: String) : BenchState
}

/** Holds the state and starts the quick phone test. It survives screen rotation. */
class BenchViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<BenchState>(BenchState.Idle)
    val state: StateFlow<BenchState> = _state

    fun runTest() {
        if (_state.value is BenchState.Running) return // ignore double taps
        _state.value = BenchState.Running
        viewModelScope.launch {
            val text = try {
                Report.build(getApplication())
            } catch (e: Throwable) {
                FailureLog.logError("RUN_TEST_FAILED", e)
                "Something went wrong: ${e.javaClass.simpleName}"
            }
            _state.value = BenchState.Done(text)
        }
    }

    fun reset() {
        _state.value = BenchState.Idle
    }
}

/** Tab 1: the quick device / speech / CPU test and the shareable report. */
@Composable
fun TestScreen(vm: BenchViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (val s = state) {
            is BenchState.Idle -> {
                Text(
                    "Tap the button. The test takes about 10 seconds. " +
                        "It checks your phone, speech support and CPU speed. " +
                        "The report also includes any model bench results from the Models tab. " +
                        "Nothing is sent anywhere unless you tap Share."
                )
                Button(onClick = { vm.runTest() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Run test")
                }
            }

            is BenchState.Running -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text("Testing... please keep the app open.")
                }
            }

            is BenchState.Done -> ResultsView(s.reportText, onAgain = { vm.reset() })
        }
    }
}

@Composable
fun ResultsView(reportText: String, onAgain: () -> Unit) {
    val context = LocalContext.current

    Text("Results", style = MaterialTheme.typography.titleMedium)

    // The report, in a scrollable box.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(reportText, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { shareReport(context, reportText) }) { Text("Share report") }
        OutlinedButton(onClick = { copyReport(context, reportText) }) { Text("Copy") }
        OutlinedButton(onClick = onAgain) { Text("Run again") }
    }
}
