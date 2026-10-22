package com.example.musicplayer

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.util.Locale

/** Строка текста песни в редакторе. timeMs = null — время ещё не отмечено. */
data class EditLine(val text: String, val timeMs: Long?)

fun formatLrcTime(ms: Long): String {
    val t = ms.coerceAtLeast(0L)
    return String.format(Locale.US, "%02d:%02d.%02d", t / 60000, (t % 60000) / 1000, (t % 1000) / 10)
}

private fun initialEditLines(state: LyricsState): List<EditLine> =
    when (state) {
        is LyricsState.Synced -> state.lines.map { EditLine(it.text, it.timeMs) }
        is LyricsState.Plain -> state.text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { EditLine(it, null) }
        else -> emptyList()
    }

private fun buildLrcText(song: Song, lines: List<EditLine>): String {
    val sb = StringBuilder()
    sb.append("[ti:").append(song.title).append("]\n")
    if (song.artist.isNotBlank()) sb.append("[ar:").append(song.artist).append("]\n")
    lines.mapNotNull { l -> l.timeMs?.let { it to l.text } }
        .sortedBy { it.first }
        .forEach { (time, text) ->
            sb.append('[').append(formatLrcTime(time)).append(']').append(text).append('\n')
        }
    return sb.toString()
}

/**
 * Редактор файла .lrc: слушаете песню и нажимаете кнопку в тот момент,
 * когда начинается очередная строка. Приложение запоминает время и сохраняет файл.
 */
@Composable
fun LrcEditorDialog(vm: PlayerViewModel, song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val initial = remember { initialEditLines(vm.lyrics) }
    val lines = remember { mutableStateListOf<EditLine>().apply { addAll(initial) } }
    var step by remember { mutableIntStateOf(if (initial.isEmpty()) 0 else 1) }
    var rawText by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }
    var dirty by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            position = vm.positionMs()
            delay(100)
        }
    }

    fun requestClose() {
        if (dirty) confirmExit = true else onDismiss()
    }

    fun stampSelected() {
        if (selected in lines.indices) {
            lines[selected] = lines[selected].copy(timeMs = vm.positionMs())
            dirty = true
            if (selected < lines.lastIndex) selected++
        }
    }

    fun nudge(deltaMs: Long) {
        if (selected in lines.indices) {
            val t = lines[selected].timeMs ?: return
            lines[selected] = lines[selected].copy(timeMs = (t + deltaMs).coerceAtLeast(0L))
            dirty = true
        }
    }

    Dialog(
        onDismissRequest = { requestClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { requestClose() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                    Text(
                        "Редактор .lrc",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        enabled = !saving && lines.any { it.timeMs != null },
                        onClick = {
                            val untimed = lines.count { it.timeMs == null }
                            saving = true
                            vm.saveLrc(song, buildLrcText(song, lines)) { ok, message ->
                                saving = false
                                if (ok) dirty = false
                                val text = if (ok && untimed > 0) {
                                    "$message Строк без времени пропущено: $untimed."
                                } else message
                                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                            }
                        }
                    ) { Text("Сохранить") }
                }
                Text(
                    "${song.title} — ${song.artist}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))

                if (step == 0) {
                    Text(
                        "Вставьте текст песни: каждая строка текста станет отдельной строкой файла. " +
                            "Потом вы расставите время, нажимая кнопку во время музыки.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        label = { Text("Текст песни") },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = rawText.isNotBlank(),
                        onClick = {
                            val parsed = rawText.lines()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .map { EditLine(it, null) }
                            lines.clear()
                            lines.addAll(parsed)
                            selected = 0
                            step = 1
                            dirty = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Далее: расставить время") }
                } else {
                    val listState = rememberLazyListState()
                    LaunchedEffect(selected) {
                        if (lines.isNotEmpty()) listState.animateScrollToItem((selected - 2).coerceAtLeast(0))
                    }

                    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                        itemsIndexed(lines) { index, line ->
                            val isSelected = index == selected
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        selected = index
                                        line.timeMs?.let { vm.seekTo(it) }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    line.timeMs?.let { formatLrcTime(it) } ?: "--:--.--",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = if (line.timeMs == null) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(line.text.ifBlank { "♪" }, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    val duration = vm.durationMs().coerceAtLeast(1L)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatLrcTime(position)} / ${formatLrcTime(duration)}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = position.coerceIn(0L, duration).toFloat(),
                        onValueChange = {
                            vm.seekTo(it.toLong())
                            position = it.toLong()
                        },
                        valueRange = 0f..duration.toFloat()
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { vm.seekTo((vm.positionMs() - 5000L).coerceAtLeast(0L)) }) {
                            Text("−5 с")
                        }
                        FilledIconButton(onClick = { vm.togglePlay() }) {
                            Icon(
                                if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Пауза / играть"
                            )
                        }
                        OutlinedButton(onClick = { vm.seekTo((vm.positionMs() + 5000L).coerceAtMost(duration)) }) {
                            Text("+5 с")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { stampSelected() },
                        enabled = lines.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("Отметить строку") }
                    Text(
                        "Нажимайте в момент, когда начинается выделенная строка.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(4.dp))
                    val compact = PaddingValues(horizontal = 4.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = { nudge(-100L) },
                            modifier = Modifier.weight(1f),
                            contentPadding = compact
                        ) { Text("−0,1 с") }
                        OutlinedButton(
                            onClick = { nudge(100L) },
                            modifier = Modifier.weight(1f),
                            contentPadding = compact
                        ) { Text("+0,1 с") }
                        OutlinedButton(
                            onClick = {
                                if (selected in lines.indices) {
                                    lines[selected] = lines[selected].copy(timeMs = null)
                                    dirty = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = compact
                        ) { Text("Без метки") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = { if (selected in lines.indices) editingIndex = selected },
                            modifier = Modifier.weight(1f),
                            contentPadding = compact
                        ) { Text("Текст") }
                        OutlinedButton(
                            onClick = {
                                val at = if (lines.isEmpty()) 0 else selected + 1
                                lines.add(at, EditLine("", null))
                                selected = at
                                dirty = true
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = compact
                        ) { Text("+ пауза ♪") }
                        OutlinedButton(
                            onClick = {
                                if (selected in lines.indices) {
                                    lines.removeAt(selected)
                                    selected = selected.coerceAtMost(lines.lastIndex).coerceAtLeast(0)
                                    dirty = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = compact
                        ) { Text("Удалить") }
                    }
                    TextButton(
                        onClick = {
                            rawText = lines.joinToString("\n") { it.text }
                            step = 0
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Править весь текст (метки времени сбросятся)") }
                }
            }
        }
    }

    editingIndex?.let { idx ->
        var text by remember(idx) { mutableStateOf(lines.getOrNull(idx)?.text.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingIndex = null },
            title = { Text("Текст строки") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Строка") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (idx in lines.indices) {
                        lines[idx] = lines[idx].copy(text = text.trim())
                        dirty = true
                    }
                    editingIndex = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editingIndex = null }) { Text("Отмена") } }
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Выйти без сохранения?") },
            text = { Text("Несохранённые изменения пропадут.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Выйти") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Остаться") } }
        )
    }
}
