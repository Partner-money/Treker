package de.treker.habits.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.treker.habits.notifications.Sounds

/**
 * Reusable sound selector with preview and a system file picker.
 * Keeps only a read permission for the chosen audio file, so no broad storage access is requested.
 */
@Composable
fun SoundChooser(
    selected: String?,
    customUri: String?,
    allowInherit: Boolean,
    inheritLabel: String,
    onSelect: (String?) -> Unit,
    onPickedUri: (String) -> Unit,
    onPreview: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            onPickedUri(uri.toString())
            if (!persisted) onMessage("Файл выбран, но доступ может не сохраниться после перезапуска. Если звук пропадёт, выберите файл снова.")
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (allowInherit) FilterChip(selected == null, onClick = { onSelect(null) }, label = { Text(inheritLabel) })
            Sounds.options.forEach { option ->
                FilterChip(selected == option.id, onClick = { onSelect(option.id) }, label = { Text(option.label) })
            }
        }
        val current = Sounds.options.firstOrNull { it.id == Sounds.normalize(selected) }
        Text(
            if (selected == null) "Звук берётся из настроек приложения." else current?.hint.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (Sounds.normalize(selected) == Sounds.CUSTOM) {
            Text(
                if (customUri == null) "Файл не выбран — будет использован системный звук." else "Выбран пользовательский аудиофайл.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPreview) { Icon(Icons.Outlined.PlayArrow, null); Text("Прослушать", modifier = Modifier.padding(start = 4.dp)) }
            TextButton(onClick = { picker.launch(arrayOf("audio/*")) }) { Icon(Icons.Outlined.UploadFile, null); Text("Свой файл", modifier = Modifier.padding(start = 4.dp)) }
        }
    }
}
