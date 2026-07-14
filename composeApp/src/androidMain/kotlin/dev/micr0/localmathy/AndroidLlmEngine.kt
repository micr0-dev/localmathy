package dev.micr0.localmathy

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.ResponseCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Session
import com.google.ai.edge.litertlm.SessionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

private const val TAG = "LocalMathy"

/**
 * Runs VibeThinker-3B with LiteRT-LM — the same runtime as
 * `litert-lm run model.litertlm --backend=gpu --max-num-tokens 4096`.
 *
 * The engine (weights loaded on the backend) is created once and reused;
 * every question gets a brand-new session so runs are strictly single-turn.
 */
class AndroidLlmEngine(
    private val context: Context,
    private val modelPathProvider: () -> String?,
) : LlmEngine {

    override var backend: EngineBackend = EngineBackend.Auto

    private var engine: Engine? = null
    private var loadedModelPath: String? = null
    private var loadedBackend: EngineBackend? = null

    @Volatile
    private var activeSession: Session? = null

    override fun generate(question: String): Flow<LlmEvent> = callbackFlow {
        val modelPath = modelPathProvider()
        if (modelPath == null) {
            trySend(LlmEvent.Failed("Model file not found. Set up the model first."))
            close()
            return@callbackFlow
        }

        val engine = try {
            obtainEngine(modelPath) { detail ->
                Log.i(TAG, detail)
                trySend(LlmEvent.LoadingModel(detail))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Engine init failed", e)
            trySend(LlmEvent.Failed("Failed to load model: ${e.message}"))
            close()
            return@callbackFlow
        }

        val session = try {
            engine.createSession(
                SessionConfig(
                    samplerConfig = SamplerConfig(
                        topK = TOP_K,
                        topP = TOP_P,
                        temperature = TEMPERATURE,
                    ),
                ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Session creation failed", e)
            trySend(LlmEvent.Failed("Failed to start session: ${e.message}"))
            close()
            return@callbackFlow
        }
        activeSession = session
        Log.i(TAG, "Session created, prefilling question (${question.length} chars)")
        trySend(LlmEvent.LoadingModel("Processing question…"))

        val output = StringBuilder()
        val startedAt = System.currentTimeMillis()
        try {
            session.generateContentStream(
                listOf(InputData.Text(question)),
                object : ResponseCallback {
                    override fun onNext(response: String) {
                        if (output.isEmpty()) {
                            Log.i(TAG, "First token after ${System.currentTimeMillis() - startedAt} ms")
                        }
                        output.append(response)
                        trySend(LlmEvent.Partial(output.toString()))
                    }

                    override fun onDone() {
                        Log.i(TAG, "Done: ${output.length} chars in ${System.currentTimeMillis() - startedAt} ms")
                        trySend(LlmEvent.Done(output.toString()))
                        close()
                    }

                    override fun onError(throwable: Throwable) {
                        Log.e(TAG, "Generation failed", throwable)
                        trySend(LlmEvent.Failed("Generation failed: ${throwable.message}"))
                        close()
                    }
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            trySend(LlmEvent.Failed("Generation failed: ${e.message}"))
            close()
        }

        awaitClose {
            activeSession = null
            try {
                session.close()
            } catch (_: Exception) {
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun cancel() {
        val session = activeSession ?: return
        try {
            session.cancelProcess()
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
            onStatus("Reloading engine (backend changed)…")
            existing.close()
            engine = null
        }

        val created = when (backend) {
            EngineBackend.Gpu -> createEngine(modelPath, Backend.GPU(), onStatus)
            EngineBackend.Cpu -> createEngine(modelPath, Backend.CPU(), onStatus)
            EngineBackend.Auto -> try {
                createEngine(modelPath, Backend.GPU(), onStatus)
            } catch (e: Exception) {
                Log.w(TAG, "GPU init failed, falling back to CPU", e)
                onStatus("GPU failed (${e.message?.take(120)}), falling back to CPU…")
                createEngine(modelPath, Backend.CPU(), onStatus)
            }
        }
        engine = created
        loadedModelPath = modelPath
        loadedBackend = backend
        return created
    }

    private fun createEngine(modelPath: String, llmBackend: Backend, onStatus: (String) -> Unit): Engine {
        onStatus("Loading model on ${llmBackend.name} (first load can take several minutes)…")
        val start = System.currentTimeMillis()
        // No cacheDir, matching the litert-lm CLI defaults: writing the
        // converted-weights cache stalled the first GPU inference for minutes.
        val config = EngineConfig(
            modelPath = modelPath,
            backend = llmBackend,
            maxNumTokens = MAX_TOKENS,
        )
        return Engine(config).also {
            it.initialize()
            onStatus("Model ready on ${llmBackend.name} (took ${(System.currentTimeMillis() - start) / 1000}s)")
        }
    }

    private companion object {
        // Reasoning models need generous budgets to finish their chain of thought.
        const val MAX_TOKENS = 4096

        // VibeThinker recommends top_k=-1 (disabled), but the GPU top-k
        // sampler's cost scales with k - full-vocab k (151936) never finishes
        // a single token on device. With top_p=0.95 the tokens beyond the top
        // few dozen carry negligible probability, so 64 approximates
        // "disabled" while staying fast on the GPU sampler.
        const val TOP_K = 64
        const val TOP_P = 0.95
        const val TEMPERATURE = 1.0
    }
}
