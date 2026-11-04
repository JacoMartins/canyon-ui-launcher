package io.github.jacomartins.canyonui.wallpaper

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * The Day gradient wallpaper: a sky that goes from top to bottom colour with one large soft glow,
 * like a sun (or moon) travelling round the screen through the day: low on the right at night,
 * rising on the left at dawn, across the top by day, sinking on the right in the evening. Each
 * key moment is a [Sky]; between them everything blends, position included, through the colours
 * between (OKLCH) rather than grey. Pure (tested).
 */
object DayGradient {

    /**
     * Colours as 0xRRGGBB. The glow is at ([gx], [gy]) as shares of the screen's width and height
     * (it may sit just outside), [radius] in widths, [strength] 0..1.
     */
    data class Sky(
        val top: Int,
        val bottom: Int,
        val glow: Int,
        val strength: Float,
        val gx: Float,
        val gy: Float,
        val radius: Float,
    )

    // Minute of the day -> sky, in order; the day wraps from the last back to the first.
    private val KEYS = listOf(
        0 to Sky(0x0B0B38, 0x0E1A3A, 0x1E6E6A, 0.85f, 1.25f, 0.95f, 1.15f), // 00:00 navy, teal glow low right
        140 to Sky(0x1A1648, 0x1E1844, 0x3A1A28, 0.60f, 1.00f, 1.00f, 0.81f), // 02:20 indigo, ember
        180 to Sky(0x1C1850, 0x201A48, 0x8A4A2A, 0.70f, 1.05f, 1.00f, 0.74f), // 03:00 orange ember
        260 to Sky(0x1E1A4E, 0x24183E, 0x3A1828, 0.40f, 1.00f, 1.00f, 0.68f), // 04:20 last of the night
        280 to Sky(0x4E7A9A, 0x7E7A86, 0xC8735E, 0.80f, -0.05f, 1.05f, 0.94f), // 04:40 first light, coral low left
        354 to Sky(0x1F86A8, 0x66B7A6, 0xE0A050, 0.90f, -0.10f, 0.55f, 0.74f), // 05:54 teal sky, orange sun left
        507 to Sky(0x3C9EE6, 0x4AA6E2, 0x9ED23C, 0.90f, 0.20f, 0.18f, 0.68f), // 08:27 blue, lime top left
        678 to Sky(0x5A9CF0, 0x5EA4E6, 0x7EE08A, 0.80f, 0.45f, 0.15f, 0.74f), // 11:18 blue, green top
        798 to Sky(0x3C74D8, 0x7A90B8, 0xF2924A, 0.85f, 0.80f, 0.20f, 0.68f), // 13:18 orange top right
        918 to Sky(0x4A52C8, 0x6A6AD0, 0xF0806E, 0.90f, 1.00f, 0.25f, 0.88f), // 15:18 violet blue, coral
        1038 to Sky(0x3A1A50, 0x6A2E28, 0xA0503A, 0.90f, 1.10f, 0.35f, 0.94f), // 17:18 purple over brown
        1159 to Sky(0x0E1E40, 0x2A2A6A, 0x6A2A70, 0.85f, 1.10f, 0.45f, 0.94f), // 19:19 navy, purple glow right
        1299 to Sky(0x0C1030, 0x0E2A30, 0x1E6E5A, 0.80f, 0.60f, 1.10f, 1.08f), // 21:39 navy, teal glow low
    )

    private const val DAY = 24 * 60

    /**
     * The sky at [minute] of the day (fractions allowed), blended between the nearest keys:
     * [eased] settles at each key moment; otherwise it moves at a steady pace all day.
     */
    fun at(minute: Float, eased: Boolean = true): Sky {
        val m = ((minute % DAY) + DAY) % DAY
        val i = KEYS.indexOfLast { it.first <= m }.coerceAtLeast(0)
        val (m0, a) = KEYS[i]
        val (m1raw, b) = KEYS[(i + 1) % KEYS.size]
        val m1 = if (m1raw <= m0) m1raw + DAY else m1raw
        // Eased, so the sky settles at each key moment instead of turning at a corner.
        val lin = ((m - m0) / (m1 - m0)).coerceIn(0f, 1f)
        val k = if (eased) lin * lin * (3f - 2f * lin) else lin
        return Sky(
            mixColor(a.top, b.top, k), mixColor(a.bottom, b.bottom, k), mixColor(a.glow, b.glow, k),
            lerp(a.strength, b.strength, k), lerp(a.gx, b.gx, k), lerp(a.gy, b.gy, k), lerp(a.radius, b.radius, k),
        )
    }

