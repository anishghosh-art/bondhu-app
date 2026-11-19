package `in`.bynq.bondhu.bench

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** "This model is big compared to free RAM" warning. The user must tap a second time. */
data class RamWarning(val modelId: String, val modelMb: Long, val availableMb: Long)

/** State of the benchmark screen part. */
data class BenchUi(
    val runningModelId: String? = null,   // which model is being benchmarked (null = none)
    val status: String = "",              // "Prompt 3 of 9", ...
    val useGpu: Boolean = false,          // the experimental GPU switch
    val resultVersion: Int = 0            // bumps when a new result was saved (makes the UI re-read)
)

/** Runs [ModelBench] for one model at a time and stores the result. */
class ModelBenchViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(BenchUi())
    val ui: StateFlow<BenchUi> = _ui

    private val _ramWarning = MutableStateFlow<RamWarning?>(null)
    val ramWarning: StateFlow<RamWarning?> = _ramWarning

    private var job: Job? = null

    private fun context() = getApplication<Application>()

    fun setUseGpu(value: Boolean) {
        _ui.update { it.copy(useGpu = value) }
    }

    /** First tap on "Run bench". Shows the RAM warning if the model looks too big. */
    fun requestBench(model: ModelInfo) {
        if (_ui.value.runningModelId != null) return // one bench at a time
        val file = ModelCatalog.finalFile(context(), model)
        val avail = ModelBench.availableRamBytes(context())
        if (avail > 0 && file.length() > avail * 0.9) {
            _ramWarning.value = RamWarning(model.id, file.length() / MB, avail / MB)
            return
        }
        startBench(model)
    }

    /** Second tap ("Continue anyway") on the RAM warning. */
    fun confirmRamWarning() {
        val id = _ramWarning.value?.modelId ?: return
        _ramWarning.value = null
        ModelCatalog.byId(id)?.let { startBench(it) }
    }

    fun dismissRamWarning() {
        _ramWarning.value = null
    }

    private fun startBench(model: ModelInfo) {
        if (_ui.value.runningModelId != null) return
        val file = ModelCatalog.finalFile(context(), model)
        if (!file.exists()) return
        val gpu = _ui.value.useGpu
        _ui.update { it.copy(runningModelId = model.id, status = "Starting...") }
        job = viewModelScope.launch {
            var result: JSONObject? = null
            try {
                withContext(Dispatchers.IO) { ModelBenchStore.markStarted(context(), model.id) }
                result = ModelBench.run(context(), model, file, gpu) { text ->
                    _ui.update { it.copy(status = text) }
                }
            } catch (e: CancellationException) {
                // user pressed Cancel; engine was already closed inside ModelBench.run
            } catch (e: Throwable) {
                FailureLog.logError("MODEL_BENCH_FAILED", e)
                result = JSONObject().put("modelId", model.id).put("error", e.javaClass.simpleName)
            } finally {
                withContext(Dispatchers.IO + NonCancellable) {
                    ModelBenchStore.markFinished(context())
                    result?.let { ModelBenchStore.add(context(), it) }
                }
                _ui.update { it.copy(runningModelId = null, status = "", resultVersion = it.resultVersion + 1) }
            }
        }
    }

    fun cancelBench() {
        job?.cancel()
    }

    private companion object {
        const val MB = 1024L * 1024L
    }
}
