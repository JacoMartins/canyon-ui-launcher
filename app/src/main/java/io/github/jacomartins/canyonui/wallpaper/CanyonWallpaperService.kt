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

/**
 * Canyon UI's live wallpapers ([Scene]). Animated scenes draw at [FPS] while visible, as an AGSL
 * shader on Android 13+ (a small Kotlin-drawn picture stretched to the screen before that); Day
 * cycle draws the photo once and again each minute. Nothing runs while the wallpaper is hidden
 * (another app in front, screen off).
 */
class CanyonWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SceneEngine()

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

        override fun onDestroy() {
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
            if (visible) start() else stop()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            stop()
            super.onSurfaceDestroyed(holder)
        }

        private fun start() {
            stop()
            draw()
            if (scene.animated) {
                Choreographer.getInstance().postFrameCallback(frame)
            } else {
                main.postDelayed(minuteTick, nextRedrawMs())
            }
        }

        private fun stop() {
            Choreographer.getInstance().removeFrameCallback(frame)
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
            if (Build.VERSION.SDK_INT >= 33 && !shaderBroken) {
                val s = (shader as? RuntimeShader) ?: try {
                    RuntimeShader(if (scene == Scene.DAY_LINEAR) DayGradient.LINEAR_SHADER else DayGradient.SHADER).also { shader = it }
                } catch (e: IllegalArgumentException) {
                    android.util.Log.w("CanyonWallpaper", "Day gradient shader unavailable", e)
                    shaderBroken = true
                    null
                }
                if (s != null) {
                    val sky = DayGradient.at(minute, eased = scene != Scene.DAY_LINEAR)
                    fun rgb(name: String, c: Int) = s.setFloatUniform(name, ((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
                    s.setFloatUniform("size", width.toFloat(), height.toFloat())
                    rgb("top", sky.top)
                    rgb("bottom", sky.bottom)
                    rgb("glow", sky.glow)
                    s.setFloatUniform("shape", sky.gx, sky.gy, sky.radius, sky.strength)
                    shaderPaint.shader = s
                    canvas.drawPaint(shaderPaint)
                    return
                }
            }
            drawSmall(canvas, minute)
        }

        private fun drawDayCycle(canvas: Canvas) {
            val p = photo ?: CanyonWallpaper.photo(this@CanyonWallpaperService)?.let {
                if (it.width == width && it.height == height) it else it.scale(width, height)
            }?.also { photo = it }
            if (p == null) {
                canvas.drawColor(Color.rgb(20, 24, 40))
                return
            }
            DayCycle.draw(canvas, p, Rect(0, 0, width, height), DayCycle.at(CanyonWallpaper.minuteOfDay()))
        }

        // The colours the phone's theme (and other apps) see: from a small picture of the scene.
        override fun onComputeColors(): WallpaperColors? {
            colors?.let { return it }
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
    }
}
