package app.dayframe.mobile.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dayframe.mobile.BuildConfig
import app.dayframe.mobile.DownloadUiState
import app.dayframe.mobile.data.DayTask
import app.dayframe.mobile.data.DayframeStore
import app.dayframe.mobile.data.FocusSession
import app.dayframe.mobile.data.UpdateService
import app.dayframe.mobile.data.UpdateSnapshot
import app.dayframe.mobile.data.UpdateStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class DayframePage { TODAY, FOCUS, INSIGHTS, ABOUT }

@Composable
fun DayframeApp(
    downloadState: DownloadUiState,
    onDownloadApk: (String, String) -> Unit,
    onInstallApk: () -> Unit
) {
    val context = LocalContext.current
    val tasks = remember { mutableStateListOf<DayTask>().apply { addAll(DayframeStore.loadTasks(context)) } }
    val sessions = remember { mutableStateListOf<FocusSession>().apply { addAll(DayframeStore.loadSessions(context)) } }

    var page by remember { mutableStateOf(DayframePage.TODAY) }
    var showSplash by remember { mutableStateOf(true) }
    var showAddTask by remember { mutableStateOf(false) }
    var focusFinished by remember { mutableStateOf(false) }

    var timerMinutes by remember { mutableIntStateOf(25) }
    var remainingSeconds by remember { mutableIntStateOf(25 * 60) }
    var timerEndAt by remember { mutableStateOf(0L) }
    var timerRunning by remember { mutableStateOf(false) }

    var updateSnapshot by remember { mutableStateOf<UpdateSnapshot?>(null) }
    var updateLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(1_250)
        showSplash = false
    }

    LaunchedEffect(timerRunning, timerEndAt) {
        if (timerRunning) {
            while (isActive) {
                val seconds = ((timerEndAt - System.currentTimeMillis() + 999L) / 1_000L)
                    .toInt().coerceAtLeast(0)
                remainingSeconds = seconds
                if (seconds == 0) {
                    timerRunning = false
                    val session = FocusSession(minutes = timerMinutes)
                    DayframeStore.addSession(context, session)
                    sessions.add(session)
                    focusFinished = true
                    break
                }
                delay(250L)
            }
        }
    }

    LaunchedEffect(page) {
        if (page == DayframePage.ABOUT) {
            while (isActive) {
                updateLoading = true
                updateSnapshot = UpdateService.check(BuildConfig.VERSION_NAME.substringBefore("-"))
                updateLoading = false
                delay(5 * 60 * 1_000L)
            }
        }
    }

    fun refreshUpdates() {
        scope.launch {
            updateLoading = true
            updateSnapshot = UpdateService.check(BuildConfig.VERSION_NAME.substringBefore("-"))
            updateLoading = false
        }
    }

    fun persistTasks() = DayframeStore.saveTasks(context, tasks.toList())

    fun toggleTask(task: DayTask) {
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            tasks[index] = tasks[index].copy(completed = !tasks[index].completed)
            persistTasks()
        }
    }

    fun deleteTask(task: DayTask) {
        tasks.removeAll { it.id == task.id }
        persistTasks()
    }

    fun addTask(title: String, priority: String) {
        val clean = title.trim()
        if (clean.isNotEmpty()) {
            tasks.add(0, DayTask(title = clean, priority = priority))
            persistTasks()
        }
        showAddTask = false
    }

    fun startTimer() {
        if (remainingSeconds <= 0) remainingSeconds = timerMinutes * 60
        timerEndAt = System.currentTimeMillis() + remainingSeconds * 1_000L
        timerRunning = true
    }

    fun pauseTimer() {
        if (timerRunning) {
            remainingSeconds = ((timerEndAt - System.currentTimeMillis() + 999L) / 1_000L)
                .toInt().coerceAtLeast(0)
            timerRunning = false
        }
    }

    fun resetTimer() {
        timerRunning = false
        remainingSeconds = timerMinutes * 60
        timerEndAt = 0L
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0B1120), Ink, Color(0xFF111327))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val wide = maxWidth >= 680.dp || (maxHeight < 480.dp && maxWidth >= 500.dp)

        if (showSplash) {
            SplashScene()
        } else if (wide) {
            Row(Modifier.fillMaxSize()) {
                SideNavigation(page = page, onSelect = { page = it })
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    PageContent(
                        page = page,
                        wide = true,
                        tasks = tasks,
                        sessions = sessions,
                        remainingSeconds = remainingSeconds,
                        timerMinutes = timerMinutes,
                        timerRunning = timerRunning,
                        onAddTask = { showAddTask = true },
                        onToggleTask = { task -> toggleTask(task) },
                        onDeleteTask = { task -> deleteTask(task) },
                        onOpenFocus = { page = DayframePage.FOCUS },
                        onPreset = { minutes ->
                            if (!timerRunning) {
                                timerMinutes = minutes
                                remainingSeconds = minutes * 60
                            }
                        },
                        onStartTimer = { startTimer() },
                        onPauseTimer = { pauseTimer() },
                        onResetTimer = { resetTimer() },
                        updateSnapshot = updateSnapshot,
                        updateLoading = updateLoading,
                        downloadState = downloadState,
                        onRefreshUpdates = ::refreshUpdates,
                        onDownloadApk = onDownloadApk,
                        onInstallApk = onInstallApk
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PageContent(
                        page = page,
                        wide = false,
                        tasks = tasks,
                        sessions = sessions,
                        remainingSeconds = remainingSeconds,
                        timerMinutes = timerMinutes,
                        timerRunning = timerRunning,
                        onAddTask = { showAddTask = true },
                        onToggleTask = ::toggleTask,
                        onDeleteTask = ::deleteTask,
                        onOpenFocus = { page = DayframePage.FOCUS },
                        onPreset = { minutes ->
                            if (!timerRunning) {
                                timerMinutes = minutes
                                remainingSeconds = minutes * 60
                            }
                        },
                        onStartTimer = ::startTimer,
                        onPauseTimer = ::pauseTimer,
                        onResetTimer = ::resetTimer,
                        updateSnapshot = updateSnapshot,
                        updateLoading = updateLoading,
                        downloadState = downloadState,
                        onRefreshUpdates = ::refreshUpdates,
                        onDownloadApk = onDownloadApk,
                        onInstallApk = onInstallApk
                    )
                }
                BottomNavigation(page = page, onSelect = { page = it })
            }
        }

        if (showAddTask) {
            AddTaskDialog(
                onDismiss = { showAddTask = false },
                onAdd = { title, priority -> addTask(title, priority) }
            )
        }

        if (focusFinished) {
            AlertDialog(
                onDismissRequest = { focusFinished = false },
                containerColor = PanelRaised,
                title = { Text("Session complete", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "You completed " + timerMinutes + " focused minutes. Your session has been added to Insights.",
                        color = TextMuted
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { focusFinished = false; page = DayframePage.INSIGHTS },
                        colors = ButtonDefaults.buttonColors(containerColor = Violet)
                    ) { Text("View progress") }
                },
                dismissButton = {
                    TextButton(onClick = { focusFinished = false }) { Text("Done", color = TextMuted) }
                }
            )
        }
    }
}

