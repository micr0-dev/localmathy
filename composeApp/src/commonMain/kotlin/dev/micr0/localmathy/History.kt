package dev.micr0.localmathy

import kotlinx.coroutines.flow.StateFlow

/** One solved question, as stored in the local history database. */
data class HistoryEntry(
    val id: Long,
    val question: String,
    val answer: String,
    /** Empty unless the user chose to keep the chain of thought. */
    val thinking: String,
    val elapsedSeconds: Int,
    val createdAtMillis: Long,
)

/**
 * Local, on-device storage of past questions and answers.
 * Android backs this with a SQLite database; writes are fire-and-forget.
 */
interface HistoryStore {
    /** All entries, newest first. */
    val entries: StateFlow<List<HistoryEntry>>

    fun add(question: String, answer: String, thinking: String, elapsedSeconds: Int)

    fun delete(id: Long)

    fun clear()
}

/** What [buildHistoryRecord] decided to persist for a finished run. */
data class HistoryRecord(
    val question: String,
    val answer: String,
    val thinking: String,
    val elapsedSeconds: Int,
)

/**
 * Applies the user's history settings to a finished run.
 * Returns null when nothing should be saved (history disabled, or the run
 * produced nothing worth keeping at the chosen detail level).
 */
fun buildHistoryRecord(
    settings: AppSettings,
    question: String,
    thinking: String,
    answer: String,
    elapsedSeconds: Int,
): HistoryRecord? {
    if (!settings.historyEnabled) return null
    val storedAnswer = when (settings.historyDetail) {
        HistoryDetail.AnswerOnly -> extractFinalAnswer(answer)
        else -> answer
    }
    val storedThinking = if (settings.historyDetail == HistoryDetail.Everything) thinking else ""
    if (storedAnswer.isBlank() && storedThinking.isBlank()) return null
    return HistoryRecord(question, storedAnswer, storedThinking, elapsedSeconds)
}

/**
 * Pulls the last `\boxed{...}` result out of the answer markdown for the
 * "answer only" history level. Falls back to the full answer when there is
 * no boxed result (or the braces never balance in a truncated run).
 */
internal fun extractFinalAnswer(answer: String): String {
    val marker = "\\boxed{"
    val start = answer.lastIndexOf(marker)
    if (start < 0) return answer

    var depth = 1
    var i = start + marker.length
    while (i < answer.length && depth > 0) {
        when (answer[i]) {
            '{' -> depth++
            '}' -> depth--
        }
        i++
    }
    if (depth != 0) return answer
    val content = answer.substring(start + marker.length, i - 1)
    return "$$\\boxed{" + content + "}$$"
}
