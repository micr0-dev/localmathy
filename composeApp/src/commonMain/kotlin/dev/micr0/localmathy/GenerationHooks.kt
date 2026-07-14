package dev.micr0.localmathy

/**
 * Platform hooks around a generation run. Android uses these to hold a
 * foreground service + wake lock while solving and to post a notification
 * when the answer is ready and the app isn't visible.
 */
interface GenerationHooks {
    fun onGenerationStarted(question: String) {}

    fun onGenerationFinished(success: Boolean) {}
}
