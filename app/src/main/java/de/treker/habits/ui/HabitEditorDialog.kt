package de.treker.habits.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.treker.habits.data.HabitEntity
import de.treker.habits.domain.Tracking
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Composable
fun HabitEditorDialog(
    existing: HabitEntity?,
    date: LocalDate,
    initial: Any? = null,
    onDismiss: () -> Unit,
    onSave: (HabitEntity) -> Unit,
    onReminderPermission: () -> Unit = {}
) {
    val preset = initial as? TemplateInput
    var title by remember(existing, initial) { mutableStateOf(existing?.title ?: preset?.title.orEmpty()) }
    var description by remember(existing, initial) { mutableStateOf(existing?.description ?: preset?.description.orEmpty()) }
    var category by remember(existing, initial) { mutableStateOf(existing?.category ?: preset?.category ?: "Здоровье") }
    var type by remember(existing, initial) { mutableStateOf(existing?.trackingType ?: preset?.type ?: Tracking.SIMPLE) }
    var target by remember(existing, initial) { mutableStateOf((existing?.target ?: preset?.target ?: 1.0).toString()) }
    var unit by remember(existing, initial) { mutableStateOf(existing?.unit ?: preset?.unit ?: "раз") }
    var scheduleMode by remember(existing, initial) { mutableStateOf(existing?.scheduleMode ?: preset?.scheduleMode ?: "WEEKLY") }
    var onceDate by remember(existing) { mutableStateOf(existing?.onceDate ?: date.toString()) }
    var selectedDays by remember(existing, initial) { mutableStateOf(existing?.scheduleMask ?: preset?.scheduleMask ?: 127) }
    var reminderEnabled by remember(existing) { mutableStateOf(existing?.reminderEnabled ?: false) }
    var reminderTime by remember(existing) { mutableStateOf(existing?.reminderMinute?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "09:00") }
    var selectedColor by remember(existing, initial) { mutableLongStateOf(existing?.color ?: preset?.color ?: 0xFF5C6BC0) }
    var typeMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val categories = listOf("Спорт", "Здоровье", "Обучение", "Продуктивность", "Саморазвитие", "Отдых", "Личное")
    val types = listOf(Tracking.SIMPLE to "Простое действие", Tracking.COUNTER to "Счётчик количества", Tracking.TIMER to "Таймер", Tracking.ACCUMULATE to "Накопление времени")
    val weekdays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    val colors = listOf(0xFF5C6BC0, 0xFF00897B, 0xFFE16C5B, 0xFF8E63BA, 0xFFE5A33D, 0xFF3979B9)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Новая привычка" else "Изменить привычку", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 590.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it; error = null }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = error != null)
                OutlinedTextField(description, { description = it }, label = { Text("Описание (необязательно)") }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3)
                OutlinedTextField(category, { category = it }, label = { Text("Категория") }, supportingText = { Text("Можно выбрать вариант или вписать свой") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { suggestion -> FilterChip(category == suggestion, onClick = { category = suggestion }, label = { Text(suggestion) }) }
                }
                Box {
                    OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Тип: ${types.first { it.first == type }.second}") }
                    DropdownMenu(typeMenu, { typeMenu = false }) { types.forEach { (key, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { type = key; typeMenu = false; if (key == Tracking.TIMER || key == Tracking.ACCUMULATE) unit = "мин" }) } }
                }
                if (type != Tracking.SIMPLE) {
                    OutlinedTextField(target, { target = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, label = { Text(if (type == Tracking.TIMER || type == Tracking.ACCUMULATE) "Цель, минут" else "Целевое количество") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (type == Tracking.COUNTER) OutlinedTextField(unit, { unit = it }, label = { Text("Единица измерения") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Text("Расписание", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(scheduleMode == "WEEKLY" && selectedDays == 127, { scheduleMode = "WEEKLY"; selectedDays = 127 }, label = { Text("Каждый день") })
                    FilterChip(scheduleMode == "WEEKLY" && selectedDays == 31, { scheduleMode = "WEEKLY"; selectedDays = 31 }, label = { Text("Будни") })
                    FilterChip(scheduleMode == "ONCE", { scheduleMode = "ONCE" }, label = { Text("Один раз") })
                }
                if (scheduleMode == "WEEKLY") {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { weekdays.forEachIndexed { index, day ->
                        val checked = selectedDays and (1 shl index) != 0
                        FilterChip(checked, { selectedDays = if (checked) selectedDays and (1 shl index).inv() else selectedDays or (1 shl index) }, label = { Text(day) })
                    } }
                } else {
                    OutlinedTextField(onceDate, { onceDate = it }, label = { Text("Дата (ГГГГ-ММ-ДД)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text("Ежедневное напоминание"); Switch(reminderEnabled, { reminderEnabled = it; if (it) onReminderPermission() }) }
                if (reminderEnabled) OutlinedTextField(reminderTime, { reminderTime = it }, label = { Text("Время (ЧЧ:ММ)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Акцентный цвет", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) { colors.forEach { color ->
                    Surface(onClick = { selectedColor = color }, shape = RoundedCornerShape(50), color = Color(color), modifier = Modifier.size(34.dp)) { if (selectedColor == color) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.padding(7.dp)) }
                } }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = {
            val parsedTarget = target.replace(',', '.').toDoubleOrNull()
            val parsedTime = runCatching {
                val (hour, minute) = reminderTime.split(":").map { it.toInt() }
                require(hour in 0..23 && minute in 0..59)
                hour * 60 + minute
            }.getOrNull()
            if (title.isBlank()) error = "Укажи название привычки."
            else if (category.isBlank()) error = "Укажи категорию или выбери одну из подсказок."
            else if (type != Tracking.SIMPLE && (parsedTarget == null || !parsedTarget.isFinite() || parsedTarget <= 0.0)) error = "Цель должна быть конечным числом больше нуля."
            else if (scheduleMode == "WEEKLY" && selectedDays == 0) error = "Выбери хотя бы один день недели."
            else if (reminderEnabled && parsedTime == null) error = "Время должно быть в формате ЧЧ:ММ."
            else if (scheduleMode == "ONCE" && runCatching { LocalDate.parse(onceDate) }.isFailure) error = "Дата должна быть в формате ГГГГ-ММ-ДД."
            else {
                val safeTarget = if (type == Tracking.SIMPLE) 1.0 else parsedTarget ?: 1.0
                onSave(HabitEntity(
                    id = existing?.id ?: 0, title = title.trim(), description = description.trim(), category = category,
                    icon = category.lowercase(), color = selectedColor, trackingType = type, target = safeTarget,
                    unit = if (type == Tracking.TIMER || type == Tracking.ACCUMULATE) "мин" else unit.trim().ifBlank { "раз" },
                    scheduleMode = scheduleMode, scheduleMask = if (scheduleMode == "ONCE") 127 else selectedDays,
                    onceDate = if (scheduleMode == "ONCE") onceDate else null, reminderMinute = if (reminderEnabled) parsedTime else null,
                    reminderEnabled = reminderEnabled, createdDate = existing?.createdDate ?: date.toString(),
                    paused = existing?.paused ?: false, archived = existing?.archived ?: false, archivedDate = existing?.archivedDate
                ))
            }
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Small UI-only value object used by the template catalogue. */
data class TemplateInput(
    val title: String,
    val description: String,
    val category: String,
    val type: String,
    val target: Double,
    val unit: String,
    val color: Long = 0xFF5C6BC0,
    val scheduleMode: String = "WEEKLY",
    val scheduleMask: Int = 127
)
