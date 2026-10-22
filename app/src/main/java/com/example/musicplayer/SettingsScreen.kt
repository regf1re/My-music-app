package com.example.musicplayer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Настройки с вкладками: Главное, Кастомизация, Библиотека, Данные, Прочее. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    vm: PlayerViewModel,
    uiScale: Float,
    onScaleChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AppSettings.init(context.applicationContext)

    val titles = listOf("Главное", "Кастомизация", "Библиотека", "Данные", "Прочее")
    var tab by remember { mutableIntStateOf(0) }
    var showHidden by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                    Text("Настройки", style = MaterialTheme.typography.titleLarge)
                }
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    titles.forEachIndexed { index, title ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(title, maxLines = 1) }
                        )
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    when (tab) {
                        0 -> MainTab(vm)
                        1 -> LookTab(vm, uiScale, onScaleChange)
                        2 -> LibraryTab(vm) { showHidden = true }
                        3 -> DataTab { showStats = true }
                        else -> OtherTab()
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showHidden) HiddenSongsDialog(vm) { showHidden = false }
    if (showStats) StatsDialog { showStats = false }
}

// ====================================================================
//  Главное
// ====================================================================

@Composable
private fun MainTab(vm: PlayerViewModel) {
    SectionHeader("Воспроизведение")
    SwitchRow(
        title = "Пауза при отключении наушников",
        subtitle = "Музыка остановится, если наушники отключили или пропал Bluetooth",
        checked = AppSettings.pauseOnUnplug,
        onChange = { AppSettings.changePauseOnUnplug(it) }
    )
    SwitchRow(
        title = "Пропуск тишины",
        subtitle = "Плеер сам перепрыгивает долгие паузы в начале и конце песен",
        checked = AppSettings.skipSilence,
        onChange = { AppSettings.changeSkipSilence(it) }
    )
    SwitchRow(
        title = "Не гасить экран на экране плеера",
        checked = AppSettings.keepScreenOn,
        onChange = { AppSettings.changeKeepScreenOn(it) }
    )

    SectionHeader("Плавный переход между песнями")
    SwitchRow(
        title = "Кроссфейд",
        checked = vm.crossfadeEnabled,
        onChange = { vm.changeCrossfadeEnabled(it) }
    )
    if (vm.crossfadeEnabled) {
        Text("Длительность: ${vm.crossfadeSeconds} с")
        Slider(
            value = vm.crossfadeSeconds.toFloat(),
            onValueChange = { vm.changeCrossfadeSeconds(it.roundToInt()) },
            valueRange = 1f..12f,
            steps = 10
        )
        SwitchRow(
            title = "Не смешивать песни одного альбома",
            checked = vm.crossfadeSkipAlbum,
            onChange = { vm.changeCrossfadeSkipAlbum(it) }
        )
        Text(
            "Конец песни плавно перекрывается началом следующей. " +
                    "Не действует в режиме «Повтор песни» и в конце списка.",
            style = MaterialTheme.typography.bodySmall
        )
    }

    SectionHeader("Таймер сна")
    SleepTimerSection()
}

@Composable
private fun SleepTimerSection() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            AppSettings.refreshSleep()
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val left = AppSettings.sleepUntil - now
    val status = when {
        AppSettings.sleepAfterSong -> "Музыка остановится после текущей песни"
        AppSettings.sleepUntil > 0L && left > 0L -> {
            val seconds = left / 1000
            "Музыка остановится через " + String.format("%d:%02d", seconds / 60, seconds % 60)
        }
        else -> "Таймер выключен"
    }
    Text(status)
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(15, 30, 45, 60).forEach { minutes ->
            OutlinedButton(onClick = { AppSettings.startSleepTimer(minutes) }) { Text("$minutes мин") }
        }
        OutlinedButton(onClick = { AppSettings.startSleepAfterSong() }) { Text("После песни") }
    }
    TextButton(onClick = { AppSettings.cancelSleep() }) { Text("Выключить таймер") }
    Text(
        "Последние 10 секунд перед остановкой громкость плавно затихает.",
        style = MaterialTheme.typography.bodySmall
    )
}

// ====================================================================
//  Кастомизация
// ====================================================================

private val themeNames = listOf("Как в системе", "Светлая", "Тёмная")

