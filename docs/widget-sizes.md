# Widget sizes on the Galaxy A03 (logcat, Stage 3.1)

Captured from `logcat -s WidgetSizes` on 2026-10-22. Cell size on the A03: **165 x 208 px**
(4 x 6 grid). `mode` is the provider's resize mode: 0 none, 1 horizontal, 2 vertical, 3 both.
"min" is what the launcher now allows (minimums rounded down to whole cells).

## Why the weather widget will not go to 1 column

| Widget | mode | min px | default | allowed min | Note |
|---|---|---|---|---|---|
| Weather (Aesthetic) `WeatherAestheticAppWidget` | 2 | 225x225 | 2x2 | 2x1 | Declares **vertical resize only**, so width stays 2 |
| Weather (Insight) `WeatherInsightAppWidget` | 2 | 225x225 | 2x2 | 2x1 | Same |
| Weather 2x1 `WeatherAppWidget2x1` | 3 | 225x225 | 2x2 | 1x1 | Both directions: 1x1 works |
| Weather `WeatherAppWidget` | 3 | 664x66 | 4x1 | 4x1 | 664 px is wider than 4 cells (660) |
| Weather forecast `WeatherForecastAppWidget` | 3 | 664x225 | 4x2 | 4x1 | Same width limit |
| Digital clock `DigitalClockWidgetProvider` | 1 | 225x75 | 2x1 | 1x1 | Works (confirmed) |
| Analog clock `AnalogClockWidgetProvider` | 3 | 139x139 | 2x2 | 1x1 | |
| Alarm `ClockAlarmWidgetProvider` | 1 | 225x75 | 2x1 | 1x1 | |
| Dual clock `DualClockDigitalWidgetProvider` | 1 | 488x75 | 3x1 | 2x1 | |

The One UI launcher shrinks the vertical-only weather widgets to 1 column anyway. It ignores
what these Samsung widgets declare (likely through Samsung-only metadata, which a normal
launcher cannot read). The public-API way to match it is a user option to resize widgets
beyond what the app declares (as Nova's "resize anything" does). Widgets then pick their
closest layout or are scaled.

## Other observations

- Several Samsung Calendar widgets report `min=0x0` (complications with mode 0): they keep
  their default size.
- `PhotoAppWidgetProvider` (picture frame) asks for 1x1 by default although its minimum is
  270x248 px; it relies on Android 12 target cells.
- TikTok widgets are all mode 0 (fixed size).
- Smaller cells than the guideline: a 2-column minimum of 225 px fits in 2 x 165 px, which is
  why rounding down was safe for most widgets.

## One UI 8.5 home screen measurements (reference screenshot, 2026-10-23)

Taken from a One UI 8.5 screenshot at 923 px wide (360 dp, so 1 dp = 2.56 px), with this
launcher's screenshot on the A03 (720 px, 1 dp = 2 px) beside it. The screenshots themselves are
not stored in the repository: they show personal calendar entries.

| What | One UI 8.5 | This launcher (Stage 4) | Change made |
|---|---|---|---|
| Icon column pitch | ~83 dp (centres at 141/355/569/782 px) | ~82 dp | none |
| Page side padding | ~13 dp | 16 dp | candidate for Stage 5 polish |
| Widget inset inside its cells | ~10.5 dp per side (21 dp between widgets) | 0 (widgets filled their cells) | 10 dp inset (`WidgetFrame.INSET_DP`) |
| Widget corners | ~26 dp; 1-row widgets are pills | whatever the widget draws (Samsung weather was square) | clipped to 26 dp / pill for every widget |
| Row pitch | ~98 dp | ~104 dp | none yet (cell height follows the screen) |

Notes:
- The provider (Samsung Calendar, Clock) chooses its layout from the size the launcher reports.
  With the inset, the reported size is now the widget's real size, which brings the choice closer
  to One UI's (for example the compact clock pill instead of clock + date).
- The Samsung Calendar widget's header overlapping the "+" button is drawn by the Calendar app.
  If it still looks different after the size change, the Calendar app on the A03 may be an older
  version than the one in the reference; updating it from the Galaxy Store is the user-side fix.
