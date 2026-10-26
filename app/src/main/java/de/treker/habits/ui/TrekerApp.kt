package de.treker.habits.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.treker.habits.data.DailyRecordEntity
import de.treker.habits.data.HabitEntity
import de.treker.habits.data.UserSettings
import de.treker.habits.data.UserTemplateEntity
import de.treker.habits.domain.Tracking
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class Section(val label: String) { HABITS("Привычки"), TODAY("Сегодня"), TEMPLATES("Шаблоны"), PROGRESS("Прогресс"), SETTINGS("Настройки") }

@Composable
fun TrekerApp(vm: HabitViewModel) {
    val habits by vm.habits.collectAsStateWithLifecycle()
    val userTemplates by vm.userTemplates.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val selectedDate by vm.selectedDate.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val context = LocalContext.current
    var section by rememberSaveable { mutableStateOf(Section.HABITS.name) }
    var showNewHabit by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HabitEntity?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(records) {
        while (true) {
            now = System.currentTimeMillis()
            if (records.any { it.timerRunning && (it.timerDeadlineMillis ?: Long.MAX_VALUE) <= now }) vm.finishElapsedTimers()
            delay(1_000)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(vm.exportJson()) } }
                .onFailure { dialogMessage = "Не удалось сохранить резервную копию: ${it.localizedMessage}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val json = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
                .getOrElse { dialogMessage = "Не удалось прочитать файл: ${it.localizedMessage}"; null }
            if (json != null) pendingImport = json
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) dialogMessage = "Уведомления выключены в настройках Android. Привычки продолжат работать; системные напоминания появятся после выдачи разрешения."
    }
    val askForNotificationPermission: () -> Unit = {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val dark = when (settings.theme) { "DARK" -> true; "LIGHT" -> false; else -> androidx.compose.foundation.isSystemInDarkTheme() }
    val accent = Color(settings.accentColor)
    val colors = if (dark) darkColorScheme(primary = accent, secondary = accent, background = Color(0xFF101116), surface = Color(0xFF191A22))
    else lightColorScheme(primary = accent, secondary = accent, background = Color(0xFFF6F6FA), surface = Color.White)

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar {
                    Section.entries.forEach { item ->
                        val selected = section == item.name
                        NavigationBarItem(selected = selected, onClick = { section = item.name },
                            icon = { Icon(sectionIcon(item), contentDescription = null) }, label = { Text(item.label, maxLines = 1) })
                    }
                }
            },
            floatingActionButton = {
                if (section == Section.HABITS.name) ExtendedFloatingActionButton(
                    onClick = { editing = null; showNewHabit = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Новая привычка") }
                )
            }
        ) { padding ->
            when (Section.valueOf(section)) {
                Section.HABITS -> HabitsScreen(habits, records, selectedDate, vm, now, Modifier.padding(padding), onEdit = { editing = it; showNewHabit = true }, onCreate = { editing = null; showNewHabit = true }, onSaveTemplate = { vm.saveAsTemplate(it); dialogMessage = "Привычка сохранена в «Мои шаблоны»." })
                Section.TODAY -> TodayScreen(records, selectedDate, today, vm, now, Modifier.padding(padding))
                Section.TEMPLATES -> TemplatesScreen(today, userTemplates, vm, Modifier.padding(padding), askForNotificationPermission)
                Section.PROGRESS -> ProgressScreen(habits, records, selectedDate, today, vm, Modifier.padding(padding))
                Section.SETTINGS -> SettingsScreen(settings, vm, Modifier.padding(padding),
                    onExport = { exportLauncher.launch("treker-backup-${LocalDate.now()}.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                    onNotificationsChanged = { enabled ->
                        vm.setReminders(enabled)
                        if (enabled && android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    })
            }
        }
        if (showNewHabit) HabitEditorDialog(editing, LocalDate.now(), onDismiss = { showNewHabit = false }, onSave = { vm.saveHabit(it); showNewHabit = false }, onReminderPermission = askForNotificationPermission)
        pendingImport?.let { json ->
            AlertDialog(onDismissRequest = { pendingImport = null }, title = { Text("Заменить текущие данные?") },
                text = { Text("Импорт заменит привычки, историю, пользовательские шаблоны и включённые в копию настройки. Сначала убедитесь, что у вас есть актуальная резервная копия.") },
                confirmButton = { TextButton(onClick = {
                    scope.launch { runCatching { vm.importJson(json) }.onSuccess { dialogMessage = "Данные восстановлены из резервной копии." }.onFailure { dialogMessage = "Файл повреждён или несовместим: ${it.localizedMessage}" } }
                    pendingImport = null
                }) { Text("Заменить данные") } }, dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Отмена") } })
        }
        dialogMessage?.let { message -> AlertDialog(onDismissRequest = { dialogMessage = null }, confirmButton = { TextButton(onClick = { dialogMessage = null }) { Text("Понятно") } }, text = { Text(message) }) }
    }
}

@Composable
private fun HabitsScreen(habits: List<HabitEntity>, records: List<DailyRecordEntity>, today: LocalDate, vm: HabitViewModel, now: Long, modifier: Modifier, onEdit: (HabitEntity) -> Unit, onCreate: () -> Unit, onSaveTemplate: (HabitEntity) -> Unit) {
    val byHabit = records.associateBy { it.habitId }
    val planned = records.size
    val done = records.count { it.status == Tracking.DONE }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("ТВОЙ РИТМ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(greeting(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru"))).replaceFirstChar { it.titlecase(Locale("ru")) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { DailySummary(done, planned, if (today == LocalDate.now()) "Сегодня" else today.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))) }
        if (habits.none { !it.archived }) item { EmptyCard("Начни с одной полезной привычки", "Создай привычку или выбери готовый шаблон — дневной план появится здесь.", "Создать привычку", onClick = onCreate) }
        items(habits.filterNot { it.archived }, key = { it.id }) { habit ->
            val record = byHabit[habit.id]
            if (record != null) HabitCard(habit, record, vm, now, onEdit = { onEdit(habit) }, onArchive = { vm.archive(habit) }, onPause = { vm.togglePause(habit) }, onSaveTemplate = { onSaveTemplate(habit) })
        }
    }
}