@Composable
private fun LookTab(vm: PlayerViewModel, uiScale: Float, onScaleChange: (Float) -> Unit) {
    var tempScale by remember(uiScale) { mutableFloatStateOf(uiScale) }

    SectionHeader("Тема")
    themeNames.forEachIndexed { index, name ->
        Row(
            Modifier.fillMaxWidth().clickable { AppSettings.changeThemeMode(index) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = AppSettings.themeMode == index,
                onClick = { AppSettings.changeThemeMode(index) }
            )
            Text(name)
        }
    }
    SwitchRow(
        title = "Динамические цвета",
        subtitle = if (Build.VERSION.SDK_INT >= 31) "Цвета подбираются под обои телефона (Android 12+)"
        else "Доступно на Android 12 и новее",
        checked = AppSettings.dynamicColors && Build.VERSION.SDK_INT >= 31,
        onChange = { AppSettings.changeDynamicColors(it) },
        enabled = Build.VERSION.SDK_INT >= 31
    )

    SectionHeader("Размер интерфейса: ${(tempScale * 100).roundToInt()}%")
    Slider(
        value = tempScale,
        onValueChange = { tempScale = it },
        onValueChangeFinished = { onScaleChange(tempScale) },
        valueRange = 0.7f..1.2f,
        steps = 9
    )
    TextButton(onClick = {
        tempScale = 1f
        onScaleChange(1f)
    }) { Text("Сбросить размер (100%)") }

    SectionHeader("Экран плеера")
    SwitchRow(
        title = "Визуализатор",
        subtitle = "Анимированные полоски под музыку",
        checked = vm.visualizerEnabled,
        onChange = { vm.changeVisualizer(it) }
    )
    SwitchRow(
        title = "Фон из обложки",
        subtitle = "Размытая обложка песни на фоне (если обложка есть)",
        checked = AppSettings.coverBackground,
        onChange = { AppSettings.changeCoverBackground(it) }
    )
    if (AppSettings.coverBackground) {
        Text("Затемнение фона: ${(AppSettings.coverDim * 100).roundToInt()}%")
        Slider(
            value = AppSettings.coverDim,
            onValueChange = { AppSettings.changeCoverDim(it) },
            valueRange = 0f..0.8f
        )
    }
    Text("Размер текста песни: ${(AppSettings.lyricsTextScale * 100).roundToInt()}%")
    Slider(
        value = AppSettings.lyricsTextScale,
        onValueChange = { AppSettings.changeLyricsTextScale(it) },
        valueRange = 0.8f..1.5f,
        steps = 13
    )
}

// ====================================================================
//  Библиотека
// ====================================================================

@Composable
private fun LibraryTab(vm: PlayerViewModel, onOpenHidden: () -> Unit) {
    SectionHeader("Скрытые песни")
    Text(
        "Скрытые песни не показываются в приложении и не играют.",
        style = MaterialTheme.typography.bodySmall
    )
    OutlinedButton(onClick = onOpenHidden) { Text("Открыть список (${vm.hiddenSongs.size})") }

    SectionHeader("Тексты песен (.lrc)")
    Text("Найдено файлов .lrc: ${vm.lrcCount}")
    if (vm.lrcCount == 0) {
        Text(
            "Если файлы есть, но их не видно, разрешите приложению доступ ко всем файлам " +
                    "(вкладка «Прочее»).",
            style = MaterialTheme.typography.bodySmall
        )
    }
    OutlinedButton(onClick = { vm.rescanLyrics() }) { Text("Пересканировать тексты") }

    SectionHeader("Музыка на телефоне")
    Text(
        "Если вы добавили или удалили песни другой программой, обновите список.",
        style = MaterialTheme.typography.bodySmall
    )
    OutlinedButton(onClick = { vm.loadSongs() }) { Text("Обновить список песен") }
}

// ====================================================================
//  Данные
// ====================================================================

@Composable
private fun DataTab(onOpenStats: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }

    SectionHeader("Статистика прослушивания")
    SwitchRow(
        title = "Вести статистику",
        subtitle = "Время прослушивания, любимые исполнители и песни, история",
        checked = AppSettings.statsEnabled,
        onChange = { AppSettings.changeStatsEnabled(it) }
    )
    Spacer(Modifier.height(8.dp))
    Button(onClick = onOpenStats) { Text("Открыть статистику") }
    TextButton(onClick = { confirmClear = true }) { Text("Очистить статистику") }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить статистику?") },
            text = { Text("Время прослушивания, любимые исполнители и история будут удалены.") },
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

// ====================================================================
//  Прочее
// ====================================================================

@Composable
private fun OtherTab() {
    val context = LocalContext.current
    val detailsIntent = {
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
    }

    SectionHeader("Музыка в фоне")
    Text(
        "Если на вашем телефоне музыка останавливается при выключенном экране, отключите для " +
                "приложения оптимизацию батареи: найдите «Музыка» в списке и выберите «Не оптимизировать» " +
                "(на некоторых телефонах — «Без ограничений» или «Разрешить работу в фоне»).",
        style = MaterialTheme.typography.bodySmall
    )
    OutlinedButton(onClick = {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: Exception) {
            context.startActivity(detailsIntent())
        }
    }) { Text("Открыть настройки батареи") }

    SectionHeader("Доступ ко всем файлам")
    Text(
        "Нужен для чтения и записи файлов .lrc, переименования и удаления песен. " +
                "Сейчас: " + if (LyricsLoader.hasAccess()) "разрешён" else "не разрешён",
        style = MaterialTheme.typography.bodySmall
    )
    OutlinedButton(onClick = {
        val intent = if (Build.VERSION.SDK_INT >= 30) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            detailsIntent()
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(detailsIntent())
        }
    }) { Text("Открыть настройки доступа") }

    SectionHeader("О приложении")
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
        } catch (e: Exception) {
            "—"
        }
    }
    Text("Музыка, версия $version")
    Text("Android ${Build.VERSION.RELEASE}", style = MaterialTheme.typography.bodySmall)
}