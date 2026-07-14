package dev.micr0.localmathy

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import platform.UIKit.UIViewController

/**
 * iOS entry point. Inference on iOS (LiteRT-LM Swift runtime) is not wired up
 * yet — this exists so the shared UI compiles and renders.
 */
private val stubSettings by lazy { AppSettings(InMemoryKeyValueStore()) }
private val stubSolveState by lazy { SolveState(StubLlmEngine, MainScope()) }

@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController {
    App(
        modelManager = StubModelManager,
        visionModelManager = StubVisionModelManager,
        engine = StubLlmEngine,
        transcriber = null,
        imagePicker = null,
        solveState = stubSolveState,
        settings = stubSettings,
        historyStore = StubHistoryStore,
    )
}

private class InMemoryKeyValueStore : KeyValueStore {
    private val strings = mutableMapOf<String, String>()
    private val bools = mutableMapOf<String, Boolean>()
    override fun getString(key: String): String? = strings[key]
    override fun putString(key: String, value: String) { strings[key] = value }
    override fun getBoolean(key: String, default: Boolean): Boolean = bools[key] ?: default
    override fun putBoolean(key: String, value: Boolean) { bools[key] = value }
}

private object StubHistoryStore : HistoryStore {
    override val entries: StateFlow<List<HistoryEntry>> = MutableStateFlow(emptyList())
    override fun add(question: String, answer: String, thinking: String, elapsedSeconds: Int) = Unit
    override fun delete(id: Long) = Unit
    override fun clear() = Unit
}

private object StubModelManager : ModelManager {
    override val spec: ModelSpec = ModelInfo.Solver
    override val state: StateFlow<ModelState> =
        MutableStateFlow(ModelState.Error("iOS support is not implemented yet."))

    override fun startDownload() = Unit
    override fun cancelTransfer() = Unit
    override fun requestImport() = Unit
    override fun deleteModel() = Unit
}

private object StubVisionModelManager : ModelManager {
    override val spec: ModelSpec = ModelInfo.Vision
    override val state: StateFlow<ModelState> = MutableStateFlow(ModelState.Missing)

    override fun startDownload() = Unit
    override fun cancelTransfer() = Unit
    override fun requestImport() = Unit
    override fun deleteModel() = Unit
}

private object StubLlmEngine : LlmEngine {
    override var backend: EngineBackend = EngineBackend.Auto

    override fun generate(question: String): Flow<LlmEvent> =
        flowOf(LlmEvent.Failed("iOS support is not implemented yet."))

    override fun cancel() = Unit
}
