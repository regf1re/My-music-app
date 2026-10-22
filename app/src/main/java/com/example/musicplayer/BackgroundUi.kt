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
//  Фон для текста песни: показ и выбор
// ====================================================================
/**
Анимированный или обычный фон. alpha = 0 — фон не показывается и не загружается.
playing = false — гифка замирает на текущем кадре, при playing = true продолжается с него.
 */
@Composable
fun BackgroundView(
    bg: BgItem?,
    alpha: Float,
    modifier: Modifier = Modifier,
    playing: Boolean = true
) {
    val context = LocalContext.current
    val enabled = alpha > 0f && bg != null
    val drawable by produceState<Drawable?>(initialValue = null, key1 = bg?.id, key2 = enabled) {
        value = if (enabled && bg != null) {
            withContext(Dispatchers.IO) { BackgroundStore.loadDrawable(context, bg) }
        } else null
    }
    DisposableEffect(drawable) {
        // Запоминаем именно ЭТУ картинку: при замене остановить нужно старую, а не новую
        val captured = drawable
        onDispose { if (captured != null) BackgroundStore.stop(captured) }
    }
    val current = drawable
    if (enabled && current != null) {
        AndroidView(
            modifier = modifier.alpha(alpha),
            factory = { ctx ->
                ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            },
            update = { view ->
                if (view.drawable !== current) view.setImageDrawable(current)
                if (playing) BackgroundStore.start(current) else BackgroundStore.stop(current)
            }
        )
    }
}

@Composable
fun BackgroundTile(
    bg: BgItem,
    selected: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(initialValue = null, key1 = bg.id) {
        value = withContext(Dispatchers.IO) {
            BackgroundStore.loadThumbnail(context, bg)?.asImageBitmap()
        }
    }
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .aspectRatio(0.75f)
            .clip(shape)
            .border(if (selected) 3.dp else 1.dp, borderColor, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        thumb?.let {
            Image(
                bitmap = it,
                contentDescription = bg.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (pinned) {
            Text("📌", modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
        }
    }
}

@Composable
fun BackgroundPickerDialog(vm: PlayerViewModel, song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var pinMode by remember { mutableStateOf(vm.songBgs.containsKey(song.id)) }
    var toDelete by remember { mutableStateOf<BgItem?>(null) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.addBackgrounds(uris) { count ->
                val text = if (count > 0) "Добавлено фонов: $count" else "Не удалось добавить файл"
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val pinnedId = vm.songBgs[song.id]
    val activeId = vm.effectiveBg(song.id)?.id
    val shape = RoundedCornerShape(12.dp)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фон для текста") },
        text = {
            Column {
                Text(
                    "Прозрачность фона: ${(vm.lyricsBgAlpha * 100).roundToInt()}%",
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = vm.lyricsBgAlpha,
                    onValueChange = { vm.changeLyricsBgAlpha(it) },
                    valueRange = 0f..1f
                )
                Text(
                    "0% — фон выключен, 100% — фон виден полностью.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (vm.lyricsBgAlpha > 0f && vm.backgrounds.isEmpty()) {
                    Text(
                        "Нет фонов: нажмите «+» и добавьте картинку или гифку.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !pinMode,
                        onClick = { pinMode = false },
                        label = { Text("Для всех песен") }
                    )
                    FilterChip(
                        selected = pinMode,
                        onClick = { pinMode = true },
                        label = { Text("Для этой песни") }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (pinMode) "Нажмите на фон, чтобы закрепить его за этой песней."
                    else "Нажмите на фон, чтобы использовать его для всех песен.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (pinnedId != null) {
                    TextButton(onClick = { vm.unpinBackground(song.id) }) {
                        Text("Открепить фон от этой песни")
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.heightIn(max = 260.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Box(
                            Modifier
                                .aspectRatio(0.75f)
                                .clip(shape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                                .clickable { picker.launch(arrayOf("image/*")) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    gridItems(vm.backgrounds, key = { it.id }) { bg ->
                        BackgroundTile(
                            bg = bg,
                            selected = bg.id == activeId,
                            pinned = bg.id == pinnedId,
                            onClick = {
                                if (pinMode) vm.pinBackground(song.id, bg.id)
                                else vm.selectBackground(bg.id)
                            },
                            onLongClick = {
                                if (bg.id.startsWith("file:")) toDelete = bg
                                else Toast.makeText(
                                    context,
                                    "Этот фон встроен в приложение, его нельзя удалить",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Плюс — добавить картинки или гифки из памяти телефона. Долгое нажатие на фон — удалить.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } }
    )
    toDelete?.let { bg ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить фон?") },
            text = { Text("Фон будет удалён из приложения, а закрепления за песнями снимутся.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteBackground(bg.id)
                    toDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } }
        )
    }
}
