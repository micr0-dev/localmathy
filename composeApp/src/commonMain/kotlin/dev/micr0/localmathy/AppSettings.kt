package dev.micr0.localmathy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Tiny persistence abstraction; Android backs it with SharedPreferences. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

/** How much of a finished run gets saved to history. */
enum class HistoryDetail(val label: String, val description: String) {
    AnswerOnly("Answer only", "Keep just the final boxed answer"),
    AnswerAndReasoning("Answer + reasoning", "Keep the full worked solution"),
    Everything("Answer, reasoning + thinking", "Also keep the raw chain of thought"),
}

/**
 * User-facing options. Every setter persists immediately — there is no
 * save button anywhere. Values are Compose state so the UI recomposes.
 */
class AppSettings(private val store: KeyValueStore) {

    private var backendState by mutableStateOf(
        store.getString(KEY_BACKEND)
            ?.let { saved -> EngineBackend.entries.find { it.name == saved } }
            ?: EngineBackend.Auto,
    )
    private var notifyWhenDoneState by mutableStateOf(store.getBoolean(KEY_NOTIFY_DONE, true))
    private var historyEnabledState by mutableStateOf(store.getBoolean(KEY_HISTORY_ENABLED, true))
    private var historyDetailState by mutableStateOf(
        store.getString(KEY_HISTORY_DETAIL)
            ?.let { saved -> HistoryDetail.entries.find { it.name == saved } }
            ?: HistoryDetail.Everything,
    )
    private var hasSeenPhotoIntroState by mutableStateOf(store.getBoolean(KEY_PHOTO_INTRO, false))

    /** Which backend to run inference on; applied on the next question. */
    var backend: EngineBackend
        get() = backendState
        set(value) {
            backendState = value
            store.putString(KEY_BACKEND, value.name)
        }

    /** Post a notification when solving finishes while the app is backgrounded. */
    var notifyWhenDone: Boolean
        get() = notifyWhenDoneState
        set(value) {
            notifyWhenDoneState = value
            store.putBoolean(KEY_NOTIFY_DONE, value)
        }

    /** Master switch for saving solved questions to the local history DB. */
    var historyEnabled: Boolean
        get() = historyEnabledState
        set(value) {
            historyEnabledState = value
            store.putBoolean(KEY_HISTORY_ENABLED, value)
        }

    var historyDetail: HistoryDetail
        get() = historyDetailState
        set(value) {
            historyDetailState = value
            store.putString(KEY_HISTORY_DETAIL, value.name)
        }

    /** Whether the one-time photo-solving intro has been shown. */
    var hasSeenPhotoIntro: Boolean
        get() = hasSeenPhotoIntroState
        set(value) {
            hasSeenPhotoIntroState = value
            store.putBoolean(KEY_PHOTO_INTRO, value)
        }

    private companion object {
        const val KEY_BACKEND = "backend"
        const val KEY_NOTIFY_DONE = "notify_when_done"
        const val KEY_HISTORY_ENABLED = "history_enabled"
        const val KEY_HISTORY_DETAIL = "history_detail"
        const val KEY_PHOTO_INTRO = "has_seen_photo_intro"
    }
}
