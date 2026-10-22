@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
package com.example.musicplayer


import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import android.Manifest
import androidx.compose.ui.platform.LocalView
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.graphics.Bitmap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.FilterChip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsDraggedAsState

// ====================================================================
//  Плеер
// ====================================================================
@Composable
fun MiniPlayer(
    vm: PlayerViewModel,
    song: Song,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(song.id, vm.isPlaying) {
        position = vm.positionMs()
        duration = vm.durationMs()
        while (vm.isPlaying) {
            delay(300)
            position = vm.positionMs()
            duration = vm.durationMs()
        }
    }
    val progress = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    // Свайп по мини-плееру: влево — следующая песня, вправо — предыдущая
    val swipeScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().then(modifier)) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
            Row(
                Modifier
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            swipeScope.launch { offsetX.snapTo(offsetX.value + delta) }
                        },
                        onDragStopped = { velocity ->
                            val x = offsetX.value
                            if (x < -160f || velocity < -900f) {
                                vm.next()
                            } else if (x > 160f || velocity > 900f) {
                                vm.previous()
                            }
                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                        }
                    )
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .alpha(1f - (abs(offsetX.value) / 900f).coerceIn(0f, 0.6f))
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { vm.previous() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущая")
                }
                IconButton(onClick = { vm.togglePlay() }) {
                    Icon(
                        if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Пауза / играть"
                    )
                }
                IconButton(onClick = { vm.next() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Следующая")
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(vm: PlayerViewModel, song: Song, onClose: () -> Unit) {
    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showBgPicker by remember { mutableStateOf(false) }
    var showModeMenu by remember { mutableStateOf(false) }
    var showLrcEditor by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        while (true) {
            if (!dragging) position = vm.positionMs()
            delay(250)
        }
    }
    val duration = vm.durationMs().coerceAtLeast(1L)
    val shownPosition = position.coerceIn(0L, duration)
    val modeIcon = when (vm.playMode) {
        PlayMode.ORDER -> Icons.Default.ArrowForward
        PlayMode.REPEAT_ALL -> Icons.Default.Repeat
        PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
        PlayMode.SHUFFLE -> Icons.Default.Shuffle
    }
    val cover = rememberCoverImages(vm, song)
    val showCoverBg = cover != null && AppSettings.coverBackground

    // Не гасить экран на экране плеера (если включено в настройках)
    val view = LocalView.current
    DisposableEffect(AppSettings.keepScreenOn) {
        view.keepScreenOn = AppSettings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Двойное нажатие у левого или правого края: перемотка на 10 секунд назад или вперёд
    var seekFlash by remember { mutableIntStateOf(0) }
    var seekFlashKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(seekFlashKey) {
        if (seekFlash != 0) {
            delay(600)
            seekFlash = 0
        }
    }

    Surface(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { pos ->
                val width = size.width
                val step = when {
                    pos.x < width * 0.4f -> -10_000L
                    pos.x > width * 0.6f -> 10_000L
                    else -> 0L
                }
                if (step != 0L) {
                    vm.seekTo((vm.positionMs() + step).coerceIn(0L, vm.durationMs()))
                    seekFlash = if (step < 0L) -1 else 1
                    seekFlashKey++
                }
            })
        },
        contentColor = if (showCoverBg) Color.White else MaterialTheme.colorScheme.onSurface
    ) {
        Box(Modifier.fillMaxSize()) {
            // Размытая обложка на фоне (только если у песни есть обложка)
            if (cover != null && AppSettings.coverBackground) {
                Image(
                    bitmap = cover.blurred,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(24.dp) else Modifier)
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = AppSettings.coverDim)))
            }
            if (showLyrics) {
                BackgroundView(
                    bg = vm.effectiveBg(song.id),
                    alpha = vm.lyricsBgAlpha,
                    playing = vm.isPlaying,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Подсказка при двойном нажатии: «−10 с» или «+10 с»
            if (seekFlash != 0) {
                Box(
                    Modifier
                        .align(if (seekFlash < 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        if (seekFlash < 0) "−10 с" else "+10 с",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            // Анимированные полоски под музыку (за кнопками, внизу экрана)
            if (vm.visualizerEnabled) {
                SpectrumVisualizer(
                    playing = vm.isPlaying,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Свернуть")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showSpeed = true }) {
                        Text(formatSpeed(vm.speed))
                    }
                    TextButton(onClick = { showLyrics = !showLyrics }) {
                        Text(if (showLyrics) "Обложка" else "Текст")
                    }
                    IconButton(onClick = { vm.toggleFavorite(song.id) }) {
                        Icon(
                            if (vm.isFavorite(song.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Избранное",
                            tint = if (vm.isFavorite(song.id)) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showQueue = true }) {
                        Icon(Icons.Default.QueueMusic, contentDescription = "Очередь")
                    }
                }
                if (showLyrics) {
                    val shift = vm.lyricsOffsetFor(song.id)
                    val baseDensity = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            baseDensity.density,
                            baseDensity.fontScale * AppSettings.lyricsTextScale
                        )
                    ) {
                        LyricsPanel(
                            vm = vm,
                            song = song,
                            positionMs = shownPosition - shift,
                            onSeek = {
                                val target = (it + shift).coerceAtLeast(0L)
                                vm.seekTo(target)
                                position = target
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
                    if (vm.lyrics is LyricsState.Synced) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { vm.shiftLyrics(song.id, -500L) }) { Text("−0,5 с") }
                            TextButton(onClick = { vm.resetLyricsShift(song.id) }) {
                                Text(if (shift == 0L) "Сдвиг текста" else "Сдвиг: " + "%+.1f с".format(shift / 1000f))
                            }
                            TextButton(onClick = { vm.shiftLyrics(song.id, 500L) }) { Text("+0,5 с") }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cover != null) {
                            Image(
                                bitmap = cover.full,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(120.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(16.dp))
                Slider(
                    value = shownPosition.toFloat(),
                    onValueChange = {
                        dragging = true
                        position = it.toLong()
                    },
                    onValueChangeFinished = {
                        vm.seekTo(position)
                        dragging = false
                    },
                    valueRange = 0f..duration.toFloat()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt(shownPosition), style = MaterialTheme.typography.bodySmall)
                    Text(fmt(duration), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Короткое нажатие — следующий режим, долгое — меню с настройками режимов
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = { vm.cycleMode() },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showModeMenu = true
                                }
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(modeIcon, contentDescription = "Режим воспроизведения")
                        }
                        Text(
                            vm.playMode.label,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    IconButton(onClick = { vm.previous() }, modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Предыдущая",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    FilledIconButton(onClick = { vm.togglePlay() }, modifier = Modifier.size(72.dp)) {
                        Icon(
                            if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Пауза / играть",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    IconButton(onClick = { vm.next() }, modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Следующая",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    // Кнопка меню (три точки) теперь здесь, на той же высоте что и остальные кнопки
                    SongMenuButton(
                        vm = vm,
                        song = song,
                        modifier = Modifier.width(72.dp),
                        onBackgroundSettings = if (showLyrics) ({ showBgPicker = true }) else null,
                        onLyricsEditor = { showLrcEditor = true }
                    )
                }
                if (!showLyrics) {
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Громкость")
                        Slider(
                            value = vm.volume,
                            onValueChange = { vm.changeVolume(it) },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Text("${(vm.volume * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    if (showBgPicker) {
        BackgroundPickerDialog(vm, song) { showBgPicker = false }
    }
    if (showModeMenu) {
        PlayModeDialog(vm) { showModeMenu = false }
    }
    if (showLrcEditor) {
        LrcEditorDialog(vm, song) { showLrcEditor = false }
    }
    if (showSpeed) {
        SpeedDialog(vm) { showSpeed = false }
    }
    if (showQueue) {
        QueueDialog(vm) { showQueue = false }
    }
}

@Composable
fun QueueDialog(vm: PlayerViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Очередь (${vm.queue.size})") },
        text = {
            if (vm.queue.isEmpty()) {
                Text("Очередь пуста.")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(vm.queue) { index, song ->
                        val isCurrent = index == vm.queueIndex
                        ListItem(
                            modifier = Modifier.clickable { vm.jumpToQueueItem(index) },
                            headlineContent = {
                                Text(
                                    song.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified
                                )
                            },
                            supportingContent = {
                                Text(
                                    if (isCurrent) "Сейчас играет" else song.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingContent = {
                                if (!isCurrent) {
                                    IconButton(onClick = { vm.removeFromQueue(index) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Убрать из очереди")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        dismissButton = {
            TextButton(onClick = { vm.clearUpcoming() }) { Text("Очистить следующие") }
        }
    )
}
