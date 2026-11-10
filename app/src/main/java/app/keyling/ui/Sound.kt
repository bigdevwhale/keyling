package app.keyling.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import kotlin.random.Random

/**
 * Real recorded switch sounds (tplai/kbsim, MIT): five presses and a release per switch,
 * plus space, enter and backspace for the switches that have them.
 */
class Sound(ctx: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(8)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ids = HashMap<String, Int>()
    private val main = Handler(Looper.getMainLooper())
    @Volatile var on = true

    init {
        for (sw in listOf("blue", "brown", "red")) {
            for (r in 0..4) load(ctx, "${sw}_p_generic_r$r")
            load(ctx, "${sw}_r_generic")
            if (sw != "blue") for (k in listOf("space", "enter", "backspace")) { load(ctx, "${sw}_p_$k"); load(ctx, "${sw}_r_$k") }
        }
    }

    private fun load(ctx: Context, name: String) {
        val res = ctx.resources.getIdentifier(name, "raw", ctx.packageName)
        if (res != 0) ids[name] = pool.load(ctx, res, 1)
    }

    /** A keypress: the press now, the release a moment later. [key] is space · enter · backspace or null. */
    fun click(sw: String, key: String? = null) {
        if (!on) return
        val special = key != null && ids.containsKey("${sw}_p_$key")
        val press = if (special) "${sw}_p_$key" else "${sw}_p_generic_r${Random.nextInt(5)}"
        val release = if (special) "${sw}_r_$key" else "${sw}_r_generic"
        ids[press]?.let { pool.play(it, .7f, .7f, 1, 0, .97f + Random.nextFloat() * .06f) }
        main.postDelayed({ ids[release]?.let { pool.play(it, .45f, .45f, 0, 0, 1f) } }, 55L + Random.nextLong(35))
    }
}