@Composable
private fun DailySummary(done: Int, planned: Int, heading: String = "Сегодня") {
    val fraction = if (planned == 0) 0f else done.toFloat() / planned
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (planned == 0) "План пока пуст" else "$done из $planned выполнено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${(fraction * 100).roundToInt()}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), trackColor = MaterialTheme.colorScheme.surface.copy(alpha = .6f))
        }
    }
}

@Composable
private fun HabitCard(habit: HabitEntity, record: DailyRecordEntity, vm: HabitViewModel, now: Long, onEdit: () -> Unit, onArchive: () -> Unit, onPause: () -> Unit, onSaveTemplate: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var amountDialog by remember { mutableStateOf<String?>(null) }
    var amountText by remember { mutableStateOf("") }
    val progress = when {
        record.typeSnapshot == Tracking.SIMPLE -> if (record.status == Tracking.DONE) 1f else 0f
        record.targetSnapshot <= 0 -> 0f
        else -> (record.progress / record.targetSnapshot).toFloat().coerceIn(0f, 1f)
    }
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(habit.color).copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                    Icon(iconFor(habit.icon), null, tint = Color(habit.color))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(record.titleSnapshot, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textDecoration = if (record.status == Tracking.DONE) TextDecoration.LineThrough else null, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${habit.category} · ${trackingLabel(record.typeSnapshot)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Действия") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Изменить") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text(if (habit.paused) "Продолжить" else "Приостановить") }, leadingIcon = { Icon(Icons.Outlined.Pause, null) }, onClick = { menu = false; onPause() })
                        DropdownMenuItem(text = { Text("Сохранить как шаблон") }, leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, null) }, onClick = { menu = false; onSaveTemplate() })
                        DropdownMenuItem(text = { Text("В архив") }, leadingIcon = { Icon(Icons.Outlined.Archive, null) }, onClick = { menu = false; onArchive() })
                    }
                }
            }
            if (habit.description.isNotBlank()) Text(habit.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (record.typeSnapshot) {
                Tracking.SIMPLE -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (record.status == Tracking.DONE) "Готово на сегодня" else "Отметь выполнение", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = { vm.progress(record, 1.0) }, enabled = !habit.paused) { Icon(if (record.status == Tracking.DONE) Icons.AutoMirrored.Outlined.Undo else Icons.Outlined.Check, null); Spacer(Modifier.width(6.dp)); Text(if (record.status == Tracking.DONE) "Отменить" else "Сделано") }
                }
                Tracking.COUNTER -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${formatNumber(record.progress)} / ${formatNumber(record.targetSnapshot)} ${record.unitSnapshot}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        OutlinedButton(onClick = { vm.progress(record, -1.0) }, enabled = !habit.paused, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("−") }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(onClick = { amountText = ""; amountDialog = "counter" }, enabled = !habit.paused, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Другое") }
                        Spacer(Modifier.width(6.dp))
                        FilledTonalButton(onClick = { vm.progress(record, 1.0) }, enabled = !habit.paused, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("+1") }
                    }
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                }
                Tracking.TIMER -> {
                    val remaining = if (record.timerRunning) ((record.timerDeadlineMillis ?: now) - now).coerceAtLeast(0) else record.timerRemainingMillis.takeIf { it > 0 } ?: (record.targetSnapshot * 60_000).toLong()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (record.status == Tracking.DONE) "Сессия завершена" else formatDuration(remaining), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { vm.resetTimer(record) }) { Icon(Icons.Outlined.Replay, "Сбросить таймер") }
                        FilledTonalButton(onClick = { if (record.timerRunning) vm.pauseTimer(record) else vm.startTimer(record) }, enabled = record.status != Tracking.DONE && !habit.paused) {
                            Icon(if (record.timerRunning) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null)
                            Spacer(Modifier.width(4.dp)); Text(if (record.timerRunning) "Пауза" else "Старт")
                        }
                    }
                }
                Tracking.ACCUMULATE -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${formatNumber(record.progress)} / ${formatNumber(record.targetSnapshot)} мин", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        OutlinedButton(onClick = { amountText = ""; amountDialog = "session" }, enabled = record.status != Tracking.DONE && !habit.paused) { Text("Другое") }
                        FilledTonalButton(onClick = { vm.addSession(record, 15) }, enabled = record.status != Tracking.DONE && !habit.paused) { Icon(Icons.Outlined.Add, null); Text("15 мин") }
                    }
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                }
            }
            if (record.status == Tracking.DONE) StatusPill("Выполнено", Color(0xFF23855B))
            else if (habit.paused) StatusPill("На паузе", MaterialTheme.colorScheme.tertiary)
        }
    }
    amountDialog?.let { mode ->
        AlertDialog(onDismissRequest = { amountDialog = null }, title = { Text(if (mode == "counter") "Добавить количество" else "Добавить сессию") },
            text = { OutlinedTextField(amountText, { amountText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } }, label = { Text(if (mode == "counter") "Сколько ${record.unitSnapshot}?" else "Минут") }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                val value = amountText.replace(',', '.').toDoubleOrNull()
                if (value != null && value > 0) {
                    if (mode == "counter") vm.progress(record, value) else vm.addSession(record, value.toInt().coerceAtLeast(1))
                    amountDialog = null
                }
            }) { Text("Добавить") } }, dismissButton = { TextButton(onClick = { amountDialog = null }) { Text("Отмена") } })
    }
}

