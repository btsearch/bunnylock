package dev.ririxi.bunnylock.data

import android.app.Activity
import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

object AppLanguage {
    val tags = listOf("en", "pl", "zh-CN")

    private const val KEY = "language"

    private val perAppLocales get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun current(context: Context): String =
        if (perAppLocales) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            storedTag(context)
        }

    fun set(
        activity: Activity,
        tag: String,
    ) {
        if (perAppLocales) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
            return
        }
        prefs(activity).edit { putString(KEY, tag) }
        apply(activity.application)
        activity.recreate()
    }

    fun wrap(base: Context): Context =
        if (perAppLocales) base else base.createConfigurationContext(localized(base))

    fun apply(app: Application) {
        if (perAppLocales) return
        val config = localized(app)
        Locale.setDefault(config.locales[0])
        @Suppress("DEPRECATION")
        app.resources.updateConfiguration(config, app.resources.displayMetrics)
    }

    private fun localized(context: Context): Configuration {
        val tag = storedTag(context)
        val locales = if (tag.isEmpty()) Resources.getSystem().configuration.locales else LocaleList.forLanguageTags(tag)
        return Configuration(context.resources.configuration).apply { setLocales(locales) }
    }

    private fun storedTag(context: Context): String = prefs(context).getString(KEY, "").orEmpty()

    private fun prefs(context: Context) = context.getSharedPreferences("bunnylock", Context.MODE_PRIVATE)
}