    private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k

    /**
     * Between two key colours through the colours between them (OKLCH: lightness, colourfulness
     * and hue each move straight on), rather than through a dull grey mix, and a little more
     * colourful half way ([TRANSITION_LIFT]).
     */
    private fun mixColor(a: Int, b: Int, k: Float): Int {
        if (k <= 0f) return a
        if (k >= 1f) return b
        val x = Oklab.of(a)
        val y = Oklab.of(b)
        val ca = hypot(x[1], x[2])
        val cb = hypot(y[1], y[2])
        var ha = atan2(x[2], x[1])
        var hb = atan2(y[2], y[1])
        // A near-grey has no hue of its own: it takes the other's.
        if (ca < GREY) ha = hb
        if (cb < GREY) hb = ha
        var dh = hb - ha
        if (dh > PI) dh -= (2 * PI).toFloat()
        if (dh < -PI) dh += (2 * PI).toFloat()
        val h = ha + dh * k
        val c = lerp(ca, cb, k) * (1f + TRANSITION_LIFT * sin(PI.toFloat() * k))
        return Oklab.toInt(Oklab.rgb(lerp(x[0], y[0], k), c * cos(h), c * sin(h)))
    }

    private const val GREY = 0.02f
    private const val TRANSITION_LIFT = 0.3f

    /** Colours more vivid all round (OKLab colourfulness), within what the screen shows. */
    private const val CHROMA = 1.12f

    /**
     * [sky]'s colours in OKLab ([Oklab]), where the picture's own gradients are mixed: they stay
     * clear between two colours instead of greying (and the shaders take them like this).
     */
    class Palette(val top: FloatArray, val bottom: FloatArray, val glow: FloatArray)

    fun palette(sky: Sky): Palette {
        fun lab(c: Int) = Oklab.of(c).also { it[1] *= CHROMA; it[2] *= CHROMA }
        return Palette(lab(sky.top), lab(sky.bottom), lab(sky.glow))
    }

    /** One pixel of [sky] at ([u], [v]) on a screen [aspect] (height / width) tall, as r, g, b in 0..1. */
    fun color(sky: Sky, u: Float, v: Float, aspect: Float): FloatArray = color(palette(sky), sky, u, v, aspect)

    /** [color] with [sky]'s [palette] worked out once (for a whole picture). */
    fun color(p: Palette, sky: Sky, u: Float, v: Float, aspect: Float): FloatArray {
        val dx = u - sky.gx
        val dy = (v - sky.gy) * aspect
        val w = sky.strength * exp(-(dx * dx + dy * dy) / (sky.radius * sky.radius))
        val c = FloatArray(3) { i ->
            val base = p.top[i] + (p.bottom[i] - p.top[i]) * v
            base + (p.glow[i] - base) * w
        }
        return Oklab.rgb(c[0], c[1], c[2])
    }

    /**
     * The linear version: two colours only, in horizontal bands from top to bottom. The glow
     * takes the end on its side (the top by day, the bottom at night) and the sky's colour at
     * the far end is the other.
     */
    fun linearColor(sky: Sky, u: Float, v: Float, aspect: Float): FloatArray = linearColor(linearPair(sky), v)

    /** [linearColor] from its [linearPair], at height [v]. */
    fun linearColor(pair: Pair<FloatArray, FloatArray>, v: Float): FloatArray {
        val (a, b) = pair
        return Oklab.rgb(a[0] + (b[0] - a[0]) * v, a[1] + (b[1] - a[1]) * v, a[2] + (b[2] - a[2]) * v)
    }

    /** The two colours of [linearColor], top and bottom, in OKLab. */
    fun linearPair(sky: Sky): Pair<FloatArray, FloatArray> {
        val p = palette(sky)
        val end = if (sky.gy < 0.5f) p.top else p.bottom
        for (i in 0..2) end[i] += (p.glow[i] - end[i]) * sky.strength
        return p.top to p.bottom
    }

