package com.example.musicplayer

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val UNKNOWN_ARTIST_NAME = "Неизвестный исполнитель"

@Composable
private fun MenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(24.dp))
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier
                .padding(start = 72.dp, end = 24.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

/** Меню песни: название сверху, список действий со значками, внизу «ОТМЕНА». */
@Composable
fun SongActionsDialog(
    vm: PlayerViewModel,
    song: Song,
    onDismiss: () -> Unit,
    onPickPlaylist: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
    onBackgroundSettings: (() -> Unit)? = null,
    onLyricsEditor: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val artists = remember(song.artist) { splitArtists(song.artist).take(3) }
    val albumName = song.album.ifBlank { "Неизвестный альбом" }
    val favorite = song.id in vm.favorites

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(top = 20.dp, bottom = 4.dp)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(8.dp))

                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    MenuRow(
                        if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (favorite) "Убрать из избранного" else "Добавить в избранное"
                    ) {
                        vm.toggleFavorite(song.id)
                        onDismiss()
                    }
                    MenuRow(Icons.Default.PlaylistAdd, "Добавить в плейлист") {
                        onDismiss()
                        onPickPlaylist()
                    }
                    MenuRow(Icons.Default.PlaylistPlay, "Воспроизвести следующую") {
                        vm.playNext(song)
                        Toast.makeText(context, "Сыграет следующей", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                    MenuRow(Icons.Default.QueueMusic, "Добавить в очередь") {
                        vm.addToQueue(song)
                        Toast.makeText(context, "Добавлено в очередь", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                    MenuRow(Icons.Default.Share, "Отправить") {
                        onDismiss()
                        try {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, song.uri)
                                clipData = ClipData.newRawUri("", song.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Отправить песню"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Не удалось отправить песню", Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (onBackgroundSettings != null) {
                        MenuRow(Icons.Default.Image, "Фон текста") {
                            onDismiss()
                            onBackgroundSettings()
                        }
                    }
                    if (onLyricsEditor != null) {
                        MenuRow(Icons.Default.Edit, "Редактор .lrc") {
                            onDismiss()
                            onLyricsEditor()
                        }
                    }
                    MenuRow(Icons.Default.Album, "Посмотреть альбом: $albumName") {
                        vm.openAlbum(song)
                        onDismiss()
                    }
                    artists.forEach { name ->
                        MenuRow(Icons.Default.Person, "Посмотреть исполнителя: $name") {
                            vm.openArtist(name)
                            onDismiss()
                        }
                    }
                    MenuRow(Icons.Default.Info, "Сведения композиции") {
                        onDismiss()
                        onDetails()
                    }
                    MenuRow(Icons.Default.VisibilityOff, "Скрыть") {
                        vm.hideSong(song)
                        Toast.makeText(
                            context,
                            "Песня скрыта. Вернуть её можно в настройках (шестерёнка).",
                            Toast.LENGTH_LONG
                        ).show()
                        onDismiss()
                    }
                    MenuRow(Icons.Default.Delete, "Удалить") {
                        onDismiss()
                        onDelete()
                    }
                }

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("ОТМЕНА")
                }
            }
        }
    }
}

private fun fileSizeText(path: String): String {
    val size = try {
        File(path).length()
    } catch (e: Exception) {
        0L
    }
    return when {
        size <= 0L -> "—"
        size >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f МБ", size / 1048576f)
        else -> "${size / 1024} КБ"
    }
}

/** Сведения о песне: обложка, название, исполнители, альбом, текст, размер и путь. */
@Composable
fun SongDetailsDialog(vm: PlayerViewModel, song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val startArtists = if (song.artist == UNKNOWN_ARTIST_NAME) "" else song.artist

    var name by remember(song.id) { mutableStateOf(song.title) }
    var artists by remember(song.id) { mutableStateOf(startArtists) }
    var album by remember(song.id) { mutableStateOf(song.album) }
    var saving by remember { mutableStateOf(false) }
    var showCover by remember { mutableStateOf(false) }
    var showLrc by remember { mutableStateOf(false) }

    val cover = rememberCoverImages(vm, song)
    val lyricsState by produceState<LyricsState?>(initialValue = null, key1 = song.id) {
        value = withContext(Dispatchers.IO) { LyricsLoader.load(song) }
    }
    val sizeText = remember(song.path) { fileSizeText(song.path) }

    val changed = name.trim() != song.title ||
        artists.trim() != startArtists ||
        album.trim() != song.album

    fun save() {
        saving = true
        val newName = name.trim()
        val newArtists = artists.trim()
        val newAlbum = album.trim()
        if (newArtists.isNotEmpty() && newArtists != startArtists) vm.renameArtist(song.id, newArtists)
        if (newAlbum != song.album) vm.setAlbum(song.id, newAlbum)
        if (newName.isNotEmpty() && newName != song.title) {
            // название меняет и имя файла песни (и файла .lrc)
            vm.renameSong(song, newName) { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                saving = false
                onDismiss()
            }
        } else {
            Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
            saving = false
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (!saving) onDismiss() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                    Text(
                        "Сведения композиции",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(enabled = changed && !saving, onClick = { save() }) {
                        Icon(Icons.Default.Check, contentDescription = "Сохранить")
                    }
                }
                Spacer(Modifier.height(16.dp))

                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(200.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { showCover = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (cover != null) {
                        Image(
                            bitmap = cover.full,
                            contentDescription = "Обложка",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    "Нажмите на обложку, чтобы изменить",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    supportingText = { Text("При смене названия переименуется и файл песни") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = artists,
                    onValueChange = { artists = it },
                    label = { Text("Исполнители") },
                    supportingText = { Text("Несколько исполнителей — через «;»") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Альбом") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                ListItem(
                    modifier = Modifier.clickable {
                        if (vm.currentSong?.id == song.id) {
                            showLrc = true
                        } else {
                            Toast.makeText(
                                context,
                                "Включите эту песню, чтобы редактировать её текст",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    headlineContent = { Text("Слова композиции") },
                    supportingContent = {
                        Text(
                            when (lyricsState) {
                                null -> "Проверка…"
                                is LyricsState.Synced -> "Есть, с метками времени"
                                is LyricsState.Plain -> "Есть, без меток времени"
                                is LyricsState.NoPermission -> "Нужен доступ ко всем файлам"
                                else -> "Нет. Нажмите, чтобы создать"
                            }
                        )
                    },
                    trailingContent = { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null) }
                )
                ListItem(
                    headlineContent = { Text("Размер") },
                    supportingContent = { Text(sizeText) }
                )
                ListItem(
                    headlineContent = { Text("Путь") },
                    supportingContent = { Text(song.path.ifBlank { "—" }) }
                )
            }
        }
    }

    if (showCover) CoverDialog(vm, song) { showCover = false }
    if (showLrc) LrcEditorDialog(vm, song) { showLrc = false }
}
