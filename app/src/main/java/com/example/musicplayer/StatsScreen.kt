@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.musicplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val WEEKDAYS_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val WEEKDAYS_FULL = listOf(
    "понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье"
)
private val MONTHS_FULL = listOf(
    "январь", "февраль", "март", "апрель", "май", "июнь",
    "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"
)

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "$hours ч $minutes мин"
        totalMinutes > 0 -> "$totalMinutes мин"
        ms > 0 -> "меньше минуты"
        else -> "0 мин"
    }
}

private fun formatHistoryTime(ts: Long): String {
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    return when {
        sameDay(then, today) -> "Сегодня, $time"
        sameDay(then, yesterday) -> "Вчера, $time"
        else -> SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ts))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 3.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

/** Столбчатая диаграмма: значения, подписи под столбцами, самый высокий столбец выделен. */
@Composable
private fun BarChart(values: List<Long>, labels: List<String>, modifier: Modifier = Modifier) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val highlight = if ((values.maxOrNull() ?: 0L) > 0L) values.indexOf(values.max()) else -1
    val primary = MaterialTheme.colorScheme.primary

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            values.forEachIndexed { index, value ->
                val fraction = maxOf((value.toFloat() / max).coerceIn(0f, 1f), 0.02f)
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (index == highlight) primary else primary.copy(alpha = 0.4f))
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            labels.forEachIndexed { index, label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (index == highlight) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
        if (highlight >= 0) {
            Text(
                "Больше всего: ${labels[highlight]} — ${formatDuration(values[highlight])}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** Строка рейтинга с полоской, длина которой показывает долю от лидера. */
@Composable
private fun RankRow(item: RankItem, maxMs: Long, showSubtitle: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                if (showSubtitle && item.subtitle.isNotBlank()) {
                    Text(
                        item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                "${formatDuration(item.totalMs)} • ${item.plays}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { item.totalMs.toFloat() / maxMs.coerceAtLeast(1L) },
            modifier = Modifier.fillMaxWidth().height(4.dp)
        )
    }
}

/** Итоги года: главные цифры, любимые исполнитель, песня, альбом, день недели и лучший месяц. */
@Composable
private fun YearReview(years: List<Int>) {
    var year by remember { mutableIntStateOf(years.first()) }
    val summary = remember(year, StatsStore.version) { StatsStore.yearSummary(year) }

    if (years.size > 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            years.take(4).forEach { y ->
                FilterChip(selected = y == year, onClick = { year = y }, label = { Text("$y") })
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Итоги ${summary.year}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (summary.totalMs <= 0L) {
                Spacer(Modifier.height(8.dp))
                Text("За этот год данных пока нет.")
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    formatDuration(summary.totalMs),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("музыки вы послушали за год")
                Spacer(Modifier.height(12.dp))
                Text("Дней с музыкой: ${summary.activeDays}")
                Text("Песен: ${summary.songCount} • прослушиваний: ${summary.plays}")
                Spacer(Modifier.height(12.dp))
                summary.topArtist?.let {
                    Text("Любимый исполнитель: ${it.name} (${formatDuration(it.totalMs)})")
                }
                summary.topSong?.let {
                    Text("Любимая песня: ${it.name} (${formatDuration(it.totalMs)})")
                }
                summary.topAlbum?.let {
                    Text("Любимый альбом: ${it.name} (${formatDuration(it.totalMs)})")
                }
                if (summary.favWeekday >= 0) {
                    Text("Чаще всего вы слушаете в ${WEEKDAYS_FULL[summary.favWeekday]}")
                }
                if (summary.bestMonth > 0) {
                    Text(
                        "Лучший месяц: ${MONTHS_FULL[summary.bestMonth - 1]} " +
                                "(${formatDuration(summary.bestMonthMs)})"
                    )
                }
            }
        }
    }
}

/** Экран статистики: сколько слушали, итоги года, графики, любимые исполнители, альбомы, песни и история. */
@Composable
fun StatsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    StatsStore.init(context.applicationContext)

    val version = StatsStore.version
    val summary = remember(version) { StatsStore.summary() }
    val years = remember(version) { StatsStore.years() }
    val weekdays = remember(version) { StatsStore.weekdayTotals().toList() }
    val months = remember(version) { StatsStore.lastMonthsTotals(12) }
    val artists = remember(version) { StatsStore.topArtists(10) }
    val albums = remember(version) { StatsStore.topAlbums(10) }
    val songs = remember(version) { StatsStore.topSongs(10) }
    val history = remember(version) { StatsStore.recentHistory(100) }
    var confirmClear by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                    Text(
                        "Статистика",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { confirmClear = true }) { Text("Очистить") }
                }

                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatCard("Сегодня", formatDuration(summary.todayMs), Modifier.weight(1f))
                                StatCard("За 7 дней", formatDuration(summary.weekMs), Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatCard("Всего", formatDuration(summary.totalMs), Modifier.weight(1f))
                                StatCard(
                                    "Прослушиваний",
                                    "${summary.plays} (песен: ${summary.songCount})",
                                    Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    item { SectionTitle("Итоги года") }
                    item { YearReview(years) }

                    item { SectionTitle("Время по дням недели") }
                    item { BarChart(weekdays, WEEKDAYS_SHORT) }

                    item { SectionTitle("Время по месяцам") }
                    item { BarChart(months.map { it.second }, months.map { it.first }) }

                    item { SectionTitle("Любимые исполнители") }
                    if (artists.isEmpty()) {
                        item {
                            Text(
                                "Пока нет данных: послушайте музыку, и статистика появится здесь.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    val artistMax = artists.firstOrNull()?.totalMs ?: 1L
                    items(artists) { RankRow(it, artistMax, showSubtitle = false) }

                    item { SectionTitle("Любимые альбомы") }
                    if (albums.isEmpty()) {
                        item {
                            Text(
                                "Альбомы считаются по песням, которые вы слушали после этого обновления.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    val albumMax = albums.firstOrNull()?.totalMs ?: 1L
                    items(albums) { RankRow(it, albumMax, showSubtitle = false) }

                    item { SectionTitle("Любимые песни") }
                    if (songs.isEmpty()) {
                        item { Text("Пока нет данных.", style = MaterialTheme.typography.bodyMedium) }
                    }
                    val songMax = songs.firstOrNull()?.totalMs ?: 1L
                    items(songs) { RankRow(it, songMax, showSubtitle = true) }

                    item { SectionTitle("История") }
                    if (history.isEmpty()) {
                        item { Text("История пока пуста.", style = MaterialTheme.typography.bodyMedium) }
                    }
                    items(history) { entry ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    entry.title.ifBlank { "Без названия" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    entry.artist + " • " + formatHistoryTime(entry.time),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить статистику?") },
            text = { Text("Время прослушивания, итоги, любимые исполнители и история будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    StatsStore.clear()
                    confirmClear = false
                }) { Text("Очистить") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена") } }
        )
    }
}