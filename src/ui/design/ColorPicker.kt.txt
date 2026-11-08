package io.github.jacomartins.canyonui.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import io.github.jacomartins.canyonui.R
import kotlin.math.roundToInt

/**
 * A colour picker with transparency: hue, saturation, brightness and opacity sliders over a
 * preview, and a colour code to type (#RRGGBB keeps the opacity, #AARRGGBB sets it too, see
 * [ColorCode]). [onCommit] runs when a slider is let go or a code is complete (not on every frame
 * of a drag) with the colour as ARGB; [onChange], when given, on every change. [labels] name the
 * four sliders for accessibility. With [initial] 0 it starts on [startColor]'s colour.
 */
@Composable
fun ColorPicker(
    initial: Int,
    labels: List<String>,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onChange: ((Int) -> Unit)? = null,
) {
    // Held here (not keyed on [initial]): a commit coming back as the new [initial] must not
    // replace the sliders' state while they are in use, or lose the hue of a grey or black.
    val hsv = remember { FloatArray(3) }
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(0f) }
    var alpha by remember { mutableFloatStateOf(0f) }
    var sent by remember { mutableIntStateOf(0) }
    var seeded by remember { mutableStateOf(false) }

    fun argb(): Int = android.graphics.Color.HSVToColor((alpha * 255f).roundToInt(), floatArrayOf(hue * 360f, sat, value))
    fun seed(c: Int) {
        android.graphics.Color.colorToHSV(c, hsv)
        // Greys have no hue of their own: keep the slider where it was.
        if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0] / 360f
        if (hsv[2] > 0f) sat = hsv[1]
        value = hsv[2]
        alpha = (c ushr 24) / 255f
    }
    if (!seeded) {
        seed(startColor(initial))
        sent = initial
        seeded = true
    }
    // A colour changed from elsewhere (another folder, "default") is shown; our own is not re-read.
    LaunchedEffect(initial) {
        if (initial != sent) {
            seed(startColor(initial))
            sent = initial
        }
    }
    val latestCommit by rememberUpdatedState(onCommit)
    val latestChange by rememberUpdatedState(onChange)
    fun changed() = latestChange?.invoke(argb())
    fun commit() {
        val c = argb()
        sent = c
        latestCommit(c)
    }

    val full = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue * 360f, 1f, 1f)))
    val opaque = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue * 360f, sat, value)))

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.smMd)) {
            Box(Modifier.size(36.dp).clip(CircleShape)) {
                Checker(Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color(argb())))
            }
            CodeField(
                color = argb(),
                onCode = { c, done ->
                    seed(c)
                    changed()
                    if (done) commit()
                },
                modifier = Modifier.weight(1f),
            )
        }
        GradientSlider(hue, labels[0], Brush.horizontalGradient(HUES), { hue = it; changed() }, ::commit)
        GradientSlider(sat, labels[1], Brush.horizontalGradient(listOf(Color.White, full)), { sat = it; changed() }, ::commit)
        GradientSlider(value, labels[2], Brush.horizontalGradient(listOf(Color.Black, full)), { value = it; changed() }, ::commit)
        GradientSlider(alpha, labels[3], Brush.horizontalGradient(listOf(opaque.copy(alpha = 0f), opaque)), { alpha = it; changed() }, ::commit, checker = true)
    }
}

/**
 * The colour as a code to read or type. While typing, each complete code ([ColorCode.parse])
 * shows at once and is kept; Done (or leaving the field) puts the code back in full.
 */
@Composable
private fun CodeField(color: Int, onCode: (Int, done: Boolean) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.color_code)
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(ColorCode.format(color)) }
    val focus = LocalFocusManager.current
    // Not typing: the field follows the sliders.
    if (!focused && text != ColorCode.format(color)) text = ColorCode.format(color)
    fun current() = ColorCode.parse(text, color)
    Surface(shape = CircleShape, color = colors.surfaceContainerHighest, modifier = modifier.height(40.dp).oneDepthBorder()) {
        BasicTextField(
            value = text,
            onValueChange = { t ->
                text = t.uppercase().filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }.take(9)
                current()?.let { onCode(it, ColorCode.complete(text)) }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                current()?.let { onCode(it, true) }
                focus.clearFocus()
            }),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.md)
                .onFocusChanged {
                    if (focused && !it.isFocused) current()?.let { c -> onCode(c, true) }
                    focused = it.isFocused
                }
                .semantics { contentDescription = label },
            decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { inner() } },
        )
    }
}

/** A pill track filled with [brush] and a round thumb; drag or tap to set [value] in 0..1. */
@Composable
private fun GradientSlider(
    value: Float,
    label: String,
    brush: Brush,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
    checker: Boolean = false,
) {
    val thumb = 24.dp
    // The gesture handlers outlive a composition: they call the latest callbacks.
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { v -> onChange(v.coerceIn(0f, 1f)); onDone(); true }
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val thumbPx = with(LocalDensity.current) { thumb.toPx() }
        fun at(x: Float) = ((x - thumbPx / 2f) / (widthPx - thumbPx)).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
                .clip(CircleShape)
                .oneDepthBorder()
                .pointerInput(widthPx) {
                    detectTapGestures { change(at(it.x)); done() }
                }
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { change(at(it.x)) },
                        onDragEnd = { done() },
                        onDragCancel = { done() },
                    ) { c, _ -> change(at(c.position.x)) }
                },
        ) {
            if (checker) Checker(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(brush))
        }
        val x = with(LocalDensity.current) { ((widthPx - thumbPx) * value).toDp() }
        Box(
            Modifier
                .offset(x = x)
                .size(thumb)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .background(Color.White)
                .oneDepthBorder(),
        )
    }
}

/** Grey checkerboard behind transparent colours. */
@Composable
private fun Checker(modifier: Modifier) {
    Canvas(modifier) {
        val s = 6.dp.toPx()
        drawRect(Color(0xFFE0E0E0))
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else s
            while (x < size.width) {
                drawRect(Color(0xFFBDBDBD), Offset(x, y), Size(s, s))
                x += 2 * s
            }
            y += s
            row++
        }
    }
}

private val HUES = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF),
    Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
)
private val DEFAULT = Color(0xFF5B8DEF).toArgb()

/** The colour [ColorPicker] shows for [initial]: [initial] itself, or its starting colour for 0. */
fun startColor(initial: Int): Int = if (initial != 0) initial else {
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(DEFAULT, it) }
    android.graphics.Color.HSVToColor((DEFAULT_ALPHA * 255f).roundToInt(), hsv)
}
private const val DEFAULT_ALPHA = 0.65f
