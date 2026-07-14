package dev.micr0.localmathy

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

private const val TAG = "LocalMathy"

/**
 * Reads a photo of a math problem into text using a multimodal model
 * (Gemma 4 E2B) on LiteRT-LM. Same runtime as [AndroidLlmEngine], but the
 * engine is created with a vision backend and each generation feeds an
 * image plus a transcribe-only prompt through the higher-level Conversation
 * API — the low-level Session/InputData path does not preprocess images.
 * Strictly single-turn: every image gets a fresh conversation.
 */
class AndroidTranscriber(
    private val context: Context,
    private val modelPathProvider: () -> String?,
) : Transcriber {

    /**
     * The vision model runs on CPU by default. Gemma 4 ships a multi-token-
     * prediction drafter section that the LiteRT GPU/OpenCL delegate rejects
     * ("Input tensor not found" at LLM executor creation), and its vision
     * adapter is CPU-only anyway, so GPU is not a safe default here. Callers
     * can still opt into Gpu/Auto explicitly.
     */
    override var backend: EngineBackend = EngineBackend.Cpu

    private var engine: Engine? = null
    private var loadedModelPath: String? = null
    private var loadedBackend: EngineBackend? = null

    @Volatile
    private var activeConversation: Conversation? = null

    override fun transcribe(imageBytes: ByteArray, hint: String): Flow<TranscribeEvent> = callbackFlow {
        val modelPath = modelPathProvider()
        if (modelPath == null) {
            trySend(TranscribeEvent.Failed("Vision model not found. Set it up first."))
            close()
            return@callbackFlow
        }

        val engine = try {
            obtainEngine(modelPath) { detail ->
                Log.i(TAG, detail)
                trySend(TranscribeEvent.Loading(detail))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vision engine init failed", e)
            trySend(TranscribeEvent.Failed("Failed to load vision model: ${e.message}"))
            close()
            return@callbackFlow
        }

        val conversation = try {
            engine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = TOP_K,
                        topP = TOP_P,
                        temperature = TEMPERATURE,
                    ),
                ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Vision conversation creation failed", e)
            trySend(TranscribeEvent.Failed("Failed to start vision session: ${e.message}"))
            close()
            return@callbackFlow
        }
        activeConversation = conversation
        trySend(TranscribeEvent.Loading("Reading the problem…"))

        val output = StringBuilder()
        val startedAt = System.currentTimeMillis()
        try {
            conversation.sendMessageAsync(
                Contents.of(Content.ImageBytes(imageBytes), Content.Text(buildPrompt(hint))),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        // Each callback delivers a delta, not the cumulative text.
                        val delta = message.contents.contents
                            .filterIsInstance<Content.Text>()
                            .joinToString("") { it.text }
                        output.append(delta)
                        trySend(TranscribeEvent.Partial(output.toString()))
                    }

                    override fun onDone() {
                        Log.i(TAG, "Transcribed ${output.length} chars in ${System.currentTimeMillis() - startedAt} ms")
                        trySend(TranscribeEvent.Done(output.toString()))
                        close()
                    }

                    override fun onError(throwable: Throwable) {
                        Log.e(TAG, "Transcription failed", throwable)
                        trySend(TranscribeEvent.Failed("Transcription failed: ${throwable.message}"))
                        close()
                    }
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            trySend(TranscribeEvent.Failed("Transcription failed: ${e.message}"))
            close()
        }

        awaitClose {
            activeConversation = null
            try {
                conversation.close()
            } catch (_: Exception) {
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel() {
        val conversation = activeConversation ?: return
        try {
            conversation.cancelProcess()
        } catch (_: Exception) {
        }
    }

    override fun unload() {
        cancel()
        try {
            engine?.close()
        } catch (_: Exception) {
        }
        engine = null
        loadedModelPath = null
        loadedBackend = null
    }

    private fun obtainEngine(modelPath: String, onStatus: (String) -> Unit): Engine {
        engine?.let { existing ->
            if (loadedModelPath == modelPath && loadedBackend == backend) return existing
            existing.close()
            engine = null
        }

        val created = when (backend) {
            EngineBackend.Gpu -> createEngine(modelPath, Backend.GPU(), onStatus)
            EngineBackend.Cpu -> createEngine(modelPath, Backend.CPU(), onStatus)
            EngineBackend.Auto -> try {
                createEngine(modelPath, Backend.GPU(), onStatus)
            } catch (e: Exception) {
                Log.w(TAG, "Vision GPU init failed, falling back to CPU", e)
                onStatus("GPU failed, falling back to CPU…")
                createEngine(modelPath, Backend.CPU(), onStatus)
            }
        }
        engine = created
        loadedModelPath = modelPath
        loadedBackend = backend
        return created
    }

    private fun createEngine(modelPath: String, llmBackend: Backend, onStatus: (String) -> Unit): Engine {
        onStatus("Loading vision model on ${llmBackend.name}…")
        val start = System.currentTimeMillis()
        val config = EngineConfig(
            modelPath = modelPath,
            backend = llmBackend,
            // The vision adapter in this model bundle is CPU-only (its section
            // declares section_backend_constraint: cpu); requesting GPU here
            // fails engine/session creation with "Input tensor not found".
            visionBackend = Backend.CPU(),
            maxNumTokens = MAX_TOKENS,
            maxNumImages = 1,
        )
        val created = Engine(config)
        try {
            created.initialize()
        } catch (t: Throwable) {
            // A failed init (e.g. GPU delegate rejecting Gemma 4) must be closed,
            // or its leaked native state can poison a subsequent CPU retry.
            try {
                created.close()
            } catch (_: Exception) {
            }
            throw t
        }
        onStatus("Vision model ready (${(System.currentTimeMillis() - start) / 1000}s)")
        return created
    }

    private fun buildPrompt(hint: String): String {
        val base =
            "Transcribe the math problem shown in the image into plain text. " +
                "Use LaTeX for any mathematical notation: wrap inline math in \$...\$ and " +
                "display math in \$\$...\$\$. Output ONLY the problem exactly as written — " +
                "no solution, no explanation, no extra commentary."
        val trimmed = hint.trim()
        return if (trimmed.isEmpty()) base else "$base\n\nAdditional context from the user: $trimmed"
    }

    private companion object {
        // Image tokens (~256) + prompt + a short transcription fit comfortably.
        const val MAX_TOKENS = 2048

        // Low temperature keeps the transcription faithful rather than creative.
        const val TOP_K = 40
        const val TOP_P = 0.9
        const val TEMPERATURE = 0.3
    }
}