@Composable
private fun TodayScreen(records: List<DailyRecordEntity>, date: LocalDate, today: LocalDate, vm: HabitViewModel, now: Long, modifier: Modifier) {
    var filter by rememberSaveable { mutableStateOf("Все") }
    val filters = listOf("Все", "В планах", "Частично", "Готово", "Пропущено", "Не выполнено")
    val done = records.count { it.status == Tracking.DONE }
    val visible = records.filter { record -> when (filter) {
        "В планах" -> record.status == Tracking.PENDING && record.progress == 0.0
        "Частично" -> record.status == Tracking.PENDING && record.progress > 0.0
        "Готово" -> record.status == Tracking.DONE
        "Пропущено" -> record.status == Tracking.SKIPPED
        "Не выполнено" -> record.status == Tracking.FAILED
        else -> true
    } }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("ДНЕВНОЙ ПЛАН", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text("Сегодня", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { vm.selectDate(date.minusDays(1)) }) { Icon(Icons.Outlined.ChevronLeft, "Предыдущий день") }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru")).withLocale(Locale("ru"))).replaceFirstChar { it.titlecase(Locale("ru")) }, fontWeight = FontWeight.SemiBold)
                        Text(if (date == today) "Сегодня" else if (date.isAfter(today)) "Будущий день" else "История", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { if (date < today) vm.selectDate(date.plusDays(1)) }, enabled = date < today) { Icon(Icons.Outlined.ChevronRight, "Следующий день") }
                }
            }
        }
        item { DailySummary(done, records.size, if (date == today) "Сегодня" else date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { filters.forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) }) } } }
        if (visible.isEmpty()) item { EmptyCard(if (records.isEmpty()) "На этот день пока ничего нет" else "Ничего не найдено", if (records.isEmpty()) "Добавь привычки и выбери расписание — задачи появятся автоматически." else "Попробуй другой фильтр.", null, null) }
        items(visible, key = { it.id }) { record ->
            val habit = vm.habits.collectAsStateWithLifecycle().value.firstOrNull { it.id == record.habitId }
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (record.status == Tracking.DONE) Icons.Outlined.CheckCircle else if (record.status == Tracking.FAILED) Icons.Outlined.Cancel else if (record.status == Tracking.SKIPPED) Icons.Outlined.SkipNext else Icons.Outlined.RadioButtonUnchecked, null, tint = if (record.status == Tracking.DONE) Color(0xFF23855B) else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(record.titleSnapshot, fontWeight = FontWeight.SemiBold, textDecoration = if (record.status == Tracking.DONE) TextDecoration.LineThrough else null)
                            Text(recordProgressSummary(record), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (record.typeSnapshot == Tracking.SIMPLE && record.status != Tracking.DONE) FilledTonalButton(onClick = { vm.progress(record, 1.0) }, enabled = habit?.paused != true) { Text("Готово") }
                    }
                    if (record.typeSnapshot == Tracking.COUNTER && habit?.paused != true) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.progress(record, -1.0) }) { Text("−") }
                        FilledTonalButton(onClick = { vm.progress(record, 1.0) }) { Text("+1 ${record.unitSnapshot}") }
                    }
                    if (record.typeSnapshot == Tracking.TIMER && habit?.paused != true) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDuration(if (record.timerRunning) ((record.timerDeadlineMillis ?: now) - now).coerceAtLeast(0) else record.timerRemainingMillis.takeIf { it > 0 } ?: (record.targetSnapshot * 60_000).toLong()), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        TextButton(onClick = { if (record.timerRunning) vm.pauseTimer(record) else vm.startTimer(record) }) { Text(if (record.timerRunning) "Пауза" else "Старт") }
                    }
                    if (record.status != Tracking.DONE) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (record.status == Tracking.SKIPPED || record.status == Tracking.FAILED) TextButton(onClick = { vm.status(record, Tracking.PENDING) }) { Text("Вернуть в план") }
                        TextButton(onClick = { vm.status(record, Tracking.SKIPPED) }) { Text("Пропустить") }
                        TextButton(onClick = { vm.status(record, Tracking.FAILED) }) { Text("Не выполнено") }
                    } else TextButton(onClick = {
                        when (record.typeSnapshot) {
                            Tracking.TIMER -> vm.resetTimer(record)
                            Tracking.SIMPLE -> vm.progress(record, 1.0)
                            else -> vm.progress(record, -record.progress)
                        }
                    }) { Text("Отменить выполнение") }
                }
            }
        }
    }
}

