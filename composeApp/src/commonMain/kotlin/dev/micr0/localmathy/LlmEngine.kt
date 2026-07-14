package dev.micr0.localmathy

import kotlinx.coroutines.flow.Flow

/**
 * Events emitted while VibeThinker works on a single question.
 * The model is single-turn: every call to [LlmEngine.generate] runs in a
 * fresh session, and follow-up questions are not possible.
 */
enum class EngineBackend { Auto, Gpu, Cpu }

sealed interface LlmEvent {
    /** The model is being loaded into the runtime; [detail] says which step. */
    data class LoadingModel(val detail: String) : LlmEvent

    /** Cumulative raw output so far, including any <think>...</think> block. */
    data class Partial(val text: String) : LlmEvent

    /** Generation finished; [text] is the complete raw output. */
    data class Done(val text: String) : LlmEvent

    data class Failed(val message: String) : LlmEvent
}

interface LlmEngine {
    /**
     * Runs one single-turn generation for [question].
     * The flow completes after emitting [LlmEvent.Done] or [LlmEvent.Failed].
     */
    fun generate(question: String): Flow<LlmEvent>

    /** Cancels the in-flight generation, if any. */
    fun cancel()

    /** Releases the loaded model from memory, e.g. before deleting its file. */
    fun unload() {}

    /**
     * Which backend to run on. Auto tries GPU and falls back to CPU.
     * Takes effect on the next generation (the engine reloads if needed).
     */
    var backend: EngineBackend
}
