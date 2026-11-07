package dev.ririxi.bunnylock.data

import android.app.Activity
import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.icu.text.DisplayContext
import android.icu.text.LocaleDisplayNames
import android.icu.util.ULocale
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import dev.ririxi.bunnylock.BuildConfig
import dev.ririxi.bunnylock.R
import java.util.Locale

object AppLanguage {
    private val tags = BuildConfig.LOCALES.toList()

    val defaultTag: String = BuildConfig.DEFAULT_LOCALE

    private val maximized by lazy { tags.associateWith { ULocale.addLikelySubtags(ULocale.forLanguageTag(it)) } }

    private const val KEY = "language"

    private val perAppLocales get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun nativeNames(context: Context): Map<String, String> {
        val defaultName = nameIn(context, defaultTag)
        return tags.associateWith { tag ->
            val name = nameIn(context, tag)
            val untranslated = tag != defaultTag && name == defaultName
            if (untranslated) displayName(tag, inLanguage = tag) else name
        }
    }

    fun displayName(
        tag: String,
        inLanguage: String,
    ): String =
        LocaleDisplayNames
            .getInstance(ULocale.forLanguageTag(inLanguage), DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU)
            .localeDisplayName(ULocale.forLanguageTag(tag))

    fun suggested(): List<String> {
        val system = Resources.getSystem().configuration.locales
        return (0 until system.size()).mapNotNull { supportedTag(system[it]) }.distinct()
    }

    fun shownTag(locale: Locale): String = supportedTag(locale) ?: defaultTag

    private fun supportedTag(locale: Locale): String? {
        val wanted = ULocale.addLikelySubtags(ULocale.forLocale(locale))
        val sameScript = maximized.filterValues { it.language == wanted.language && it.script == wanted.script }
        return sameScript.entries.firstOrNull { it.value.country == wanted.country }?.key ?: sameScript.keys.firstOrNull()
    }

    private fun nameIn(
        context: Context,
        tag: String,
    ): String {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return context.createConfigurationContext(config).getString(R.string.settings_language_name)
    }

    fun current(context: Context): String {
        val chosen =
            if (perAppLocales) {
                context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
            } else {
                storedTag(context)
            }.substringBefore(',')
        if (chosen.isEmpty()) return ""
        return supportedTag(Locale.forLanguageTag(chosen)) ?: chosen
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