private data class Template(val title: String, val description: String, val category: String, val type: String, val target: Double, val unit: String)
private data class HabitOutcome(val id: Long, val name: String, val done: Int, val total: Int)
private val templates = listOf(
    Template("Утренняя зарядка", "Немного движения для хорошего старта.", "Спорт", Tracking.SIMPLE, 1.0, "раз"),
    Template("Отжимания", "Поддерживай силовую форму.", "Спорт", Tracking.COUNTER, 30.0, "повторений"),
    Template("Стаканы воды", "Не забывай пить в течение дня.", "Здоровье", Tracking.COUNTER, 6.0, "стаканов"),
    Template("Чтение", "Выдели время на книгу.", "Обучение", Tracking.TIMER, 20.0, "минут"),
    Template("Изучение языка", "Набирай практику короткими сессиями.", "Обучение", Tracking.ACCUMULATE, 45.0, "минут"),
    Template("Медитация", "Несколько минут тишины и внимания.", "Отдых", Tracking.TIMER, 10.0, "минут"),
    Template("Планирование дня", "Выбери главное на сегодня.", "Продуктивность", Tracking.SIMPLE, 1.0, "раз"),
    Template("Прогулка", "Небольшая прогулка на свежем воздухе.", "Здоровье", Tracking.COUNTER, 2.0, "км")
)

