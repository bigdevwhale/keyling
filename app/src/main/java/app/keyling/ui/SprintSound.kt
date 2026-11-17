package app.keyling.ui

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.keyling.MainActivity
import app.keyling.R
import app.keyling.container
import app.keyling.domain.tr
import kotlin.random.Random

/**
 * The typing in the background during a sprint: the chosen switch clatters in bursts and pauses, like someone
 * writing, until the sprint ends – also while you write in another app. Android needs a foreground service for
 * sound in the background, so a notification shows the time left and has a button to stop the sound.
 */
class SprintSound : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var sound: Sound? = null
    private var ends = 0L
    private var sw = "brown"
    private var left = 0           // keys left in this burst

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            // turned off by hand: remember it, so opening the app doesn't bring the sound back
            applicationContext.container.write { settings.update { it.copy(sprintSound = false) } }
            stopSelf(); return START_NOT_STICKY
        }
        ends = intent?.getLongExtra("ends", 0L) ?: 0L
        sw = intent?.getStringExtra("sw") ?: "brown"
        if (ends <= System.currentTimeMillis()) { stopSelf(); return START_NOT_STICKY }
        ServiceCompat.startForeground(this, ID, notification(), if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        if (sound == null) sound = Sound(this, media = true)
        main.removeCallbacksAndMessages(null)
        main.postDelayed(::tick, 800)
        return START_NOT_STICKY
    }

    /** One key, then the next after a typing gap; between bursts a thinking pause. Stops by itself at the end. */
    private fun tick() {
        if (System.currentTimeMillis() >= ends) { stopSelf(); return }
        if (left <= 0) left = 6 + Random.nextInt(30)
        left--
        val r = Random.nextFloat()
        sound?.click(sw, when { r < .16f -> "space"; r < .19f -> "backspace"; left == 0 && Random.nextFloat() < .3f -> "enter"; else -> null })
        val gap = if (left == 0) 900L + Random.nextLong(3600) else 85L + Random.nextLong(150)
        main.postDelayed(::tick, gap)
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, tr("Спринт", "Sprint"), NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, SprintSound::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CH).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(tr("Идёт спринт", "Sprint in progress")).setContentText(tr("Стучат клавиши. Пишите!", "The keys are clattering. Write!"))
            .setWhen(ends).setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
            .setOngoing(true).setSilent(true).setContentIntent(open)
            .addAction(0, tr("Выключить звук", "Stop the sound"), stop).build()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        sound?.release(); sound = null
        super.onDestroy()
    }

    companion object {
        private const val CH = "sprint"
        private const val ID = 7
        private const val STOP = "stop"

        fun start(ctx: Context, ends: Long, sw: String) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, SprintSound::class.java).putExtra("ends", ends).putExtra("sw", sw))
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, SprintSound::class.java)) }

        /** Notifications are asked for once, so the time left can be shown; the sound plays either way. */
        fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}
