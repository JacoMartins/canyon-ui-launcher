package io.github.jacomartins.canyonui.wallpaper

import kotlin.math.exp

/**
 * The Day gradient wallpaper: a sky that goes from top to bottom colour with one large soft glow,
 * like a sun (or moon) travelling round the screen through the day: low on the right at night,
 * rising on the left at dawn, across the top by day, sinking on the right in the evening. Each
 * key moment is a [Sky]; between them everything blends, position included. Pure (tested).
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

    private fun mixColor(a: Int, b: Int, k: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) + ((((b shr shift) and 0xFF) - ((a shr shift) and 0xFF)) * k) + 0.5f).toInt().coerceIn(0, 255)
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** One pixel of [sky] at ([u], [v]) on a screen [aspect] (height / width) tall, as r, g, b in 0..1. */
    fun color(sky: Sky, u: Float, v: Float, aspect: Float): FloatArray {
        fun c(x: Int, shift: Int) = ((x shr shift) and 0xFF) / 255f
        var r = c(sky.top, 16) + (c(sky.bottom, 16) - c(sky.top, 16)) * v
        var g = c(sky.top, 8) + (c(sky.bottom, 8) - c(sky.top, 8)) * v
        var b = c(sky.top, 0) + (c(sky.bottom, 0) - c(sky.top, 0)) * v
        val dx = u - sky.gx
        val dy = (v - sky.gy) * aspect
        val w = sky.strength * exp(-(dx * dx + dy * dy) / (sky.radius * sky.radius))
        r += (c(sky.glow, 16) - r) * w
        g += (c(sky.glow, 8) - g) * w
        b += (c(sky.glow, 0) - b) * w
        return floatArrayOf(r, g, b)
    }

    /**
     * The linear version: the glow's colour comes in straight bands from the side the glow is on
     * (seen from the screen's middle), instead of a round light.
     */
    fun linearColor(sky: Sky, u: Float, v: Float, aspect: Float): FloatArray {
        fun c(x: Int, shift: Int) = ((x shr shift) and 0xFF) / 255f
        var r = c(sky.top, 16) + (c(sky.bottom, 16) - c(sky.top, 16)) * v
        var g = c(sky.top, 8) + (c(sky.bottom, 8) - c(sky.top, 8)) * v
        var b = c(sky.top, 0) + (c(sky.bottom, 0) - c(sky.top, 0)) * v
        val w = sky.strength * linearBand(sky, u, v, aspect)
        r += (c(sky.glow, 16) - r) * w
        g += (c(sky.glow, 8) - g) * w
        b += (c(sky.glow, 0) - b) * w
        return floatArrayOf(r, g, b)
    }

    // 0 across the far side of the screen, rising to 1 at the glow's edge, along the line from
    // the middle towards the glow.
    private fun linearBand(sky: Sky, u: Float, v: Float, aspect: Float): Float {
        var dx = sky.gx - 0.5f
        var dy = (sky.gy - 0.5f) * aspect
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
        dx /= len; dy /= len
        val reach = 0.5f * kotlin.math.abs(dx) + 0.5f * aspect * kotlin.math.abs(dy)
        val s = ((u - 0.5f) * dx + (v - 0.5f) * aspect * dy) / reach
        val k = ((s - (0.6f - sky.radius)) / (1.1f - (0.6f - sky.radius))).coerceIn(0f, 1f)
        return k * k * (3f - 2f * k)
    }

    /** [linearColor] as AGSL, with the same uniforms as [SHADER]. */
    const val LINEAR_SHADER = """
        uniform float2 size;
        uniform float3 top;
        uniform float3 bottom;
        uniform float3 glow;
        uniform float4 shape; // gx, gy, radius, strength
        float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
        half4 main(float2 xy) {
            float u = xy.x / size.x;
            float v = xy.y / size.y;
            float aspect = size.y / size.x;
            float3 c = mix(top, bottom, v);
            float2 d = float2(shape.x - 0.5, (shape.y - 0.5) * aspect);
            d = d / max(length(d), 0.001);
            float reach = 0.5 * abs(d.x) + 0.5 * aspect * abs(d.y);
            float s = ((u - 0.5) * d.x + (v - 0.5) * aspect * d.y) / reach;
            float from = 0.6 - shape.z;
            float w = shape.w * smoothstep(from, 1.1, s);
            c = mix(c, glow, w);
            c += (hash(xy) - 0.5) / 255.0;
            return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
        }
    """

    /** The same picture as AGSL; the sky arrives as uniforms ([Sky] at the moment drawn). */
    const val SHADER = """
        uniform float2 size;
        uniform float3 top;
        uniform float3 bottom;
        uniform float3 glow;
        uniform float4 shape; // gx, gy, radius, strength
        float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
        half4 main(float2 xy) {
            float u = xy.x / size.x;
            float v = xy.y / size.y;
            float aspect = size.y / size.x;
            float3 c = mix(top, bottom, v);
            float2 d = float2(u - shape.x, (v - shape.y) * aspect);
            float w = shape.w * exp(-dot(d, d) / (shape.z * shape.z));
            c = mix(c, glow, w);
            c += (hash(xy) - 0.5) / 255.0;
            return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
        }
    """
}
