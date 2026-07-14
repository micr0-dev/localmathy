package dev.micr0.localmathy

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Generation state lives here, not in the activity, so a run keeps going
 * (and its result stays around) while the app is backgrounded.
 */
class LocalMathyApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var modelManager: AndroidModelManager
        private set
    lateinit var visionModelManager: AndroidModelManager
        private set
    lateinit var engine: AndroidLlmEngine
        private set
    lateinit var solveState: SolveState
        private set
    lateinit var settings: AppSettings
        private set
    lateinit var historyStore: HistoryStore
        private set
    lateinit var transcriber: AndroidTranscriber
        private set
    val imagePicker = AndroidImagePicker()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        settings = AppSettings(AndroidKeyValueStore(this))
        historyStore = AndroidHistoryStore(this)
        modelManager = AndroidModelManager(this, ModelInfo.Solver)
        visionModelManager = AndroidModelManager(this, ModelInfo.Vision)
        engine = AndroidLlmEngine(this) {
            modelManager.modelFile.takeIf { it.exists() }?.absolutePath
        }
        transcriber = AndroidTranscriber(this) {
            visionModelManager.modelFile.takeIf { it.exists() }?.absolutePath
        }
        solveState = SolveState(
            engine = engine,
            scope = appScope,
            transcriber = transcriber,
            hooks = AndroidGenerationHooks(this) { settings.notifyWhenDone },
            onSolved = { question, thinking, answer, elapsed ->
                buildHistoryRecord(settings, question, thinking, answer, elapsed)?.let { record ->
                    historyStore.add(record.question, record.answer, record.thinking, record.elapsedSeconds)
                }
            },
        )
    }
}

/** Tracks whether any of our activities is started, i.e. visible to the user. */
object AppForeground {
    @Volatile
    var isForeground: Boolean = false
}
