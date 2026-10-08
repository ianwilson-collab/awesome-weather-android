package com.prostellis.awe.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.prostellis.awe.data.Store

/** Shared behavior for both widget sizes. */
abstract class AweWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { WidgetUpdater.update(context, it) }
        Refresh.schedule(context)
        // A new widget (or one with no data yet) gets fresh data right away.
        val store = Store(context)
        val needsData = ids.filter { store.widgetUpdatedAt(it) == 0L || store.forecastForCurrentPlace() == null }
        if (needsData.isNotEmpty()) Refresh.now(context, needsData.toIntArray())
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        WidgetUpdater.update(context, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val store = Store(context)
        ids.forEach(store::forgetWidget)
    }

    override fun onDisabled(context: Context) {
        if (WidgetUpdater.allIds(context).isEmpty()) Refresh.cancel(context)
    }
}

/** 2×1: current temperature and conditions. */
class SmallWidget : AweWidget()

/** 4×2: now plus 5 days; 7 days when stretched wider. */
class ForecastWidget : AweWidget()