@Composable
private fun PageContent(
    page: DayframePage,
    wide: Boolean,
    tasks: List<DayTask>,
    sessions: List<FocusSession>,
    remainingSeconds: Int,
    timerMinutes: Int,
    timerRunning: Boolean,
    onAddTask: () -> Unit,
    onToggleTask: (DayTask) -> Unit,
    onDeleteTask: (DayTask) -> Unit,
    onOpenFocus: () -> Unit,
    onPreset: (Int) -> Unit,
    onStartTimer: () -> Unit,
    onPauseTimer: () -> Unit,
    onResetTimer: () -> Unit,
    updateSnapshot: UpdateSnapshot?,
    updateLoading: Boolean,
    downloadState: DownloadUiState,
    onRefreshUpdates: () -> Unit,
    onDownloadApk: (String, String) -> Unit,
    onInstallApk: () -> Unit
) {
    AnimatedContent(
        targetState = page,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            (fadeIn(tween(230)) + slideInVertically(tween(260)) { it / 24 }) togetherWith
                (fadeOut(tween(150)) + slideOutVertically(tween(170)) { -it / 30 })
        },
        label = "dayframe-page"
    ) { target ->
        when (target) {
            DayframePage.TODAY -> TodayPage(
                wide, tasks, sessions, timerMinutes, remainingSeconds,
                onAddTask, onToggleTask, onDeleteTask, onOpenFocus
            )
            DayframePage.FOCUS -> FocusPage(
                wide, remainingSeconds, timerMinutes, timerRunning,
                onPreset, onStartTimer, onPauseTimer, onResetTimer
            )
            DayframePage.INSIGHTS -> InsightsPage(wide, tasks, sessions)
            DayframePage.ABOUT -> AboutPage(
                wide, updateSnapshot, updateLoading, downloadState,
                onRefreshUpdates, onDownloadApk, onInstallApk
            )
        }
    }
}

