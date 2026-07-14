package dev.micr0.localmathy

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

/**
 * Foreground service that keeps the process (and, via a partial wake lock,
 * the CPU) alive while a generation runs with the screen off or the app in
 * the background. It does no work itself - generation runs in the
 * application-scoped [SolveState].
 */
class GenerationService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notifications.ongoing(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notifications.ONGOING_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(Notifications.ONGOING_ID, notification)
        }

        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocalMathy:generation")
                .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        // Generous upper bound; the service is stopped when generation ends.
        private const val WAKE_LOCK_TIMEOUT_MS = 30L * 60 * 1000

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, GenerationService::class.java))
            } catch (e: Exception) {
                // Background-start restrictions etc. - generation still runs,
                // just without the lifted process priority.
                Log.w("LocalMathy", "Could not start foreground service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GenerationService::class.java))
        }
    }
}

class AndroidGenerationHooks(
    private val context: Context,
    private val notifyWhenDone: () -> Boolean,
) : GenerationHooks {
    override fun onGenerationStarted(question: String) {
        Notifications.cancelResult(context)
        GenerationService.start(context)
    }

    override fun onGenerationFinished(success: Boolean) {
        GenerationService.stop(context)
        if (notifyWhenDone() && !AppForeground.isForeground) {
            Notifications.postResult(context, success)
        }
    }
}
