package dev.micr0.localmathy

import kotlinx.coroutines.flow.Flow

/**
 * Turns a photo of a math problem into an editable text/LaTeX problem statement.
 * This is a separate, optional stage from solving: a multimodal model reads the
 * image, and its transcription pre-fills the question the user then edits and
 * hands to the (text-only) solver. Runs single-turn, like [LlmEngine].
 */
sealed interface TranscribeEvent {
    /** The vision model is loading; [detail] says which step. */
    data class Loading(val detail: String) : TranscribeEvent

    /** Cumulative transcription so far. */
    data class Partial(val text: String) : TranscribeEvent

    /** Finished; [text] is the complete transcription. */
    data class Done(val text: String) : TranscribeEvent

    data class Failed(val message: String) : TranscribeEvent
}

interface Transcriber {
    /** Which backend the vision model runs on; applied on the next transcription. */
    var backend: EngineBackend

    /**
     * Reads [imageBytes] (an encoded JPEG/PNG) into a problem statement.
     * [hint] is optional user-supplied context appended to the prompt.
     * The flow completes after [TranscribeEvent.Done] or [TranscribeEvent.Failed].
     */
    fun transcribe(imageBytes: ByteArray, hint: String): Flow<TranscribeEvent>

    /** Cancels the in-flight transcription, if any. */
    fun cancel()

    /** Releases the vision model from memory (e.g. on low-memory or before deletion). */
    fun unload() {}
}
