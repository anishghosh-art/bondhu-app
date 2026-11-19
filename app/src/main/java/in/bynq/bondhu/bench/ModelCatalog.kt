package `in`.bynq.bondhu.bench

import android.content.Context
import java.io.File

/**
 * One downloadable model. All three are Apache-2.0 and NOT gated on Hugging Face
 * (verified 2026-11-19), so no login or token is needed.
 */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val repo: String,          // Hugging Face repo, e.g. "litert-community/Qwen3-0.6B"
    val fileName: String,      // file inside that repo
    val approxSizeMb: Int,     // only a hint; the real size is read from the server
    val heavy: Boolean,        // true = big, may not fit on low-RAM phones
    val note: String
) {
    /** Direct download link (Hugging Face then redirects to its CDN; we follow that). */
    val url: String get() = "https://huggingface.co/$repo/resolve/main/$fileName"
}

/** The hard-coded list of models, plus where they live on the phone. */
object ModelCatalog {

    val all: List<ModelInfo> = listOf(
        ModelInfo(
            id = "qwen3-0.6b-nothink",
            displayName = "Qwen3 0.6B (no-think, int4)",
            repo = "litert-community/Qwen3-0.6B-int4",
            fileName = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            approxSizeMb = 330,
            heavy = false,
            note = "Smallest. Answers directly (no thinking text). Good first test."
        ),
        ModelInfo(
            id = "qwen3-0.6b",
            displayName = "Qwen3 0.6B",
            repo = "litert-community/Qwen3-0.6B",
            fileName = "Qwen3-0.6B.litertlm",
            approxSizeMb = 585,
            heavy = false,
            note = "Small, higher precision than int4. May write its thinking first."
        ),
        ModelInfo(
            id = "gemma4-e2b",
            displayName = "Gemma 4 E2B (it)",
            repo = "litert-community/gemma-4-E2B-it-litert-lm",
            fileName = "gemma-4-E2B-it.litertlm",
            approxSizeMb = 2600,
            heavy = true,
            note = "Heavy: about 2.6 GB. Needs a lot of free RAM; may not fit on cheap phones."
        )
    )

    fun byId(id: String): ModelInfo? = all.firstOrNull { it.id == id }

    /** Folder for model files, inside the app's private storage (no storage permission needed). */
    fun modelsDir(context: Context): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** The finished model file (may not exist yet). */
    fun finalFile(context: Context, model: ModelInfo): File = File(modelsDir(context), model.fileName)

    /** The half-downloaded file. It is renamed to [finalFile] only when complete and verified. */
    fun partFile(context: Context, model: ModelInfo): File = File(modelsDir(context), model.fileName + ".part")
}
