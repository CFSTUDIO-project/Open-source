package app.dayframe.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
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

object UpdateService {
    private const val API = "https://api.github.com/repos/CFSTUDIO-project/Open-source"
    private const val USER_AGENT = "DAYFRAME-Android"

    suspend fun check(currentVersion: String): UpdateSnapshot = withContext(Dispatchers.IO) {
        try {
            val releases = readArray("$API/releases?per_page=8")
            val release = (0 until releases.length())
                .mapNotNull { releases.optJSONObject(it) }
                .firstOrNull { !it.optBoolean("draft", true) }

            val run = runCatching { readObject("$API/actions/runs?per_page=1&branch=main")
                .optJSONArray("workflow_runs")?.optJSONObject(0) }.getOrNull()
            val workflowState = run?.optString("status")?.takeIf { it.isNotBlank() && it != "null" }
            val workflowConclusion = run?.optString("conclusion")?.takeIf { it.isNotBlank() && it != "null" }
            val workflowUrl = run?.optString("html_url")?.takeIf { it.startsWith("https://github.com/") }

            if (workflowState == "queued" || workflowState == "in_progress") {
                return@withContext UpdateSnapshot(
                    status = UpdateStatus.BUILDING,
                    currentVersion = currentVersion,
                    latestVersion = release?.optString("tag_name"),
                    releaseName = release?.optString("name"),
                    publishedAt = release?.optString("published_at"),
                    apkUrl = findApk(release?.optJSONArray("assets")),
                    releaseUrl = release?.optString("html_url"),
                    notes = releaseNotes(release?.optString("body").orEmpty()),
                    workflowState = workflowState,
                    workflowConclusion = workflowConclusion,
                    workflowUrl = workflowUrl
                )
            }

            if (release == null) {
                return@withContext UpdateSnapshot(
                    status = UpdateStatus.MISSING,
                    currentVersion = currentVersion,
                    workflowState = workflowState,
                    workflowConclusion = workflowConclusion,
                    workflowUrl = workflowUrl
                )
            }

            val tag = release.optString("tag_name").ifBlank { "Unknown" }
            val published = release.optString("published_at").takeIf { it.isNotBlank() && it != "null" }
            val assetUrl = findApk(release.optJSONArray("assets"))
            val isNewer = compareVersions(tag, currentVersion) > 0
            val today = published?.let { value ->
                runCatching {
                    Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
                }.getOrDefault(false)
            } ?: false

            UpdateSnapshot(
                status = if (assetUrl != null) UpdateStatus.READY else UpdateStatus.MISSING,
                currentVersion = currentVersion,
                latestVersion = tag,
                releaseName = release.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: tag,
                publishedAt = published,
                isPublishedToday = today,
                updateAvailable = isNewer,
                apkUrl = assetUrl,
                releaseUrl = release.optString("html_url").takeIf { it.startsWith("https://github.com/") },
                notes = releaseNotes(release.optString("body")),
                workflowState = workflowState,
                workflowConclusion = workflowConclusion,
                workflowUrl = workflowUrl
            )
        } catch (error: Exception) {
            UpdateSnapshot(
                status = UpdateStatus.ERROR,
                currentVersion = currentVersion,
                errorMessage = error.message?.take(180) ?: "Could not reach GitHub."
            )
        }
    }

    private fun readArray(address: String): JSONArray =
        readResponse(address) as? JSONArray ?: throw IOException("Unexpected GitHub response.")

    private fun readObject(address: String): JSONObject =
        readResponse(address) as? JSONObject ?: throw IOException("Unexpected GitHub response.")

    private fun readResponse(address: String): Any {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input)).use { reader -> reader.readText() }
            }.orEmpty()
            if (code !in 200..299) {
                throw IOException(
                    when (code) {
                        403, 429 -> "GitHub API rate limit reached. Try again later."
                        404 -> "The public repository endpoint was not found."
                        else -> "GitHub returned HTTP $code."
                    }
                )
            }
            return when (body.trimStart().firstOrNull()) {
                '[' -> JSONArray(body)
                '{' -> JSONObject(body)
                else -> throw IOException("GitHub returned an empty response.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun findApk(assets: JSONArray?): String? {
        if (assets == null) return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && url.startsWith("https://github.com/")) {
                return url
            }
        }
        return null
    }

    private fun releaseNotes(raw: String): List<String> = raw
        .lineSequence()
        .map { it.trim().removePrefix("-").removePrefix("*").trim() }
        .filter { it.isNotBlank() && it != "null" }
        .take(7)
        .toList()
        .ifEmpty { listOf("No release notes have been added yet.") }

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
