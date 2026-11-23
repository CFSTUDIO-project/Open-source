package app.dayframe.mobile.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class DayTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val priority: String = "Normal",
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class FocusSession(
    val id: String = UUID.randomUUID().toString(),
    val minutes: Int,
    val completedAt: Long = System.currentTimeMillis()
)

object DayframeStore {
    private const val PREFS = "dayframe_local_v1"
    private const val TASKS = "tasks_json"
    private const val SESSIONS = "sessions_json"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadTasks(context: Context): List<DayTask> = runCatching {
        val raw = prefs(context).getString(TASKS, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val title = item.optString("title").trim()
                if (title.isNotEmpty()) {
                    add(
                        DayTask(
                            id = item.optString("id", UUID.randomUUID().toString()),
                            title = title,
                            priority = item.optString("priority", "Normal"),
                            completed = item.optBoolean("completed", false),
                            createdAt = item.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    fun saveTasks(context: Context, tasks: List<DayTask>) {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(
                JSONObject()
                    .put("id", task.id)
                    .put("title", task.title)
                    .put("priority", task.priority)
                    .put("completed", task.completed)
                    .put("createdAt", task.createdAt)
            )
        }
        prefs(context).edit().putString(TASKS, array.toString()).apply()
    }

    fun loadSessions(context: Context): List<FocusSession> = runCatching {
        val raw = prefs(context).getString(SESSIONS, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val minutes = item.optInt("minutes", 0)
                if (minutes > 0) {
                    add(
                        FocusSession(
                            id = item.optString("id", UUID.randomUUID().toString()),
                            minutes = minutes,
                            completedAt = item.optLong("completedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    fun addSession(context: Context, session: FocusSession) {
        val updated = (loadSessions(context) + session).takeLast(500)
        val array = JSONArray()
        updated.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("minutes", it.minutes)
                    .put("completedAt", it.completedAt)
            )
        }
        prefs(context).edit().putString(SESSIONS, array.toString()).apply()
    }
}