@Composable
private fun TemplatesScreen(date: LocalDate, userTemplates: List<UserTemplateEntity>, vm: HabitViewModel, modifier: Modifier, onReminderPermission: () -> Unit) {
    var chosen by remember { mutableStateOf<TemplateInput?>(null) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("БИБЛИОТЕКА", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text("Готовые идеи", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Настрой шаблон перед добавлением — его можно изменить позже.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (userTemplates.isNotEmpty()) {
            item { Text("Мои шаблоны", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            items(userTemplates, key = { "custom-${it.id}" }) { template ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(iconFor(template.icon), null, tint = Color(template.color), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(template.title, fontWeight = FontWeight.SemiBold)
                            Text("${template.description}\n${trackingLabel(template.trackingType)} · ${formatNumber(template.target)} ${template.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { chosen = TemplateInput(template.title, template.description, template.category, template.trackingType, template.target, template.unit, template.color, "WEEKLY", template.scheduleMask) }) { Icon(Icons.Outlined.AddCircle, "Создать по шаблону") }
                        IconButton(onClick = { vm.deleteTemplate(template.id) }) { Icon(Icons.Outlined.DeleteOutline, "Удалить шаблон") }
                    }
                }
            }
        }
        item { Text("Готовые шаблоны", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        items(templates, key = { "builtin-${it.title}" }) { template ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(iconFor(template.category), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(template.title, fontWeight = FontWeight.SemiBold); Text("${template.description}\n${trackingLabel(template.type)} · ${formatNumber(template.target)} ${template.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { chosen = TemplateInput(template.title, template.description, template.category, template.type, template.target, template.unit) }) { Icon(Icons.Outlined.AddCircle, "Добавить шаблон") }
                }
            }
        }
        item { Text("Сохраняй собственные шаблоны из меню привычки на главном экране.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    chosen?.let { template ->
        HabitEditorDialog(null, date, initial = template, onDismiss = { chosen = null }, onSave = { vm.saveHabit(it); chosen = null }, onReminderPermission = onReminderPermission)
    }
}

@Composable
private fun ProgressScreen(habits: List<HabitEntity>, records: List<DailyRecordEntity>, selected: LocalDate, today: LocalDate, vm: HabitViewModel, modifier: Modifier) {
    var detailHabitId by rememberSaveable { mutableStateOf<Long?>(null) }
    val dateMap = records.groupBy { runCatching { LocalDate.parse(it.date) }.getOrNull() }.filterKeys { it != null }.mapKeys { it.key!! }
    val currentWeek = records.filter { LocalDate.parse(it.date) >= today.minusDays(6) && LocalDate.parse(it.date) <= today }
    val previousWeek = records.filter { LocalDate.parse(it.date) >= today.minusDays(13) && LocalDate.parse(it.date) < today.minusDays(6) }
    val completed = records.count { it.status == Tracking.DONE }
    val active = habits.count { !it.archived }
    val successfulDays = dateMap.mapValues { (_, day) -> day.size to day.count { it.status == Tracking.DONE } }
    val currentStreak = Tracking.currentStreak(successfulDays, today)
    val longest = Tracking.longestStreak(successfulDays)
    val from = today.minusDays(364)
    val days = (0..364).map { from.plusDays(it.toLong()) }
    val weekNow = rate(currentWeek)
    val weekBefore = rate(previousWeek)
    val dayRate = rate(dateMap[today].orEmpty())
    val monthRate = rate(records.filter { LocalDate.parse(it.date).year == today.year && LocalDate.parse(it.date).month == today.month })
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("ТВОЯ ДИНАМИКА", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text("Прогресс", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Активность считается по доле выполненных задач за день.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Выполнено", completed.toString(), Modifier.weight(1f))
                StatCard("Серия сейчас", "$currentStreak дн.", Modifier.weight(1f))
                StatCard("Рекорд", "$longest дн.", Modifier.weight(1f))
            }
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Активность · последние 12 месяцев", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        days.chunked(7).forEach { week ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                week.forEach { date ->
                                    val dayRecords = dateMap[date].orEmpty()
                                    val level = if (date > today) 0 else Tracking.heatLevel(dayRecords.size, dayRecords.count { it.status == Tracking.DONE })
                                    val color = when (level) { 0 -> MaterialTheme.colorScheme.surfaceVariant; 1 -> Color(0xFFB5DFC3); 2 -> Color(0xFF68BE8B); 3 -> Color(0xFF27965B); else -> Color(0xFF17683D) }
                                    Surface(onClick = { if (date <= today) vm.selectDate(date) }, shape = RoundedCornerShape(3.dp), color = color, modifier = Modifier.size(11.dp)) {}
                                }
                            }
                        }
                    }
                    Text("□ нет данных   ▪ низкая   ▪ средняя   ▪ высокая   ▪ все выполнены", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatCard("Сегодня", "${dayRate}%", Modifier.weight(1f)); StatCard("За неделю", "${weekNow}%", Modifier.weight(1f)); StatCard("За месяц", "${monthRate}%", Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatCard("Прошлая неделя", "${weekBefore}%", Modifier.weight(1f)); StatCard("Активные", active.toString(), Modifier.weight(1f)) } }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Регулярность привычек", fontWeight = FontWeight.SemiBold)
                    val outcomes = records.groupBy { it.habitId }.map { (id, items) ->
                        HabitOutcome(id, habits.firstOrNull { it.id == id }?.title ?: items.first().titleSnapshot, items.count { it.status == Tracking.DONE }, items.size)
                    }
                    val best = outcomes.sortedByDescending { it.done.toDouble() / it.total.coerceAtLeast(1) }.take(5)
                    val mostMissed = outcomes.sortedBy { it.done.toDouble() / it.total.coerceAtLeast(1) }.take(3)
                    if (outcomes.isEmpty()) Text("История появится после выполнения привычек.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    best.forEach { outcome ->
                        TextButton(onClick = { detailHabitId = outcome.id }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text(outcome.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${outcome.done} из ${outcome.total}")
                        }
                    }
                    if (mostMissed.isNotEmpty()) {
                        Text("Чаще остаются незавершёнными", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        mostMissed.forEach { outcome -> Text("${outcome.name} · ${outcome.total - outcome.done} незавершено", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    val detail = outcomes.firstOrNull { it.id == detailHabitId }
                    if (detail != null) {
                        Text("История: ${detail.name}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        records.filter { it.habitId == detail.id }.sortedByDescending { it.date }.take(14).forEach { record ->
                            val dateLabel = runCatching { LocalDate.parse(record.date).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("ru"))) }.getOrDefault(record.date)
                            Text("$dateLabel · ${recordStatusLabel(record.status)} · ${recordProgressSummary(record)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("День", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); IconButton(onClick = { vm.selectDate(selected.minusDays(1)) }) { Icon(Icons.Outlined.ChevronLeft, null) }; Text(selected.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru")))); IconButton(onClick = { if (selected < today) vm.selectDate(selected.plusDays(1)) }) { Icon(Icons.Outlined.ChevronRight, null) } }
                    val selectedRecords = dateMap[selected].orEmpty()
                    if (selectedRecords.isEmpty()) Text("На эту дату записей нет.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else selectedRecords.forEach { record -> Text("${if (record.status == Tracking.DONE) "✓" else "·"} ${record.titleSnapshot} — ${recordProgressSummary(record)}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        item { Text("Серия учитывает только дни с запланированными задачами; дни без записи не считаются автоматически успешными.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SettingsScreen(settings: UserSettings, vm: HabitViewModel, modifier: Modifier, onExport: () -> Unit, onImport: () -> Unit, onNotificationsChanged: (Boolean) -> Unit) {
    var confirmAction by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("ПЕРСОНАЛИЗАЦИЯ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Text("Настройки", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            SettingsGroup("Оформление") {
                Text("Тема приложения", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("SYSTEM" to "Системная", "LIGHT" to "Светлая", "DARK" to "Тёмная").forEach { (key, label) -> FilterChip(settings.theme == key, onClick = { vm.setTheme(key) }, label = { Text(label) }) } }
                Text("Акцентный цвет", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(0xFF5556B8, 0xFF00897B, 0xFFE16C5B, 0xFF8E63BA, 0xFFE5A33D, 0xFF3979B9).forEach { swatch ->
                        Surface(onClick = { vm.setAccentColor(swatch) }, shape = CircleShape, color = Color(swatch), modifier = Modifier.size(32.dp)) {
                            if (settings.accentColor == swatch) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.padding(6.dp))
                        }
                    }
                }
            }
        }
        item { SettingsGroup("Напоминания и звук") {
            SettingSwitch("Напоминания", "Разрешение Android может потребоваться отдельно.", settings.remindersEnabled, onNotificationsChanged)
            SettingSwitch("Звук уведомлений", "Системная громкость и беззвучный режим имеют приоритет.", settings.soundEnabled) { vm.setSound(it) }
            Text("Напоминания задаются в настройках привычки. Доставка может зависеть от режима энергосбережения и ограничений One UI.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { SettingsGroup("Данные на устройстве") {
            Text("Данные хранятся локально и не отправляются на сервер.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.FileUpload, null); Spacer(Modifier.width(8.dp)); Text("Экспортировать резервную копию") }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Импортировать JSON") }
            TextButton(onClick = { confirmAction = "history" }) { Text("Очистить историю") }
            TextButton(onClick = { confirmAction = "reset" }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Сбросить приложение") }
        } }
        item { Text("Treker · версия 1.0.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    confirmAction?.let { action ->
        val reset = action == "reset"
        AlertDialog(onDismissRequest = { confirmAction = null }, title = { Text(if (reset) "Удалить все данные?" else "Очистить историю?") }, text = { Text(if (reset) "Привычки, записи и статистика будут удалены без возможности восстановления. Экспортируй копию заранее." else "Все дневные результаты и история будут удалены. Сами привычки сохранятся.") }, confirmButton = { TextButton(onClick = { if (reset) vm.resetAll() else vm.clearHistory(); confirmAction = null }) { Text(if (reset) "Удалить всё" else "Очистить") } }, dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("Отмена") } })
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); content() }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked, onCheckedChange = onChange) }
}

@Composable
private fun EmptyCard(title: String, subtitle: String, button: String?, onClick: (() -> Unit)?) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (button != null && onClick != null) FilledTonalButton(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = .12f)) { Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = color) }
}

private fun sectionIcon(section: Section) = when (section) {
    Section.HABITS -> Icons.Outlined.CheckCircle
    Section.TODAY -> Icons.Outlined.Today
    Section.TEMPLATES -> Icons.Outlined.AutoAwesome
    Section.PROGRESS -> Icons.Outlined.Insights
    Section.SETTINGS -> Icons.Outlined.Settings
}
private fun iconFor(name: String) = when (name.lowercase()) {
    "спорт", "fitness", "exercise" -> Icons.Outlined.FitnessCenter
    "обучение", "book", "reading" -> Icons.AutoMirrored.Outlined.MenuBook
    "здоровье", "water", "health" -> Icons.Outlined.FavoriteBorder
    "отдых", "meditation" -> Icons.Outlined.SelfImprovement
    "продуктивность", "work" -> Icons.Outlined.Bolt
    else -> Icons.Outlined.CheckCircle
}
private fun trackingLabel(type: String) = when (type) { Tracking.SIMPLE -> "Выполнение"; Tracking.COUNTER -> "Счётчик"; Tracking.TIMER -> "Таймер"; Tracking.ACCUMULATE -> "Накопление времени"; else -> type }
private fun greeting() = when (java.time.LocalTime.now().hour) { in 5..11 -> "Доброе утро"; in 12..17 -> "Хороший день"; else -> "Хороший вечер" }
private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.getDefault(), "%.1f", value)
private fun formatDuration(millis: Long): String { val seconds = (millis.coerceAtLeast(0) / 1000); return "%02d:%02d".format(seconds / 60, seconds % 60) }
private fun recordStatusLabel(status: String) = when (status) { Tracking.DONE -> "Выполнено"; Tracking.SKIPPED -> "Пропущено"; Tracking.FAILED -> "Не выполнено"; else -> "В плане" }
private fun recordProgressSummary(record: DailyRecordEntity): String = when (record.typeSnapshot) {
    Tracking.SIMPLE -> if (record.status == Tracking.DONE) "Выполнено" else "Простое действие"
    Tracking.COUNTER -> "${formatNumber(record.progress)} / ${formatNumber(record.targetSnapshot)} ${record.unitSnapshot}"
    Tracking.TIMER -> if (record.status == Tracking.DONE) "${record.actualDurationMillis / 60_000} мин выполнено" else "Цель ${formatNumber(record.targetSnapshot)} мин"
    Tracking.ACCUMULATE -> "${formatNumber(record.progress)} / ${formatNumber(record.targetSnapshot)} мин"
    else -> ""
}
private fun rate(records: List<DailyRecordEntity>): Int = if (records.isEmpty()) 0 else (100.0 * records.count { it.status == Tracking.DONE } / records.size).roundToInt()