@Composable
private fun TodayPage(
    wide: Boolean,
    tasks: List<DayTask>,
    sessions: List<FocusSession>,
    timerMinutes: Int,
    remainingSeconds: Int,
    onAddTask: () -> Unit,
    onToggleTask: (DayTask) -> Unit,
    onDeleteTask: (DayTask) -> Unit,
    onOpenFocus: () -> Unit
) {
    val done = tasks.count { it.completed }
    val focusToday = sessions.filter { isSameDay(it.completedAt, LocalDate.now()) }.sumOf { it.minutes }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE  ·  d MMMM", Locale.getDefault()))
                    .uppercase(Locale.getDefault()),
                color = Violet,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )
            Text(
                "Make room for\nwhat matters.",
                color = TextPrimary,
                fontSize = if (wide) 38.sp else 34.sp,
                lineHeight = if (wide) 43.sp else 39.sp,
                fontWeight = FontWeight.Bold
            )
            Text("A clearer plan. One good next step.", color = TextMuted, fontSize = 14.sp)
        }

        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(1.12f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TaskListCard(tasks, onAddTask, onToggleTask, onDeleteTask)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard("TASKS DONE", done.toString(), "All time", Mint, Modifier.weight(1f))
                        MetricCard("FOCUS TODAY", focusToday.toString() + "m", "Completed sessions", Violet, Modifier.weight(1f))
                    }
                }
                Column(Modifier.weight(.88f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FocusPreviewCard(timerMinutes, remainingSeconds, onOpenFocus)
                    WeeklyMiniCard(sessions)
                    QuietNoteCard()
                }
            }
        } else {
            FocusPreviewCard(timerMinutes, remainingSeconds, onOpenFocus)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("TASKS DONE", done.toString(), "All time", Mint, Modifier.weight(1f))
                MetricCard("FOCUS TODAY", focusToday.toString() + "m", "Completed sessions", Violet, Modifier.weight(1f))
            }
            TaskListCard(tasks, onAddTask, onToggleTask, onDeleteTask)
            QuietNoteCard()
        }
    }
}

