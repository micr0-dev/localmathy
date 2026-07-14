package dev.micr0.localmathy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class SolvePhase { Idle, LoadingModel, Generating, Done, Error }

/** Where a captured photo is in the read-into-text pipeline. */
enum class ImagePhase { None, Cropping, Transcribing }

/**
 * Drives one single-turn run of the model. Because VibeThinker is one-shot,
 * there is no conversation history: [reset] must be called before a new question.
 */
class SolveState(
    private val engine: LlmEngine,
    private val scope: CoroutineScope,
    /** Optional multimodal model for reading a photo into the question field. */
    private val transcriber: Transcriber? = null,
    private val hooks: GenerationHooks = object : GenerationHooks {},
    /** Called with the run's results when it completes successfully. */
    private val onSolved: (question: String, thinking: String, answer: String, elapsedSeconds: Int) -> Unit =
        { _, _, _, _ -> },
) {
    var question by mutableStateOf("")
    var phase by mutableStateOf(SolvePhase.Idle)
        private set
    var thinking by mutableStateOf("")
        private set
    var thinkingDone by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf("")
        private set
    var loadingDetail by mutableStateOf("")
        private set
    var elapsedSeconds by mutableStateOf(0)
        private set

    /**
     * Answer markdown, throttled so the (relatively expensive) markdown+KaTeX
     * re-render doesn't run on every single token.
     */
    var answer by mutableStateOf("")
        private set

    // Photo → text pipeline state.
    var imagePhase by mutableStateOf(ImagePhase.None)
        private set
    var imageToCrop by mutableStateOf<ByteArray?>(null)
        private set
    var transcriptionDetail by mutableStateOf("")
        private set
    var transcriptionError by mutableStateOf("")
        private set

    private var rawAnswer = ""
    private var currentQuestion = ""
    private var lastAnswerPush: TimeMark? = null
    private var job: Job? = null
    private var timerJob: Job? = null
    private var transcribeJob: Job? = null

    val isRunning: Boolean
        get() = phase == SolvePhase.LoadingModel || phase == SolvePhase.Generating

    /** A captured photo is being cropped or read; the solve UI should yield to it. */
    val isHandlingImage: Boolean
        get() = imagePhase != ImagePhase.None

    fun submit() {
        val q = question.trim()
        if (q.isEmpty() || isRunning) return

        currentQuestion = q
        phase = SolvePhase.LoadingModel
        thinking = ""
        answer = ""
        rawAnswer = ""
        thinkingDone = false
        errorMessage = ""
        loadingDetail = ""
        elapsedSeconds = 0
        lastAnswerPush = null
        hooks.onGenerationStarted(q)

        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                elapsedSeconds += 1
            }
        }

        job = scope.launch {
            engine.generate(q).collect { event ->
                when (event) {
                    is LlmEvent.LoadingModel -> {
                        phase = SolvePhase.LoadingModel
                        loadingDetail = event.detail
                    }

                    is LlmEvent.Partial -> {
                        phase = SolvePhase.Generating
                        applyParsed(parseModelOutput(event.text), flush = false)
                    }

                    is LlmEvent.Done -> {
                        applyParsed(parseModelOutput(event.text), flush = true)
                        finish(SolvePhase.Done)
                    }

                    is LlmEvent.Failed -> {
                        errorMessage = event.message
                        finish(SolvePhase.Error)
                    }
                }
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        engine.cancel()
        // Keep whatever was produced so far.
        answer = rawAnswer
        finish(SolvePhase.Done)
    }

    /** A photo was captured/picked (null = cancelled); move it into the crop step. */
    fun onImageCaptured(bytes: ByteArray?) {
        if (bytes == null || isRunning) return
        transcriptionError = ""
        imageToCrop = bytes
        imagePhase = ImagePhase.Cropping
    }

    fun cancelImage() {
        transcribeJob?.cancel()
        transcriber?.cancel()
        imageToCrop = null
        transcriptionDetail = ""
        imagePhase = ImagePhase.None
    }

    /** The user confirmed a crop; read it into the question field with the vision model. */
    fun onCropped(bytes: ByteArray) {
        val t = transcriber ?: return
        imageToCrop = null
        transcriptionError = ""
        transcriptionDetail = ""
        imagePhase = ImagePhase.Transcribing
        // Stream the recognized text straight into the editable question field.
        question = ""

        transcribeJob = scope.launch {
            t.transcribe(bytes, "").collect { event ->
                when (event) {
                    is TranscribeEvent.Loading -> transcriptionDetail = event.detail
                    is TranscribeEvent.Partial -> question = event.text
                    is TranscribeEvent.Done -> {
                        question = event.text.trim()
                        transcriptionDetail = ""
                        imagePhase = ImagePhase.None
                    }
                    is TranscribeEvent.Failed -> {
                        transcriptionError = event.message
                        transcriptionDetail = ""
                        imagePhase = ImagePhase.None
                    }
                }
            }
        }
    }

    fun reset() {
        engine.cancel()
        transcriber?.cancel()
        job?.cancel()
        timerJob?.cancel()
        transcribeJob?.cancel()
        question = ""
        phase = SolvePhase.Idle
        thinking = ""
        answer = ""
        rawAnswer = ""
        thinkingDone = false
        errorMessage = ""
        loadingDetail = ""
        elapsedSeconds = 0
        imagePhase = ImagePhase.None
        imageToCrop = null
        transcriptionDetail = ""
        transcriptionError = ""
    }

    private fun applyParsed(parsed: ParsedOutput, flush: Boolean) {
        thinking = parsed.thinking
        thinkingDone = parsed.thinkingDone
        rawAnswer = parsed.answer

        val sinceLastPush = lastAnswerPush?.elapsedNow()?.inWholeMilliseconds ?: Long.MAX_VALUE
        if (flush || parsed.answer.isEmpty() || sinceLastPush > 250) {
            answer = parsed.answer
            lastAnswerPush = TimeSource.Monotonic.markNow()
        }
    }

    private fun finish(finalPhase: SolvePhase) {
        timerJob?.cancel()
        timerJob = null
        phase = finalPhase
        if (finalPhase == SolvePhase.Done) {
            onSolved(currentQuestion, thinking, rawAnswer, elapsedSeconds)
        }
        hooks.onGenerationFinished(finalPhase == SolvePhase.Done)
    }
}
