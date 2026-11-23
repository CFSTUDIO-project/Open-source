package app.dayframe.mobile.data

import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class UpdateStatus {
    READY,
    BUILDING,
    MISSING,
    ERROR
}

data class UpdateSnapshot(
    val status: UpdateStatus,
    val currentVersion: String,
    val latestVersion: String? = null,
    val releaseName: String? = null,
    val publishedAt: String? = null,
    val isPublishedToday: Boolean = false,
    val updateAvailable: Boolean = false,
    val apkUrl: String? = null,
    val releaseUrl: String? = null,
    val notes: List<String> = emptyList(),
    val workflowState: String? = null,
    val workflowConclusion: String? = null,
    val workflowUrl: String? = null,
    val checkedAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

private data class ReleaseEntry(
    val tag: String,
    val title: String,
    val publishedAt: String?,
    val releaseUrl: String,
    val notes: List<String>
)

object UpdateService {
    private const val OWNER = "CFSTUDIO-project"
    private const val REPOSITORY = "Open-source"
    private const val RELEASE_FEED =
        "https://github.com/$OWNER/$REPOSITORY/releases.atom"
    private const val USER_AGENT = "DAYFRAME-Android"

    /**
     * Uses GitHub's public Atom release feed instead of the unauthenticated REST API.
     * This avoids spending the small shared GitHub API rate-limit budget every time
     * the About page refreshes. The APK URL is only exposed after its asset responds.
     */
    suspend fun check(currentVersion: String): UpdateSnapshot = withContext(Dispatchers.IO) {
        try {
            val release = readLatestRelease()
            if (release == null) {
                return@withContext UpdateSnapshot(
                    status = UpdateStatus.MISSING,
                    currentVersion = currentVersion,
                    errorMessage = "No public GitHub release has been published yet."
                )
            }

            val assetUrl = buildApkUrl(release.tag)
            val apkAvailable = assetUrl != null && isDownloadAvailable(assetUrl)
            val today = release.publishedAt?.let { value ->
                runCatching {
                    Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
                }.getOrDefault(false)
            } ?: false

            UpdateSnapshot(
                status = if (apkAvailable) UpdateStatus.READY else UpdateStatus.MISSING,
                currentVersion = currentVersion,
                latestVersion = release.tag,
                releaseName = release.title,
                publishedAt = release.publishedAt,
                isPublishedToday = today,
                updateAvailable = compareVersions(release.tag, currentVersion) > 0,
                apkUrl = if (apkAvailable) assetUrl else null,
                releaseUrl = release.releaseUrl,
                notes = release.notes
            )
        } catch (error: Exception) {
            UpdateSnapshot(
                status = UpdateStatus.ERROR,
                currentVersion = currentVersion,
                errorMessage = when {
                    error.message?.contains("timed out", ignoreCase = true) == true ->
                        "GitHub took too long to respond. Check your connection and try again."
                    else -> error.message?.take(180) ?: "Could not reach GitHub Releases."
                }
            )
        }
    }

    private fun readLatestRelease(): ReleaseEntry? {
        val connection = openConnection(RELEASE_FEED).apply {
            setRequestProperty("Accept", "application/atom+xml, application/xml, text/xml")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("GitHub Releases returned HTTP $code.")
            val parser = Xml.newPullParser()
            connection.inputStream.use { parser.setInput(it, "UTF-8") }

            var inEntry = false
            var tag: String? = null
            var title: String? = null
            var published: String? = null
            var releaseUrl: String? = null
            var rawNotes: String? = null

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "entry" -> {
                            inEntry = true
                            tag = null
                            title = null
                            published = null
                            releaseUrl = null
                            rawNotes = null
                        }
                        "title" -> if (inEntry) title = parser.nextText()
                        "published" -> if (inEntry) published = parser.nextText()
                        "content" -> if (inEntry) rawNotes = parser.nextText()
                        "link" -> if (inEntry) {
                            val rel = parser.getAttributeValue(null, "rel")
                            val href = parser.getAttributeValue(null, "href")
                            if (rel == "alternate" && href?.startsWith("https://github.com/$OWNER/$REPOSITORY/releases/tag/") == true) {
                                releaseUrl = href
                                tag = href.substringAfterLast("/")
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "entry" && inEntry) {
                        val safeTag = tag
                        val safeUrl = releaseUrl
                        if (!safeTag.isNullOrBlank() && !safeUrl.isNullOrBlank()) {
                            return ReleaseEntry(
                                tag = safeTag,
                                title = title?.takeIf { it.isNotBlank() } ?: safeTag,
                                publishedAt = published,
                                releaseUrl = safeUrl,
                                notes = releaseNotes(rawNotes.orEmpty())
                            )
                        }
                        inEntry = false
                    }
                }
                parser.next()
            }
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun buildApkUrl(tag: String): String? {
        val version = tag.removePrefix("v").removePrefix("V")
        if (!version.matches(Regex("[A-Za-z0-9._-]+"))) return null
        return "https://github.com/$OWNER/$REPOSITORY/releases/download/$tag/DAYFRAME-$version.apk"
    }

    private fun isDownloadAvailable(address: String): Boolean {
        val connection = openConnection(address).apply {
            requestMethod = "HEAD"
            instanceFollowRedirects = true
        }
        return try {
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(address: String): HttpURLConnection =
        (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Cache-Control", "no-cache")
        }

    private fun releaseNotes(raw: String): List<String> {
        val readable = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
        return readable.lineSequence()
            .map { it.trim().removePrefix("-").removePrefix("*").trim() }
            .filter { it.isNotBlank() && it != "null" }
            .take(7)
            .toList()
            .ifEmpty { listOf("No release notes have been added yet.") }
    }

    private fun compareVersions(remote: String, local: String): Int {
        fun parts(value: String): List<Int> =
            value.trim().removePrefix("v").removePrefix("V")
                .substringBefore("-")
                .substringBefore("+")
                .split(".")
                .map { segment -> segment.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

        val left = parts(remote)
        val right = parts(local)
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }
}
