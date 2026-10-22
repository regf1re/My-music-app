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
//  Главный экран
// ====================================================================
@Composable
fun MusicApp(
    vm: PlayerViewModel = viewModel(),
    uiScale: Float = 1f,
    onScaleChange: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val audioPerm = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val allPerms = remember {
        buildList {
            add(audioPerm)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun audioGranted() =
        ContextCompat.checkSelfPermission(context, audioPerm) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (audioGranted()) {
            vm.hasPermission = true
            vm.loadSongs()
        }
    }
    LaunchedEffect(Unit) {
        if (audioGranted()) {
            vm.hasPermission = true
            vm.loadSongs()
        }
        val missing = allPerms.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
    }
    var tab by remember { mutableStateOf(0) }
    var openPlaylistId by remember { mutableStateOf<String?>(null) }
    var openArtistKey by remember { mutableStateOf<String?>(null) }
    var openAlbumKey by remember { mutableStateOf<String?>(null) }
    val uiScope = rememberCoroutineScope()
    // 0 = большой плеер закрыт, 1 = открыт; между ними он едет за пальцем
    val playerProgress = remember { Animatable(0f).apply { updateBounds(0f, 1f) } }
    var showBatteryInfo by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    BackHandler(enabled = playerProgress.value > 0f) {
        uiScope.launch {
            playerProgress.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        }
    }
    BackHandler(enabled = playerProgress.value == 0f && tab == 1 && openPlaylistId != null) {
        openPlaylistId = null
    }
    BackHandler(enabled = playerProgress.value == 0f && tab == 2 && openArtistKey != null) {
        openArtistKey = null
    }
    BackHandler(enabled = playerProgress.value == 0f && tab == 3 && openAlbumKey != null) {
        openAlbumKey = null
    }
    val song = vm.currentSong
    LaunchedEffect(song == null) {
        if (song == null) playerProgress.snapTo(0f)
    }

    // Переход из меню песни: «Посмотреть альбом / исполнителя»
    LaunchedEffect(vm.navRequest) {
        val request = vm.navRequest ?: return@LaunchedEffect
        when (request.tab) {
            2 -> {
                tab = 2
                openArtistKey = request.key
            }
            3 -> {
                tab = 3
                openAlbumKey = request.key
            }
        }
        vm.consumeNavRequest()
        if (playerProgress.value > 0f) {
            playerProgress.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat()
        // Плавно открывает или закрывает большой плеер (с учётом скорости движения пальца)
        fun settlePlayer(open: Boolean, velocity: Float = 0f) {
            uiScope.launch {
                playerProgress.animateTo(
                    targetValue = if (open) 1f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    initialVelocity = -velocity / heightPx
                )
            }
        }
        // Свайп вверх по мини-плееру: большой плеер выезжает вслед за пальцем
        val miniDrag = Modifier.draggable(
            orientation = Orientation.Vertical,
            state = rememberDraggableState { delta ->
                uiScope.launch {
                    playerProgress.snapTo((playerProgress.value - delta / heightPx).coerceIn(0f, 1f))
                }
            },
            onDragStopped = { velocity ->
                val open = when {
                    velocity < -700f -> true
                    velocity > 700f -> false
                    else -> playerProgress.value > 0.4f
                }
                settlePlayer(open, velocity)
            }
        )
        // Свайп вниз по большому плееру: он уходит в мини-плеер вслед за пальцем
        val playerDrag = Modifier.draggable(
            orientation = Orientation.Vertical,
            state = rememberDraggableState { delta ->
                uiScope.launch {
                    playerProgress.snapTo((playerProgress.value - delta / heightPx).coerceIn(0f, 1f))
                }
            },
            onDragStopped = { velocity ->
                val open = when {
                    velocity > 700f -> false
                    velocity < -700f -> true
                    else -> playerProgress.value > 0.65f
                }
                settlePlayer(open, velocity)
            }
        )
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Музыка") },
                    actions = {
                        IconButton(onClick = { showStats = true }) {
                            Icon(Icons.Default.BarChart, contentDescription = "Статистика")
                        }
                        IconButton(onClick = { showBatteryInfo = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Фоновая работа")
                        }
                    }
                )
            },
            bottomBar = {
                Column {
                    if (song != null) {
                        MiniPlayer(vm, song, modifier = miniDrag) { settlePlayer(true) }
                    }
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                            label = { Text("Все песни") }
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            icon = { Icon(Icons.Default.QueueMusic, contentDescription = null) },
                            label = { Text("Плейлисты") }
                        )
                        NavigationBarItem(
                            selected = tab == 2,
                            onClick = { tab = 2 },
                            icon = { Icon(Icons.Default.Person, contentDescription = null) },
                            label = { Text("Исполнители", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                        NavigationBarItem(
                            selected = tab == 3,
                            onClick = { tab = 3 },
                            icon = { Icon(Icons.Default.Album, contentDescription = null) },
                            label = { Text("Альбомы", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                        NavigationBarItem(
                            selected = tab == 4,
                            onClick = { tab = 4 },
                            icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                            label = { Text("Эквалайзер", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                if (!vm.hasPermission) {
                    NoPermissionScreen(
                        onRequest = { launcher.launch(arrayOf(audioPerm)) },
                        onSettings = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                            )
                        }
                    )
                } else if (tab == 0) {
                    SongsScreen(vm)
                } else if (tab == 1) {
                    PlaylistsScreen(vm, openPlaylistId) { openPlaylistId = it }
                } else if (tab == 2) {
                    ArtistsScreen(vm, openArtistKey) { openArtistKey = it }
                } else if (tab == 3) {
                    AlbumsScreen(vm, openAlbumKey) { openAlbumKey = it }
                } else {
                    EqualizerScreen()
                }
            }
        }
        // Большой плеер: выезжает снизу поверх всего экрана
        if (playerProgress.value > 0f && song != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(playerDrag)
                    .offset { IntOffset(0, ((1f - playerProgress.value) * heightPx).roundToInt()) }
            ) {
                PlayerScreen(vm, song) { settlePlayer(false) }
            }
        }
    }
    if (showHidden) {
        HiddenSongsDialog(vm) { showHidden = false }
    }

    if (showStats) {
        StatsDialog { showStats = false }
    }
    if (showBatteryInfo) {
        SettingsDialog(vm, uiScale, onScaleChange) { showBatteryInfo = false }
    }
}

@Composable
fun NoPermissionScreen(onRequest: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Чтобы показать вашу музыку, приложению нужен доступ к аудиофайлам.",
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Разрешить доступ") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSettings) { Text("Открыть настройки приложения") }
    }
}