@Composable
private fun FocusPreviewCard(
    timerMinutes: Int,
    remainingSeconds: Int,
    onClick: () -> Unit
) {
    val progress = (1f - remainingSeconds.toFloat() / (timerMinutes * 60f)).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(progress, spring(dampingRatio = .78f), label = "preview-progress")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(25.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF272B56), Color(0xFF202642), Color(0xFF173A3C))
                )
            )
            .border(1.dp, Color(0xFF6B70A6).copy(alpha = .38f), RoundedCornerShape(25.dp))
            .clickable(onClick = onClick)
            .padding(21.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("FOCUS SPACE", color = Color(0xFFB8B9E9), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
                Text("One thing at a time.", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (progress > 0f) "A session is in progress." else "A little quiet can go a long way.",
                    color = Color(0xFFB8C2D5), fontSize = 12.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("OPEN FOCUS", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Icon(Icons.Outlined.ArrowForward, null, tint = Mint, modifier = Modifier.size(16.dp).padding(start = 3.dp))
                }
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(Color.White.copy(alpha = .16f), -90f, 360f, false, style = Stroke(7.dp.toPx()))
                    drawArc(
                        Brush.sweepGradient(listOf(Violet, Mint, Violet)),
                        -90f,
                        360f * animatedProgress,
                        false,
                        style = Stroke(7.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(timerMinutes.toString(), color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("MIN", color = Color(0xFFB8C2D5), fontSize = 9.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun TaskListCard(
    tasks: List<DayTask>,
    onAddTask: () -> Unit,
    onToggle: (DayTask) -> Unit,
    onDelete: (DayTask) -> Unit
) {
    SurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your tasks", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(tasks.count { !it.completed }.toString() + " still on your list", color = TextMuted, fontSize = 12.sp)
                }
                TextButton(onClick = onAddTask) {
                    Icon(Icons.Outlined.Add, null, tint = Violet, modifier = Modifier.size(18.dp))
                    Text("Add task", color = Violet, fontWeight = FontWeight.SemiBold)
                }
            }
            if (tasks.isEmpty()) {
                EmptyTasks(onAddTask)
            } else {
                tasks.take(18).forEachIndexed { index, task ->
                    TaskRow(task, onToggle, onDelete)
                    if (index < tasks.take(18).lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineSoft.copy(alpha = .65f)))
                    }
                }
                if (tasks.size > 18) {
                    Text("Showing the first 18 tasks. Complete or remove a few to keep today clear.", color = TextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: DayTask, onToggle: (DayTask) -> Unit, onDelete: (DayTask) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconButton(onClick = { onToggle(task) }, modifier = Modifier.size(34.dp)) {
            Icon(
                if (task.completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (task.completed) "Mark task incomplete" else "Complete task",
                tint = if (task.completed) Mint else TextMuted,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                task.title,
                color = if (task.completed) TextMuted else TextPrimary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
            )
            PriorityLabel(task.priority)
        }
        IconButton(onClick = { onDelete(task) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete task", tint = TextMuted.copy(alpha = .75f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PriorityLabel(priority: String) {
    val tint = when (priority) {
        "High" -> Color(0xFFE5A3B2)
        "Low" -> Mint
        else -> Color(0xFFB7B7E9)
    }
    Text(
        priority.uppercase(Locale.getDefault()),
        color = tint,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(tint.copy(alpha = .10f)).padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@Composable
private fun EmptyTasks(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("A clear page.", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("Add one useful next step to get started.", color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
        OutlinedButton(onClick = onAdd) {
            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(17.dp))
            Text("Create first task")
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    caption: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    SurfaceCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Text(label, color = TextMuted, fontSize = 10.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
            Text(value, color = TextPrimary, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text(caption, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WeeklyMiniCard(sessions: List<FocusSession>) {
    val total = sessions.filter { isSameDay(it.completedAt, LocalDate.now()) }.sumOf { it.minutes }
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Today's focus", color = TextMuted, fontSize = 11.sp)
                Text(total.toString() + " minutes", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Built one session at a time.", color = TextMuted, fontSize = 10.sp)
            }
            Icon(Icons.Outlined.Timer, null, tint = Violet, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun QuietNoteCard() {
    SurfaceCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Violet.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Text("✳", color = Violet, fontSize = 17.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("A small reminder", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("You do not need to finish everything. Just begin with the next thing that matters.", color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun FocusPage(
    wide: Boolean,
    remainingSeconds: Int,
    timerMinutes: Int,
    timerRunning: Boolean,
    onPreset: (Int) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PageHeading("Focus", "Protect a little time for one thing.")
        if (wide) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    TimerRing(remainingSeconds, timerMinutes, timerRunning, 260.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(17.dp)) {
                    TimerControls(timerMinutes, timerRunning, remainingSeconds, onPreset, onStart, onPause, onReset)
                    FocusGuidance()
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                TimerRing(remainingSeconds, timerMinutes, timerRunning, 238.dp)
            }
            TimerControls(timerMinutes, timerRunning, remainingSeconds, onPreset, onStart, onPause, onReset)
            FocusGuidance()
        }
    }
}

@Composable
private fun TimerRing(
    remainingSeconds: Int,
    timerMinutes: Int,
    timerRunning: Boolean,
    diameter: androidx.compose.ui.unit.Dp
) {
    val progress = (1f - remainingSeconds.toFloat() / (timerMinutes * 60f)).coerceIn(0f, 1f)
    val animated by animateFloatAsState(progress, tween(450, easing = FastOutSlowInEasing), label = "timer-ring")
    val pulseTransition = rememberInfiniteTransition(label = "focus-pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = .92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_500), RepeatMode.Reverse),
        label = "focus-pulse-scale"
    )
    Box(Modifier.size(diameter).scale(if (timerRunning) pulse else 1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(Color.White.copy(alpha = .08f), -90f, 360f, false, style = Stroke(13.dp.toPx()))
            drawArc(
                Brush.sweepGradient(listOf(Violet, Color(0xFFB7A9FF), Mint, Violet)),
                -90f,
                360f * animated,
                false,
                style = Stroke(13.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                String.format(Locale.getDefault(), "%02d:%02d", remainingSeconds / 60, remainingSeconds % 60),
                color = TextPrimary,
                fontSize = 43.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-1).sp
            )
            Text(
                if (timerRunning) "FOCUS IN PROGRESS" else "MAKE A LITTLE SPACE",
                color = if (timerRunning) Mint else TextMuted,
                fontSize = 9.sp,
                letterSpacing = 1.8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TimerControls(
    selectedMinutes: Int,
    running: Boolean,
    remainingSeconds: Int,
    onPreset: (Int) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Text("SESSION LENGTH", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 25, 45, 60).forEach { minutes ->
                FilterChip(
                    selected = selectedMinutes == minutes,
                    onClick = { onPreset(minutes) },
                    enabled = !running,
                    label = { Text(minutes.toString() + "m", fontSize = 12.sp) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = if (running) onPause else onStart,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color.White)
            ) {
                Icon(if (running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, modifier = Modifier.size(20.dp))
                Text(if (running) "Pause session" else if (remainingSeconds < selectedMinutes * 60) "Resume focus" else "Start focus")
            }
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Reset timer", tint = TextPrimary)
            }
        }
        Text(
            if (running) "The timer keeps its place while you move around the app." else "Choose a length that feels manageable. You can pause whenever you need.",
            color = TextMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun FocusGuidance() {
    SurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("A simple rhythm", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            GuidanceRow("01", "Choose one task", "Decide what deserves your attention.")
            GuidanceRow("02", "Start the clock", "Give it a small, protected window.")
            GuidanceRow("03", "Take a real pause", "When the session ends, step away for a moment.")
        }
    }
}

@Composable
private fun GuidanceRow(number: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number, color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun InsightsPage(wide: Boolean, tasks: List<DayTask>, sessions: List<FocusSession>) {
    val totalMinutes = sessions.sumOf { it.minutes }
    val done = tasks.count { it.completed }
    val week = (6 downTo 0).map { offset -> LocalDate.now().minusDays(offset.toLong()) }
    val values = week.map { date ->
        sessions.filter { isSameDay(it.completedAt, date) }.sumOf { it.minutes }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PageHeading("Insights", "Progress you can actually feel.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("FOCUS TIME", formatMinutes(totalMinutes), "Completed sessions", Violet, Modifier.weight(1f))
            MetricCard("TASKS DONE", done.toString(), "Across your lists", Mint, Modifier.weight(1f))
            if (wide) MetricCard("SESSIONS", sessions.size.toString(), "Finished sessions", Amber, Modifier.weight(1f))
        }
        SurfaceCard {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Your week", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("Focus minutes by day · local session history", color = TextMuted, fontSize = 12.sp)
                }
                Row(
                    Modifier.fillMaxWidth().height(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val maxValue = maxOf(1, values.maxOrNull() ?: 0)
                    week.forEachIndexed { index, date ->
                        val heightFraction = values[index].toFloat() / maxValue.toFloat()
                        Column(
                            Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(values[index].toString(), color = TextMuted, fontSize = 9.sp)
                            Spacer(Modifier.height(7.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth(.62f)
                                    .height((14f + heightFraction * 82f).dp)
                                    .clip(RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp))
                                    .background(
                                        if (date == LocalDate.now()) Brush.verticalGradient(listOf(Mint, Violet))
                                        else Brush.verticalGradient(listOf(Violet.copy(alpha = .62f), VioletDeep.copy(alpha = .72f)))
                                    )
                            )
                            Spacer(Modifier.height(9.dp))
                            Text(date.dayOfWeek.name.take(1), color = if (date == LocalDate.now()) Mint else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Mint))
                    Text("  Today", color = TextMuted, fontSize = 11.sp)
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Violet))
                    Text("  Previous days", color = TextMuted, fontSize = 11.sp)
                }
            }
        }
        SurfaceCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your activity", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                InsightLine("Focus sessions completed", sessions.size.toString())
                InsightLine("Average session length", if (sessions.isEmpty()) "—" else (totalMinutes / sessions.size).toString() + " min")
                InsightLine("Tasks completed", done.toString())
                InsightLine("Tasks remaining", tasks.count { !it.completed }.toString())
            }
        }
        if (sessions.isEmpty()) {
            SurfaceCard {
                Text("Your first completed focus session will appear here. Start small; the chart will fill itself from real sessions.", color = TextMuted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun InsightLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AboutPage(
    wide: Boolean,
    snapshot: UpdateSnapshot?,
    loading: Boolean,
    downloadState: DownloadUiState,
    onRefresh: () -> Unit,
    onDownload: (String, String) -> Unit,
    onInstall: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        PageHeading("About DAYFRAME", "A calm workspace, built to stay yours.")
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProjectIdentityCard()
                    UpdatePanel(snapshot, loading, downloadState, onRefresh, onDownload, onInstall) { uri -> uriHandler.openUri(uri) }
                }
                Column(Modifier.weight(.95f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AboutDetailsCard()
                    SourceHistoryCard()
                }
            }
        } else {
            ProjectIdentityCard()
            UpdatePanel(snapshot, loading, downloadState, onRefresh, onDownload, onInstall) { uri -> uriHandler.openUri(uri) }
            AboutDetailsCard()
            SourceHistoryCard()
        }
    }
}

@Composable
private fun ProjectIdentityCard() {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DayframeMark(62.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("DAYFRAME", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
                Text("Make room for what matters.", color = TextMuted, fontSize = 12.sp)
                Text("Created by CF Studio", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            "DAYFRAME brings daily tasks, focus sessions and personal progress into one quiet place. It is designed to feel quick on a phone, stay useful without an account, and keep your planning data on your device.",
            color = TextMuted,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallTag("OPEN SOURCE")
            SmallTag("LOCAL FIRST")
            SmallTag("NO ACCOUNT")
        }
        Text("App version  " + BuildConfig.VERSION_NAME, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text("Android package  app.dayframe.mobile", color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun UpdatePanel(
    snapshot: UpdateSnapshot?,
    loading: Boolean,
    downloadState: DownloadUiState,
    onRefresh: () -> Unit,
    onDownload: (String, String) -> Unit,
    onInstall: () -> Unit,
    openUri: (String) -> Unit
) {
    val status = snapshot?.status
    val accent = when (status) {
        UpdateStatus.READY -> Mint
        UpdateStatus.BUILDING -> Amber
        UpdateStatus.MISSING -> Coral
        UpdateStatus.ERROR -> Coral
        null -> TextMuted
    }
    val headline = when (status) {
        UpdateStatus.READY -> if (snapshot?.updateAvailable == true) "An update is ready" else "You're up to date"
        UpdateStatus.BUILDING -> "A build is in progress"
        UpdateStatus.MISSING -> "No APK is published"
        UpdateStatus.ERROR -> "Could not check GitHub"
        null -> "Checking project status"
    }
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("UPDATE CENTER", color = Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                Text("Project status", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onRefresh, enabled = !loading) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Violet, strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Refresh, contentDescription = "Refresh GitHub status", tint = TextMuted)
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = .09f)).padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(headline, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    when (status) {
                        UpdateStatus.READY -> "A real APK asset was found in the public GitHub release."
                        UpdateStatus.BUILDING -> "GitHub Actions reports queued or in-progress work."
                        UpdateStatus.MISSING -> "There is no downloadable APK asset in the latest published release."
                        UpdateStatus.ERROR -> snapshot?.errorMessage ?: "The live status request failed."
                        null -> if (loading) "Fetching releases and workflow status…" else "Tap refresh to check GitHub."
                    },
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
        }

        if (snapshot != null && snapshot.status != UpdateStatus.ERROR) {
            val version = snapshot.latestVersion
            if (version != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("LATEST RELEASE", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(version, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (snapshot.isPublishedToday) "PUBLISHED TODAY" else "LATEST PUBLISHED BUILD",
                        color = if (snapshot.isPublishedToday) Mint else TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = .7.sp
                    )
                }
                snapshot.publishedAt?.let {
                    Text("Published " + formatPublishedDate(it), color = TextMuted, fontSize = 11.sp)
                }
                if (snapshot.updateAvailable) {
                    Text("A newer version than the installed app is available.", color = Mint, fontSize = 12.sp)
                } else if (snapshot.status == UpdateStatus.READY) {
                    Text("This release matches the installed source version.", color = TextMuted, fontSize = 11.sp)
                }
            } else if (snapshot.status == UpdateStatus.BUILDING) {
                Text("No published release yet. The build has not finished.", color = TextMuted, fontSize = 12.sp)
            }

            if (snapshot.notes.isNotEmpty() && version != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("RELEASE NOTES", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    snapshot.notes.forEach { note ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 6.dp).size(5.dp).clip(CircleShape).background(Violet))
                            Text(note, color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
            if (snapshot.workflowConclusion == "failure" && snapshot.status == UpdateStatus.READY) {
                Text("The most recent build attempt failed, but the previously published APK is still available.", color = Amber, fontSize = 11.sp, lineHeight = 16.sp)
            }
            snapshot.workflowUrl?.let { url ->
                TextButton(onClick = { openUri(url) }) {
                    Text("View GitHub build", color = Violet, fontSize = 12.sp)
                    Icon(Icons.Outlined.OpenInNew, null, tint = Violet, modifier = Modifier.size(14.dp).padding(start = 3.dp))
                }
            }
            if (snapshot.status == UpdateStatus.READY && snapshot.apkUrl != null && version != null) {
                when {
                    downloadState.completed -> Button(
                        onClick = onInstall,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Install downloaded APK", fontWeight = FontWeight.Bold)
                    }
                    downloadState.downloading -> Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(PanelRaised).padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Mint, strokeWidth = 2.dp)
                        Text("Downloading through Android Download Manager…", color = TextMuted, fontSize = 11.sp)
                    }
                    else -> Button(
                        onClick = { onDownload(snapshot.apkUrl, version) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Violet),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                        Text("Download APK", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (snapshot.status == UpdateStatus.MISSING) {
                Text(
                    "Green only appears when a published APK asset exists. This page does not treat a source commit or CI artifact as a released installer.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
        downloadState.error?.let {
            Text(it, color = Coral, fontSize = 11.sp, lineHeight = 16.sp)
        }
        if (snapshot != null) {
            val checkedLabel = DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.getDefault())
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(snapshot.checkedAt))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("LAST CHECK", color = TextMuted.copy(alpha = .75f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .9.sp)
                Spacer(Modifier.weight(1f))
                Text(checkedLabel, color = TextMuted, fontSize = 10.sp)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Installed  " + snapshot.currentVersion, color = TextMuted, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Text("Latest  " + (snapshot.latestVersion ?: "Not published"), color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            "Live GitHub check · refreshes on entry and every 5 minutes while this page is open.",
            color = TextMuted.copy(alpha = .8f),
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun AboutDetailsCard() {
    SurfaceCard {
        Text("Built around your day", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(
            "The task list and completed focus sessions are stored in app-private local preferences. No account is needed, and planner content is not sent to the update service. The only network requests are made to the public GitHub API when checking project releases and build activity.",
            color = TextMuted,
            fontSize = 12.sp,
            lineHeight = 19.sp
        )
        DetailLine("App name", "DAYFRAME")
        DetailLine("Package", "app.dayframe.mobile")
        DetailLine("Minimum Android", "8.0 · API 26")
        DetailLine("UI framework", "Jetpack Compose")
        DetailLine("Creator", "CF Studio")
        DetailLine("License", "MIT")
    }
}

@Composable
private fun SourceHistoryCard() {
    SurfaceCard {
        Text("What's new in 2.0.0", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("MAJOR UPDATE  ·  NOVEMBER 2026", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text("A clearer, more informative update experience.", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        listOf(
            "Live version comparison: installed version versus latest published release.",
            "More transparent release status, including build activity and APK availability.",
            "Release notes are displayed directly inside the app when GitHub publishes them.",
            "Added a visible last-checked timestamp and manual refresh control.",
            "Improved update messaging so an unpublished build is never presented as a downloadable APK."
        ).forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 5.dp).size(5.dp).clip(CircleShape).background(Mint))
                Text(item, color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        Text("The original planner, focus timer, local storage and weekly insights remain part of DAYFRAME.", color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
private fun SmallTag(text: String) {
    Text(
        text,
        color = Mint,
        fontSize = 8.sp,
        letterSpacing = .6.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Mint.copy(alpha = .09f)).padding(horizontal = 7.dp, vertical = 5.dp)
    )
}

@Composable
private fun AddTaskDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Normal") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelRaised,
        title = { Text("Add a task", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(90) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("What needs your attention?") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet,
                        focusedLabelColor = Violet,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        unfocusedBorderColor = OutlineSoft
                    )
                )
                Text("PRIORITY", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Low", "Normal", "High").forEach {
                        FilterChip(selected = priority == it, onClick = { priority = it }, label = { Text(it, fontSize = 11.sp) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(title, priority) },
                enabled = title.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Violet)
            ) { Text("Save task") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

@Composable
private fun SideNavigation(page: DayframePage, onSelect: (DayframePage) -> Unit) {
    Column(
        Modifier.width(86.dp).fillMaxHeight().padding(start = 12.dp, top = 18.dp, bottom = 18.dp)
            .clip(RoundedCornerShape(23.dp)).background(Color(0xFF10182A).copy(alpha = .95f))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DayframeMark(42.dp)
        Spacer(Modifier.height(13.dp))
        DayframePage.values().forEach { target ->
            NavItem(target, selected = page == target, vertical = true, onClick = { onSelect(target) })
        }
        Spacer(Modifier.weight(1f))
        Text("CF", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun BottomNavigation(page: DayframePage, onSelect: (DayframePage) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp)).background(Color(0xFF111A2C).copy(alpha = .98f))
            .padding(horizontal = 5.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DayframePage.values().forEach { target ->
            NavItem(target, selected = page == target, vertical = false, onClick = { onSelect(target) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavItem(
    page: DayframePage,
    selected: Boolean,
    vertical: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint by animateFloatAsState(if (selected) 1f else .65f, tween(220), label = "nav-tint")
    val contentColor = if (selected) Violet else TextMuted
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.then(
            Modifier
                .clip(shape)
                .background(if (selected) Violet.copy(alpha = .12f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = if (vertical) 8.dp else 7.dp, vertical = 10.dp)
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = when (page) {
                DayframePage.TODAY -> Icons.Outlined.Home
                DayframePage.FOCUS -> Icons.Outlined.Timer
                DayframePage.INSIGHTS -> Icons.Outlined.BarChart
                DayframePage.ABOUT -> Icons.Outlined.Info
            },
            contentDescription = page.name,
            tint = contentColor.copy(alpha = tint),
            modifier = Modifier.size(21.dp)
        )
        Text(
            when (page) {
                DayframePage.TODAY -> "Today"
                DayframePage.FOCUS -> "Focus"
                DayframePage.INSIGHTS -> "Insights"
                DayframePage.ABOUT -> "About"
            },
            color = contentColor,
            fontSize = if (vertical) 9.sp else 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = TextPrimary, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextMuted, fontSize = 13.sp)
    }
}

@Composable
private fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Panel.copy(alpha = .96f),
        border = BorderStroke(1.dp, OutlineSoft.copy(alpha = .7f))
    ) {
        Column(
            Modifier.padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun SplashScene() {
    val transition = rememberInfiniteTransition(label = "splash-orbit")
    val rotation by transition.animateFloat(
        0f, 360f,
        animationSpec = infiniteRepeatable(tween(3_600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "splash-rotation"
    )
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(if (visible) 1f else .82f, spring(dampingRatio = .62f), label = "splash-scale")
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(500), label = "splash-alpha")
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF0C1222), Color(0xFF15162D), Color(0xFF10222B)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Box(Modifier.size(116.dp).scale(scale).alpha(alpha), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(Color.White.copy(alpha = .12f), -90f, 360f, false, style = Stroke(2.dp.toPx()))
                    rotate(rotation) {
                        drawArc(Brush.sweepGradient(listOf(Violet, Mint, Violet)), -65f, 260f, false, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                    }
                    drawCircle(Mint, radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * .77f, size.height * .73f))
                }
                DayframeMark(58.dp)
            }
            Text("DAYFRAME", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp, modifier = Modifier.alpha(alpha))
            Text("Make room for what matters.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.alpha(alpha))
            Row(
                Modifier.padding(top = 13.dp).width(92.dp).height(3.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = .10f))
            ) {
                Box(Modifier.fillMaxWidth(.62f).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(Violet, Mint))))
            }
        }
        Text("CF STUDIO  /  ANDROID", color = TextMuted.copy(alpha = .6f), fontSize = 9.sp, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
    }
}

@Composable
private fun DayframeMark(markSize: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(markSize)) {
        val stroke = markSize.toPx() * .095f
        drawArc(Violet, 115f, 250f, false, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(Mint, -65f, 240f, false, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(Color(0xFFE5E8FF), 10f, 105f, false, style = Stroke(stroke, cap = StrokeCap.Round))
        drawCircle(Mint, radius = stroke * .68f, center = androidx.compose.ui.geometry.Offset(size.width * .76f, size.height * .76f))
    }
}

private fun isSameDay(timestamp: Long, date: LocalDate): Boolean {
    val local = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    return local == date
}

private fun formatMinutes(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) hours.toString() + "h " + rest + "m" else minutes.toString() + "m"
}

private fun formatPublishedDate(raw: String): String = runCatching {
    Instant.parse(raw).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy  ·  HH:mm", Locale.getDefault()))
}.getOrDefault(raw)

