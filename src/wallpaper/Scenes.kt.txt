package io.github.jacomartins.canyonui.wallpaper

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Canyon UI's own live wallpapers. The animated ones are made of slow, smooth maths (no images),
 * written twice with the same formulas: as an AGSL shader the wallpaper draws at full size
 * ([SceneShaders]), and here in Kotlin, which draws a small copy for the launcher's frosted and
 * glass effects (and the wallpaper itself on phones without runtime shaders). Being smooth, the
 * small copy needs no blur of its own.
 *
 * Time [t] is in seconds of `SystemClock.elapsedRealtime()`, so the wallpaper and the effects,
 * in the same app, show the same moment; for the day scenes ([Scene.timeOfDay]) it is the minute
 * of the day.
 */
enum class Scene(val key: String, val animated: Boolean) {
    /** The user's photo, tinted and lit by the time of day. */
    DAY_CYCLE("day_cycle", false),

    /** A sky gradient with a travelling glow, following the time of day ([DayGradient]). */
    DAY_GRADIENT("day_gradient", false),

    /** The same sky as two colours in horizontal bands, moving at a steady pace. */
    DAY_LINEAR("day_linear", false),
    GRADIENT("gradient", true),
    AURORA("aurora", true),
    WAVES("waves", true),
    ;

    /** Follows the clock: its [SceneMath] time is the minute of the day. */
    val timeOfDay get() = this == DAY_GRADIENT || this == DAY_LINEAR

    companion object {
        fun of(key: String?): Scene? = entries.firstOrNull { it.key == key }
    }
}

object SceneMath {

