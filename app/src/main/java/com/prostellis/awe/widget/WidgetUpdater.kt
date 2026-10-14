package com.prostellis.awe.widget

import android.app.PendingIntent
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.prostellis.awe.MainActivity
import com.prostellis.awe.R
import com.prostellis.awe.data.Store
import com.prostellis.awe.data.WidgetSettings
import com.prostellis.awe.weather.WeatherLogic

/** Draws widgets and hands them to the home screen. */
object WidgetUpdater {

    private val PROVIDERS = mapOf(
        TinyWidget::class.java to WidgetKind.TINY,
        SmallWidget::class.java to WidgetKind.DAILY,
        HourlyWidget::class.java to WidgetKind.HOURLY,
        ForecastWidget::class.java to WidgetKind.FORECAST,
    )

    /** Every widget of ours currently on the home screen. */
    fun allIds(context: Context): IntArray {
        val m = AppWidgetManager.getInstance(context)
        return PROVIDERS.keys.map { m.getAppWidgetIds(ComponentName(context, it)) }.reduce { a, b -> a + b }
    }

    fun kindOf(context: Context, id: Int): WidgetKind? {
        val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className ?: return null
        return PROVIDERS.entries.firstOrNull { it.key.name == provider }?.value
    }

    /** The widget's size on the home screen in dp (portrait: narrowest width, tallest height). */
    fun sizeDp(context: Context, id: Int, kind: WidgetKind): Pair<Float, Float> {
        val o = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val (dw, dh) = defaultSize(kind)
        return (if (w > 0) w.toFloat() else dw) to (if (h > 0) h.toFloat() else dh)
    }

    fun defaultSize(kind: WidgetKind) = when (kind) {
        WidgetKind.TINY -> 74f to 74f
        WidgetKind.DAILY, WidgetKind.HOURLY -> 152f to 74f
        WidgetKind.FORECAST -> 310f to 184f
    }

    fun phoneIsDark(context: Context) =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** True when the home-screen wallpaper is light enough for dark text. */
    fun wallpaperWantsDarkText(context: Context): Boolean {
        val colors = runCatching {
            WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        }.getOrNull() ?: return false
        return colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
    }

    /** Draws one widget from the saved forecast, with settings that may not be saved yet (for the settings preview). */
    fun renderFor(context: Context, kind: WidgetKind, widthDp: Float, heightDp: Float, settings: WidgetSettings, iconStyle: IconStyle): Rendered {
        val store = Store(context)
        val saved = store.forecastForCurrentPlace()
        val now = System.currentTimeMillis()
        val model = saved?.let { WeatherLogic.model(store.place, it.forecast, it.fetchedAt, now, 7) }
        val dark = phoneIsDark(context)
        val wallpaperLight = wallpaperWantsDarkText(context)
        // The sky follows the app's background style; the 1×1 keeps its weather tint
        val sky = if (saved == null || model == null || kind == WidgetKind.TINY) null
            else WidgetSky.picture(context, store.bgStyle, model.nowCat, model.light, dark, WeatherLogic.localYmd(now, saved.forecast.utcOffsetSeconds))
        val look = Look(
            transparency = settings.transparency,
            theme = WidgetTheme.pick(dark, settings.transparency, wallpaperLight, sky?.isDark),
            phoneDark = dark,
            iconStyle = iconStyle,
            tempColor = settings.tempColor,
            glow = settings.glow,
            sky = sky,
            showCurrent = settings.showCurrent || kind != WidgetKind.FORECAST,
            wallpaperLight = wallpaperLight,
        )
        return WidgetRenderer(context).render(kind, model, widthDp, heightDp, look, store.eventColors)
    }

    /** Redraws every widget (after a change that applies to all of them, like the icon style). */
    fun updateAll(context: Context) {
        for (id in allIds(context)) update(context, id)
    }

    /** Redraws a widget. With [spinning], the refresh button shows the spinner instead. */
    fun update(context: Context, id: Int, spinning: Boolean = false) {
        val kind = kindOf(context, id) ?: return
        val (w, h) = sizeDp(context, id, kind)
        val store = Store(context)
        val r = renderFor(context, kind, w, h, store.widgetSettings(id), store.iconStyle)

        AppWidgetManager.getInstance(context).updateAppWidget(id, buildViews(context, id, r, spinning))
    }

    /**
     * The home-screen views for a drawn widget: picture, buttons, and what each tap does. A tap opens the app.
     * Every widget has a refresh button; only the forecast widget has the settings button (the others' settings open by long-press).
     */
    fun buildViews(context: Context, id: Int, r: Rendered, spinning: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_root)
        views.setImageViewBitmap(R.id.widget_image, r.bitmap)
        views.setOnClickPendingIntent(android.R.id.background, openAppIntent(context))
        place(views, R.id.btn_refresh, r.refresh, context)
        place(views, R.id.spinner, r.refresh, context)
        views.setInt(R.id.btn_refresh, "setColorFilter", r.buttonColor)
        views.setViewVisibility(R.id.btn_refresh, if (spinning) View.INVISIBLE else View.VISIBLE)
        views.setViewVisibility(R.id.spinner, if (spinning) View.VISIBLE else View.GONE)
        views.setOnClickPendingIntent(R.id.btn_refresh, RefreshReceiver.pendingIntent(context, id))
        if (r.gear == null) {
            views.setViewVisibility(R.id.btn_gear, View.GONE)
        } else {
            place(views, R.id.btn_gear, r.gear, context)
            views.setInt(R.id.btn_gear, "setColorFilter", r.buttonColor)
            views.setViewVisibility(R.id.btn_gear, View.VISIBLE)
            views.setOnClickPendingIntent(R.id.btn_gear, settingsIntent(context, id))
        }
        return views
    }

    /** Shows only the spinner, without redrawing. */
    fun showSpinner(context: Context, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_root)
        views.setViewVisibility(R.id.btn_refresh, View.INVISIBLE)
        views.setViewVisibility(R.id.spinner, View.VISIBLE)
        AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(id, views)
    }

    /** Puts a 32dp tap target centered on the spot the renderer left for the button. */
    private fun place(views: RemoteViews, viewId: Int, spot: ButtonSpot, context: Context) {
        val dip = TypedValue.COMPLEX_UNIT_DIP
        views.setViewLayoutMargin(viewId, RemoteViews.MARGIN_LEFT, spot.centerX - TAP / 2, dip)
        views.setViewLayoutMargin(viewId, RemoteViews.MARGIN_TOP, spot.centerY - TAP / 2, dip)
        val pad = ((TAP - spot.iconSize) / 2 * context.resources.displayMetrics.density).toInt()
        views.setViewPadding(viewId, pad, pad, pad, pad)
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun settingsIntent(context: Context, id: Int): PendingIntent = PendingIntent.getActivity(
        context, id,
        Intent(context, WidgetSettingsActivity::class.java)
            .setData(Uri.parse("awe://widget/$id"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private const val TAP = 32f
}
