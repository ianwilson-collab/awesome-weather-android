package com.prostellis.awe.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.util.TypedValue
import android.widget.RemoteViews
import com.prostellis.awe.MainActivity
import com.prostellis.awe.R
import com.prostellis.awe.data.Store
import com.prostellis.awe.weather.WeatherLogic

/** AWE Clock, 2×1 to 5×1: the time, with the weather and the date if wanted. */
class ClockWidget : AweWidget() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        ClockTicker.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        ClockUpdater.update(context, id)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        ClockTicker.schedule(context)
    }
}

/** Draws clock widgets and hands them to the home screen. */
object ClockUpdater {

    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ClockWidget::class.java))

    fun isClock(context: Context, id: Int) =
        AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className == ClockWidget::class.java.name

    /** Portrait size in dp, like the other widgets: narrowest width, tallest height. */
    fun sizeDp(context: Context, id: Int): Pair<Float, Float> {
        val o = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return (if (w > 0) w.toFloat() else 310f) to (if (h > 0) h.toFloat() else 74f)
    }

    /** Draws a clock with settings that may not be saved yet (for the settings preview). */
    fun render(context: Context, settings: ClockSettings, widthDp: Float, heightDp: Float, now: Long = System.currentTimeMillis()): ClockRendered {
        val store = Store(context)
        val saved = store.forecastForCurrentPlace()
        val model = saved?.let { WeatherLogic.model(store.place, it.forecast, it.fetchedAt, now, 7) }
        val dark = WidgetUpdater.phoneIsDark(context)
        val wallpaperLight = WidgetUpdater.wallpaperWantsDarkText(context)
        val sky = if (saved == null || model == null) null
            else WidgetSky.picture(context, store.bgStyle, model.nowCat, model.light, dark, WeatherLogic.localYmd(now, saved.forecast.utcOffsetSeconds))
        val look = Look(
            transparency = settings.transparency,
            theme = WidgetTheme.pick(dark, settings.transparency, wallpaperLight, sky?.isDark),
            phoneDark = dark,
            iconStyle = store.iconStyle,
            sky = sky,
            wallpaperLight = wallpaperLight,
            glow = settings.glow,
        )
        return ClockRenderer(context).render(settings, model, widthDp, heightDp, look, now, DateFormat.is24HourFormat(context))
    }

    fun update(context: Context, id: Int) {
        val (w, h) = sizeDp(context, id)
        val r = render(context, Store(context).clockSettings(id), w, h)
        AppWidgetManager.getInstance(context).updateAppWidget(id, buildViews(context, r))
    }

    fun updateAll(context: Context) {
        for (id in ids(context)) update(context, id)
    }

    /** The picture, with a tap on the time opening the Clock app and a tap on the weather opening AWE. Long-press opens settings. */
    fun buildViews(context: Context, r: ClockRendered): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_clock)
        views.setImageViewBitmap(R.id.clock_image, r.bitmap)
        val dip = TypedValue.COMPLEX_UNIT_DIP
        views.setViewLayoutWidth(R.id.tap_time, r.timeArea.width(), dip)
        views.setOnClickPendingIntent(R.id.tap_time, clockAppIntent(context))
        val weather = r.weatherArea
        if (weather == null) {
            views.setViewLayoutWidth(R.id.tap_weather, 0f, dip)
        } else {
            views.setViewLayoutWidth(R.id.tap_weather, weather.width(), dip)
            views.setOnClickPendingIntent(R.id.tap_weather, appIntent(context))
        }
        return views
    }

    private val CLOCK_PACKAGES = listOf(
        "com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock",
        "com.oneplus.deskclock", "com.coloros.alarmclock",
    )

    /** The phone's Clock app (all its tabs); failing that, whatever answers "show alarms"; failing that, AWE. */
    private fun clockAppIntent(context: Context): PendingIntent {
        val pm = context.packageManager
        val alarms = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        val target = CLOCK_PACKAGES.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
            ?: alarms.resolveActivity(pm)?.let { alarms.setComponent(it) }
            ?: Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, 1,
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun appIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * Keeps clocks on the right minute: an alarm at the start of each minute redraws them. It doesn't wake the phone,
 * so it costs nothing while the screen is off; Android delivers it the moment the phone wakes up.
 */
object ClockTicker {
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, ClockTickReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (ClockUpdater.ids(context).isEmpty()) { am.cancel(pi); return }
        val next = (System.currentTimeMillis() / 60_000L + 1) * 60_000L
        if (am.canScheduleExactAlarms()) am.setExact(AlarmManager.RTC, next, pi) else am.set(AlarmManager.RTC, next, pi)
    }
}

/** Each minute, and when the time, time zone or language changes (or the app is updated): redraw the clocks. */
class ClockTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ClockUpdater.updateAll(context)
        ClockTicker.schedule(context)
    }
}
