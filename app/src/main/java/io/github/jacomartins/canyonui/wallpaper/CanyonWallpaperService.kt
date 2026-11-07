package io.github.jacomartins.canyonui.wallpaper

import android.app.WallpaperColors
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withScale

/**
 * Canyon UI's live wallpapers ([Scene]). Animated scenes draw at [FPS] while visible, as an AGSL
 * shader on Android 13+ (a small Kotlin-drawn picture stretched to the screen before that); Day
 * cycle draws the photo once and again each minute. Nothing runs while the wallpaper is hidden
 * (another app in front, screen off). The day scenes open with a short animation each time
 * they appear: the light comes up and the glow (or the photo) settles into place.
 */
class CanyonWallpaperService : WallpaperService() {

    private val engines = HashSet<SceneEngine>()

    override fun onCreateEngine(): Engine = SceneEngine().also { engines += it }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Dark mode on or off: the day scenes dim or brighten, and say so to the system.
        engines.forEach { it.restyle() }
    }

    private inner class SceneEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val main = Handler(Looper.getMainLooper())
        private var scene = CanyonWallpaper.scene(this@CanyonWallpaperService)
        private var visible = false
        private var width = 0
        private var height = 0
        private var lastFrameNs = 0L

        private var shader: Any? = null // RuntimeShader on Android 13+
        private var shaderBroken = false
        private val shaderPaint = Paint()
        private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private var small: Bitmap? = null
        private var smallPixels = IntArray(0)
        private var photo: Bitmap? = null
        private var colors: WallpaperColors? = null

        private val frame = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!visible || !scene.animated) return
                // At most FPS: slow, smooth motion needs no more, and it is half the work.
                if (frameTimeNanos - lastFrameNs >= FRAME_NS - FRAME_SLACK_NS) {
                    lastFrameNs = frameTimeNanos
                    draw()
                }
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        // The day scenes' opening: each time the wallpaper appears (screen on, unlock, back home
        // from an app, a new scene), the light comes up into place.
        private var introStartMs = -1L
        private var introPending = true
        private val intro = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!visible || introStartMs < 0) return
                draw()
                if (introStartMs >= 0) Choreographer.getInstance().postFrameCallback(this)
            }
        }
        /** How far the opening has come, eased: 0 at its start, 1 when it is over (or none). */
        private fun introProgress(): Float {
            if (introStartMs < 0) return 1f
            val k = (SystemClock.uptimeMillis() - introStartMs) / INTRO_MS.toFloat()
            if (k >= 1f) {
                introStartMs = -1L
                return 1f
            }
            val r = 1f - k
            return 1f - r * r * r
        }

        /** The opening's dimming over the whole picture, fading out as it ends. */
        private fun introShade(canvas: Canvas, e: Float) {
            if (e < 1f) canvas.drawColor(Color.argb(((1f - e) * INTRO_SHADE * 255f).toInt(), 0, 0, 0))
        }

        // Day cycle: the light changes by the minute.
        private val minuteTick = object : Runnable {
            override fun run() {
                if (!visible || scene.animated) return
                draw()
                main.postDelayed(this, nextRedrawMs())
            }
        }

        // The linear sky drifts on every few seconds; the others change by the minute.
        private fun nextRedrawMs() = if (scene == Scene.DAY_LINEAR) LINEAR_REDRAW_MS else msToNextMinute()

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            CanyonWallpaper.prefs(this@CanyonWallpaperService).registerOnSharedPreferenceChangeListener(this)
        }

        fun restyle() {
            DayGradient.night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            if (scene.animated) return
            colors = null
            notifyColorsChanged()
            if (visible) draw()
        }

        /**
         * Which stretch of the day the colours told to the system (the lock screen's clock, the
         * phone's theme) belong to. They are told again when it changes, at most every
         * [COLOR_STEP_MIN] minutes, so the clock matches the sky without the theme being re-made
         * all the time.
         */
        private var colorsKey = -1

        private fun colorsKeyNow(): Int =
            if (scene.animated) 0 else CanyonWallpaper.minuteOfDay() / COLOR_STEP_MIN * 2 + if (DayGradient.night) 1 else 0

        private fun refreshColors() {
            if (colorsKeyNow() == colorsKey) return
            colors = null
            notifyColorsChanged()
        }

        override fun onDestroy() {
            engines -= this
            CanyonWallpaper.prefs(this@CanyonWallpaperService).unregisterOnSharedPreferenceChangeListener(this)
            stop()
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
            // Another scene, or a new Day cycle photo.
            scene = CanyonWallpaper.scene(this@CanyonWallpaperService)
            shader = null
            photo = null
            colors = null
            notifyColorsChanged()
            // A new scene opens like the screen turning on.
            introPending = true
            if (visible) start()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w
            height = h
            small = null
            photo = null
            if (visible) draw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                introPending = true
                start()
            } else {
                stop()
                // Hidden (screen off, an app in front): the colours catch up with the sky, so the
                // lock screen's clock picks them up before it is seen again.
                refreshColors()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            stop()
            super.onSurfaceDestroyed(holder)
        }

        private fun start() {
            stop()
            if (introPending && !scene.animated) {
                introStartMs = SystemClock.uptimeMillis()
                introPending = false
                Choreographer.getInstance().postFrameCallback(intro)
            }
            draw()
            if (scene.animated) {
                Choreographer.getInstance().postFrameCallback(frame)
            } else {
                main.postDelayed(minuteTick, nextRedrawMs())
            }
        }

        private fun stop() {
            Choreographer.getInstance().removeFrameCallback(frame)
            Choreographer.getInstance().removeFrameCallback(intro)
            introStartMs = -1L
            main.removeCallbacks(minuteTick)
        }

        private fun draw() {
            if (width <= 0 || height <= 0) return
            val holder = surfaceHolder
            val canvas: Canvas = try {
                holder.lockHardwareCanvas()
            } catch (e: Exception) {
                null
            } ?: return
            try {
                when (scene) {
                    Scene.DAY_CYCLE -> drawDayCycle(canvas)
                    Scene.DAY_GRADIENT, Scene.DAY_LINEAR -> drawDayGradient(canvas)
                    else -> drawScene(canvas)
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun drawScene(canvas: Canvas) {
            val t = SystemClock.elapsedRealtime() / 1000f
            if (Build.VERSION.SDK_INT >= 33 && !shaderBroken) {
                val s = (shader as? RuntimeShader) ?: try {
                    SceneShaders.source(scene)?.let { RuntimeShader(it) }?.also { shader = it }
                } catch (e: IllegalArgumentException) {
                    // The GPU's shader compiler refused it: the same maths drawn below instead.
                    android.util.Log.w("CanyonWallpaper", "Scene shader unavailable", e)
                    shaderBroken = true
                    null
                }
                if (s != null) {
                    s.setFloatUniform("size", width.toFloat(), height.toFloat())
                    s.setFloatUniform("t", t)
                    shaderPaint.shader = s
                    canvas.drawPaint(shaderPaint)
                    return
                }
            }
            drawSmall(canvas, t)
        }

        /** No runtime shaders: the same maths, drawn small and stretched (it is smooth). */
        private fun drawSmall(canvas: Canvas, t: Float) {
            val w = SMALL_W
            val h = (SMALL_W * height / width).coerceAtLeast(1)
            val bmp = small?.takeIf { it.width == w && it.height == h } ?: createBitmap(w, h).also {
                small = it
                smallPixels = IntArray(w * h)
            }
            SceneMath.render(scene, t, w, h, height / width.toFloat(), smallPixels)
            bmp.setPixels(smallPixels, 0, w, 0, 0, w, h)
            canvas.drawBitmap(bmp, null, Rect(0, 0, width, height), bitmapPaint)
        }

        private fun drawDayGradient(canvas: Canvas) {
            val minute = CanyonWallpaper.minuteNow()
            val e = introProgress()
            if (Build.VERSION.SDK_INT >= 33 && !shaderBroken) {
                val s = (shader as? RuntimeShader) ?: try {
                    RuntimeShader(if (scene == Scene.DAY_LINEAR) DayGradient.LINEAR_SHADER else DayGradient.SHADER).also { shader = it }
                } catch (e: IllegalArgumentException) {
                    android.util.Log.w("CanyonWallpaper", "Day gradient shader unavailable", e)
                    shaderBroken = true
                    null
                }
                if (s != null) {
                    // Opening: the light is cast strong and sharp, then softens and spreads into
                    // place, like a light just switched on.
                    val sky = DayGradient.at(minute, eased = scene != Scene.DAY_LINEAR).let {
                        if (e < 1f) {
                            it.copy(
                                strength = (it.strength * (1f + INTRO_BOOST * (1f - e))).coerceAtMost(1f),
                                radius = it.radius * (INTRO_RADIUS + (1f - INTRO_RADIUS) * e),
                            )
                        } else {
                            it
                        }
                    }
                    s.setFloatUniform("size", width.toFloat(), height.toFloat())
                    if (scene == Scene.DAY_LINEAR) {
                        val (top, bottom) = DayGradient.linearPair(sky)
                        s.setFloatUniform("top", top)
                        s.setFloatUniform("bottom", bottom)
                        s.setFloatUniform("edge", INTRO_EDGE + (1f - INTRO_EDGE) * e)
                    } else {
                        val p = DayGradient.palette(sky)
                        s.setFloatUniform("top", p.top)
                        s.setFloatUniform("bottom", p.bottom)
                        s.setFloatUniform("glow", p.glow)
                        s.setFloatUniform("shape", sky.gx, sky.gy, sky.radius, sky.strength)
                        s.setFloatUniform("hardness", DayGradient.HARDNESS + INTRO_HARD * (1f - e))
                    }
                    shaderPaint.shader = s
                    canvas.drawPaint(shaderPaint)
                    introShade(canvas, e)
                    return
                }
            }
            drawSmall(canvas, minute)
            introShade(canvas, e)
        }

        private fun drawDayCycle(canvas: Canvas) {
            val p = photo ?: CanyonWallpaper.photo(this@CanyonWallpaperService)?.let {
                if (it.width == width && it.height == height) it else it.scale(width, height)
            }?.also { photo = it }
            if (p == null) {
                canvas.drawColor(Color.rgb(20, 24, 40))
                return
            }
            // Opening: the photo settles from a touch closer, and the light comes up.
            val e = introProgress()
            val zoom = 1f + INTRO_ZOOM * (1f - e)
            canvas.withScale(zoom, zoom, width / 2f, height / 2f) {
                DayCycle.draw(this, p, Rect(0, 0, width, height), DayCycle.at(CanyonWallpaper.minuteOfDay()))
            }
            introShade(canvas, e)
        }

        // The colours the phone's theme (and other apps) see: from a small picture of the scene.
        override fun onComputeColors(): WallpaperColors? {
            colors?.let { return it }
            colorsKey = colorsKeyNow()
            val w = 48
            val h = 96
            val bmp = createBitmap(w, h)
            if (scene == Scene.DAY_CYCLE) {
                val p = CanyonWallpaper.photo(this@CanyonWallpaperService) ?: return null
                DayCycle.draw(Canvas(bmp), p, Rect(0, 0, w, h), DayCycle.at(CanyonWallpaper.minuteOfDay()))
            } else {
                val px = IntArray(w * h)
                val t = if (scene.timeOfDay) CanyonWallpaper.minuteNow() else SystemClock.elapsedRealtime() / 1000f
                SceneMath.render(scene, t, w, h, 2f, px)
                bmp.setPixels(px, 0, w, 0, 0, w, h)
            }
            return WallpaperColors.fromBitmap(bmp).also { colors = it }
        }
    }

    private fun msToNextMinute(): Long = 60_000L - System.currentTimeMillis() % 60_000L + 50L

    private companion object {
        const val FPS = 30
        const val FRAME_NS = 1_000_000_000L / FPS
        const val FRAME_SLACK_NS = 4_000_000L
        const val SMALL_W = 120
        const val LINEAR_REDRAW_MS = 5_000L
        const val COLOR_STEP_MIN = 60
        const val INTRO_MS = 1_800L
        const val INTRO_SHADE = 0.2f
        const val INTRO_ZOOM = 0.12f

        // The day gradients' opening: at its start the glow is this much stronger, this much
        // smaller (its size, 1 = at rest), and this much sharper-edged; the linear blend this narrow.
        const val INTRO_BOOST = 0.5f
        const val INTRO_RADIUS = 0.75f
        const val INTRO_HARD = 3f
        const val INTRO_EDGE = 0.2f
    }
}