    private fun mix(a: Float, b: Float, k: Float) = a + (b - a) * k
    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val k = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return k * k * (3f - 2f * k)
    }

    /** One pixel at ([u], [v]) in 0..1 (v down) of a screen [aspect] (height / width) tall, as ARGB. */
    fun color(scene: Scene, u: Float, v: Float, aspect: Float, t: Float): Int {
        val rgb = when (scene) {
            Scene.GRADIENT -> gradient(u, v, aspect, t)
            Scene.AURORA -> aurora(u, v, t)
            Scene.WAVES -> waves(u, v, t)
            Scene.DAY_GRADIENT -> DayGradient.color(DayGradient.at(t), u, v, aspect)
            Scene.DAY_LINEAR -> DayGradient.linearColor(DayGradient.at(t, eased = false), u, v, aspect)
            Scene.DAY_CYCLE -> floatArrayOf(0.5f, 0.5f, 0.5f)
        }
        fun ch(x: Float) = (x.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (0xFF shl 24) or (ch(rgb[0]) shl 16) or (ch(rgb[1]) shl 8) or ch(rgb[2])
    }

    /** Fills [out] ([w] x [h], row by row) with [scene] at [t]. */
    fun render(scene: Scene, t: Float, w: Int, h: Int, aspect: Float, out: IntArray) {
        if (scene.timeOfDay) {
            // One sky for the whole picture.
            val linear = scene == Scene.DAY_LINEAR
            val sky = DayGradient.at(t, eased = !linear)
            fun ch(x: Float) = (x.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val palette = DayGradient.palette(sky)
            val pair = if (linear) DayGradient.linearPair(sky) else null
            for (y in 0 until h) {
                val v = (y + 0.5f) / h
                // Bands: one colour per row.
                val row = pair?.let { DayGradient.linearColor(it, v) }
                for (x in 0 until w) {
                    val c = row ?: DayGradient.color(palette, sky, (x + 0.5f) / w, v, aspect)
                    out[y * w + x] = (0xFF shl 24) or (ch(c[0]) shl 16) or (ch(c[1]) shl 8) or ch(c[2])
                }
            }
            return
        }
        for (y in 0 until h) {
            val v = (y + 0.5f) / h
            for (x in 0 until w) out[y * w + x] = color(scene, (x + 0.5f) / w, v, aspect, t)
        }
    }

    // Soft colour blobs drifting on slow, different paths.
    private fun gradient(u: Float, v: Float, aspect: Float, t: Float): FloatArray {
        val y = v * aspect
        fun blob(cx: Float, cy: Float): Float {
            val dx = u - cx
            val dy = y - cy * aspect
            return exp(-(dx * dx + dy * dy) / 0.2f)
        }
        val w1 = blob(0.30f + 0.25f * sin(t * 0.07f), 0.30f + 0.20f * cos(t * 0.05f))
        val w2 = blob(0.70f + 0.20f * cos(t * 0.06f), 0.55f + 0.25f * sin(t * 0.04f))
        val w3 = blob(0.45f + 0.30f * sin(t * 0.035f + 2f), 0.85f + 0.12f * cos(t * 0.08f))
        var r = 0.10f; var g = 0.14f; var b = 0.35f
        r = mix(r, 0.45f, w1); g = mix(g, 0.25f, w1); b = mix(b, 0.65f, w1)
        r = mix(r, 0.10f, w2); g = mix(g, 0.55f, w2); b = mix(b, 0.60f, w2)
        r = mix(r, 0.90f, w3 * 0.8f); g = mix(g, 0.45f, w3 * 0.8f); b = mix(b, 0.40f, w3 * 0.8f)
        return floatArrayOf(r, g, b)
    }

    // A night sky with two curtains of light swaying slowly.
    private fun aurora(u: Float, v: Float, t: Float): FloatArray {
        var r = mix(0.01f, 0.04f, v); var g = mix(0.02f, 0.08f, v); var b = mix(0.07f, 0.16f, v)
        fun curtain(base: Float, phase: Float, power: Float): Pair<Float, Float> {
            val yc = base + 0.06f * sin(u * 2.5f + t * 0.12f + phase) + 0.03f * sin(u * 6f - t * 0.09f + phase)
            val d = v - yc
            val band = exp(-(d * d) / (if (d < 0f) 0.02f else 0.003f))
            // Fine, soft rays (two frequencies) rather than a few bright lumps.
            val streaks = 0.65f + 0.2f * sin(u * 31f + sin(u * 4f + t * 0.2f + phase) * 2f + t * 0.3f) +
                0.15f * sin(u * 67f - t * 0.4f + phase)
            return band * streaks * power to (-d * 4f).coerceIn(0f, 1f)
        }
        for ((base, phase, power) in listOf(Triple(0.32f, 0f, 0.85f), Triple(0.50f, 1.7f, 0.45f))) {
            val (k, top) = curtain(base, phase, power)
            r += k * mix(0.20f, 0.55f, top)
            g += k * mix(0.95f, 0.30f, top)
            b += k * mix(0.55f, 0.95f, top)
        }
        return floatArrayOf(r, g, b)
    }

    private val WAVE_COLORS = arrayOf(
        floatArrayOf(0.30f, 0.45f, 0.70f), floatArrayOf(0.20f, 0.33f, 0.60f),
        floatArrayOf(0.12f, 0.22f, 0.48f), floatArrayOf(0.07f, 0.13f, 0.32f),
    )

    // A warm sky over layers of slow waves.
    private fun waves(u: Float, v: Float, t: Float): FloatArray {
        val sky = smooth(0f, 0.5f, v)
        var r = mix(0.98f, 0.95f, sky); var g = mix(0.80f, 0.60f, sky); var b = mix(0.62f, 0.55f, sky)
        for (k in 0 until 4) {
            val edge = 0.45f + k * 0.13f + 0.03f * sin(u * (4f + k * 1.7f) + t * (0.10f + k * 0.04f) + k) +
                0.015f * sin(u * 11f - t * 0.07f * (k + 1))
            val s = smooth(edge - 0.006f, edge + 0.006f, v)
            val c = WAVE_COLORS[k]
            r = mix(r, c[0], s); g = mix(g, c[1], s); b = mix(b, c[2], s)
        }
        return floatArrayOf(r, g, b)
    }
}

/** The same scenes as AGSL, for drawing the wallpaper at full size on the GPU (Android 13+). */
object SceneShaders {
    private const val COMMON = """
        uniform float2 size;
        uniform float t;
        float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
        half4 done(float3 c, float2 xy) {
            // A whisper of noise against banding in the smooth gradients.
            c += (hash(xy) - 0.5) / 255.0;
            return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
        }
    """

    private const val GRADIENT = COMMON + """
        float blob(float2 p, float2 c, float aspect) {
            float2 d = float2(p.x - c.x, p.y - c.y * aspect);
            return exp(-dot(d, d) / 0.2);
        }
        half4 main(float2 xy) {
            float aspect = size.y / size.x;
            float2 p = float2(xy.x / size.x, xy.y / size.y * aspect);
            float w1 = blob(p, float2(0.30 + 0.25 * sin(t * 0.07), 0.30 + 0.20 * cos(t * 0.05)), aspect);
            float w2 = blob(p, float2(0.70 + 0.20 * cos(t * 0.06), 0.55 + 0.25 * sin(t * 0.04)), aspect);
            float w3 = blob(p, float2(0.45 + 0.30 * sin(t * 0.035 + 2.0), 0.85 + 0.12 * cos(t * 0.08)), aspect);
            float3 c = float3(0.10, 0.14, 0.35);
            c = mix(c, float3(0.45, 0.25, 0.65), w1);
            c = mix(c, float3(0.10, 0.55, 0.60), w2);
            c = mix(c, float3(0.90, 0.45, 0.40), w3 * 0.8);
            return done(c, xy);
        }
    """

    private const val AURORA = COMMON + """
        float3 curtain(float u, float v, float base, float phase, float power) {
            float yc = base + 0.06 * sin(u * 2.5 + t * 0.12 + phase) + 0.03 * sin(u * 6.0 - t * 0.09 + phase);
            float d = v - yc;
            float band = exp(-(d * d) / (d < 0.0 ? 0.02 : 0.003));
            float streaks = 0.65 + 0.2 * sin(u * 31.0 + sin(u * 4.0 + t * 0.2 + phase) * 2.0 + t * 0.3)
                + 0.15 * sin(u * 67.0 - t * 0.4 + phase);
            float k = band * streaks * power;
            float top = clamp(-d * 4.0, 0.0, 1.0);
            return k * mix(float3(0.20, 0.95, 0.55), float3(0.55, 0.30, 0.95), top);
        }
        half4 main(float2 xy) {
            float u = xy.x / size.x;
            float v = xy.y / size.y;
            float3 c = mix(float3(0.01, 0.02, 0.07), float3(0.04, 0.08, 0.16), v);
            c += curtain(u, v, 0.32, 0.0, 0.85);
            c += curtain(u, v, 0.50, 1.7, 0.45);
            return done(c, xy);
        }
    """

    private const val WAVES = COMMON + """
        half4 main(float2 xy) {
            float u = xy.x / size.x;
            float v = xy.y / size.y;
            float3 c = mix(float3(0.98, 0.80, 0.62), float3(0.95, 0.60, 0.55), smoothstep(0.0, 0.5, v));
            float3 cols[4];
            cols[0] = float3(0.30, 0.45, 0.70);
            cols[1] = float3(0.20, 0.33, 0.60);
            cols[2] = float3(0.12, 0.22, 0.48);
            cols[3] = float3(0.07, 0.13, 0.32);
            for (int k = 0; k < 4; k++) {
                float fk = float(k);
                float edge = 0.45 + fk * 0.13 + 0.03 * sin(u * (4.0 + fk * 1.7) + t * (0.10 + fk * 0.04) + fk)
                    + 0.015 * sin(u * 11.0 - t * 0.07 * (fk + 1.0));
                c = mix(c, cols[k], smoothstep(edge - 0.006, edge + 0.006, v));
            }
            return done(c, xy);
        }
    """

    fun source(scene: Scene): String? = when (scene) {
        Scene.GRADIENT -> GRADIENT
        Scene.AURORA -> AURORA
        Scene.WAVES -> WAVES
        Scene.DAY_CYCLE, Scene.DAY_GRADIENT, Scene.DAY_LINEAR -> null
    }
}
