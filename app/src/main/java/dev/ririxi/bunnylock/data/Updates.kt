package dev.ririxi.bunnylock.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Release(
    val version: String,
    val notes: String,
    val url: String,
)

object Updates {
    private const val LATEST_RELEASE = "https://api.github.com/repos/btsearch/bunnylock/releases/latest"
    private const val TIMEOUT_MS = 10_000

    private val COMMIT_LINK = Regex("""\s*\(\[[0-9a-f]{7,40}\]\([^)]*\)\)""")
    private val HEADING_MARK = Regex("""^#+\s+""", RegexOption.MULTILINE)

    suspend fun newerThan(installed: String): Release? =
        withContext(Dispatchers.IO) {
            val latest = JSONObject(fetch(LATEST_RELEASE))
            val version = latest.getString("tag_name").removePrefix("v")
            if (!isNewer(version, installed)) return@withContext null
            val assets = latest.optJSONArray("assets") ?: JSONArray()
            val apk = List(assets.length()) { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") }
            val notes = if (latest.isNull("body")) "" else latest.getString("body")
            Release(
                version = version,
                notes = notes.replace(COMMIT_LINK, "").replace(HEADING_MARK, "").trim(),
                url = apk?.getString("browser_download_url") ?: latest.getString("html_url"),
            )
        }

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "GitHub answered with status ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun isNewer(
        version: String,
        installed: String,
    ): Boolean {
        val newNumbers = numbersOf(version)
        val installedNumbers = numbersOf(installed)
        for (index in 0 until maxOf(newNumbers.size, installedNumbers.size)) {
            val difference = newNumbers.getOrElse(index) { 0 } - installedNumbers.getOrElse(index) { 0 }
            if (difference != 0) return difference > 0
        }
        val newLabel = prereleaseOf(version)
        val installedLabel = prereleaseOf(installed)
        return when {
            newLabel.isEmpty() -> installedLabel.isNotEmpty()
            installedLabel.isEmpty() -> false
            else -> comparePrereleases(newLabel, installedLabel) > 0
        }
    }

    private fun numbersOf(version: String): List<Int> =
        version.substringBefore('+').substringBefore('-').split('.').map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    private fun prereleaseOf(version: String): List<String> =
        version.substringBefore('+').substringAfter('-', "").split('.').filter(String::isNotEmpty)

    private fun comparePrereleases(
        first: List<String>,
        second: List<String>,
    ): Int {
        for ((a, b) in first.zip(second)) {
            val aNumeric = a.all(Char::isDigit)
            val bNumeric = b.all(Char::isDigit)
            val order =
                when {
                    aNumeric && bNumeric -> a.toBigInteger().compareTo(b.toBigInteger())
                    aNumeric -> -1
                    bNumeric -> 1
                    else -> a.compareTo(b)
                }
            if (order != 0) return order
        }
        return first.size.compareTo(second.size)
    }
}