    /** [linearColor] as AGSL: the two colours (OKLab) arrive as uniforms ([linearPair]). */
    const val LINEAR_SHADER = """
        uniform float2 size;
        uniform float3 top;
        uniform float3 bottom;
        float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
        float3 srgb(float3 lab) {
            float l = lab.x + 0.3963377774 * lab.y + 0.2158037573 * lab.z;
            float m = lab.x - 0.1055613458 * lab.y - 0.0638541728 * lab.z;
            float s = lab.x - 0.0894841775 * lab.y - 1.2914855480 * lab.z;
            l = l * l * l; m = m * m * m; s = s * s * s;
            float3 c = float3(
                4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
            c = clamp(c, 0.0, 1.0);
            float3 hi = 1.055 * pow(max(c, float3(1e-6)), float3(1.0 / 2.4)) - 0.055;
            return mix(c * 12.92, hi, step(0.0031308, c));
        }
        half4 main(float2 xy) {
            float3 c = srgb(mix(top, bottom, xy.y / size.y));
            c += (hash(xy) - 0.5) / 255.0;
            return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
        }
    """

    /** The same picture as AGSL; the sky arrives as uniforms ([palette] at the moment drawn). */
    const val SHADER = """
        uniform float2 size;
        uniform float3 top;
        uniform float3 bottom;
        uniform float3 glow;
        uniform float4 shape; // gx, gy, radius, strength
        float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
        float3 srgb(float3 lab) {
            float l = lab.x + 0.3963377774 * lab.y + 0.2158037573 * lab.z;
            float m = lab.x - 0.1055613458 * lab.y - 0.0638541728 * lab.z;
            float s = lab.x - 0.0894841775 * lab.y - 1.2914855480 * lab.z;
            l = l * l * l; m = m * m * m; s = s * s * s;
            float3 c = float3(
                4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
            c = clamp(c, 0.0, 1.0);
            float3 hi = 1.055 * pow(max(c, float3(1e-6)), float3(1.0 / 2.4)) - 0.055;
            return mix(c * 12.92, hi, step(0.0031308, c));
        }
        half4 main(float2 xy) {
            float u = xy.x / size.x;
            float v = xy.y / size.y;
            float aspect = size.y / size.x;
            float3 c = mix(top, bottom, v);
            float2 d = float2(u - shape.x, (v - shape.y) * aspect);
            float w = shape.w * exp(-dot(d, d) / (shape.z * shape.z));
            c = srgb(mix(c, glow, w));
            c += (hash(xy) - 0.5) / 255.0;
            return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
        }
    """
}

/** OKLab (Björn Ottosson's), a colour space where straight mixes look even and stay clear. */
internal object Oklab {
    private fun toLinear(c: Float) = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    private fun toSrgb(c: Float): Float {
        val x = c.coerceIn(0f, 1f)
        return if (x <= 0.0031308f) x * 12.92f else 1.055f * x.pow(1f / 2.4f) - 0.055f
    }

    /** 0xRRGGBB as L, a, b. */
    fun of(rgb: Int): FloatArray {
        val r = toLinear(((rgb shr 16) and 0xFF) / 255f)
        val g = toLinear(((rgb shr 8) and 0xFF) / 255f)
        val b = toLinear((rgb and 0xFF) / 255f)
        val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
        val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
        val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
        return floatArrayOf(
            0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
            1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
            0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s,
        )
    }

    /** L, a, b as r, g, b in 0..1 (colours beyond the screen's clipped to it). */
    fun rgb(lL: Float, a: Float, b: Float): FloatArray {
        var l = lL + 0.3963377774f * a + 0.2158037573f * b
        var m = lL - 0.1055613458f * a - 0.0638541728f * b
        var s = lL - 0.0894841775f * a - 1.2914855480f * b
        l = l * l * l; m = m * m * m; s = s * s * s
        return floatArrayOf(
            toSrgb(4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s),
            toSrgb(-1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s),
            toSrgb(-0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s),
        )
    }

    fun toInt(c: FloatArray): Int {
        fun ch(x: Float) = (x.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (ch(c[0]) shl 16) or (ch(c[1]) shl 8) or ch(c[2])
    }
}
