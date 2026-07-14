package dev.micr0.localmathy

data class ParsedOutput(
    val thinking: String,
    val answer: String,
    /** True once the closing </think> tag has been seen (or no think block exists). */
    val thinkingDone: Boolean,
)

private const val OPEN_TAG = "<think>"
private const val CLOSE_TAG = "</think>"

/**
 * Splits VibeThinker's raw output into the chain-of-thought and the final answer.
 * Handles partial (still-streaming) output, a missing opening tag (some runtimes
 * strip it), and output with no think block at all.
 */
fun parseModelOutput(raw: String): ParsedOutput {
    val text = raw.trimStart()
    val closeIdx = text.indexOf(CLOSE_TAG)

    if (text.startsWith(OPEN_TAG)) {
        return if (closeIdx >= 0) {
            ParsedOutput(
                thinking = text.substring(OPEN_TAG.length, closeIdx).trim(),
                answer = text.substring(closeIdx + CLOSE_TAG.length).trim(),
                thinkingDone = true,
            )
        } else {
            ParsedOutput(thinking = text.removePrefix(OPEN_TAG).trim(), answer = "", thinkingDone = false)
        }
    }

    // Opening tag was stripped by the runtime but a close tag exists:
    // everything before it is reasoning.
    if (closeIdx >= 0) {
        return ParsedOutput(
            thinking = text.substring(0, closeIdx).trim(),
            answer = text.substring(closeIdx + CLOSE_TAG.length).trim(),
            thinkingDone = true,
        )
    }

    return ParsedOutput(thinking = "", answer = text.trim(), thinkingDone = true)
}
