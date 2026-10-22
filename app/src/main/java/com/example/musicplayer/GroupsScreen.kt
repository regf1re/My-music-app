package com.example.musicplayer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.fillMaxWidth

private const val UNKNOWN_ARTIST = "Неизвестный исполнитель"
private const val UNKNOWN_ALBUM = "Неизвестный альбом"

/** Делит строку исполнителей по «;»: «A; B» превращается в A и B. */
fun splitArtists(raw: String): List<String> {
    val parts = raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }
    return if (parts.isEmpty()) listOf(UNKNOWN_ARTIST) else parts
}

data class ArtistGroup(val key: String, val name: String, val songs: List<Song>)

data class AlbumGroup(val key: String, val name: String, val artist: String, val songs: List<Song>)

/** Исполнители с их песнями. Песня с несколькими исполнителями попадает к каждому из них. */
fun buildArtistGroups(songs: List<Song>): List<ArtistGroup> {
    val spellings = HashMap<String, MutableMap<String, Int>>()
    val members = LinkedHashMap<String, MutableList<Song>>()
    for (song in songs) {
        for (name in splitArtists(song.artist).distinctBy { it.lowercase() }) {
            val key = name.lowercase()
            members.getOrPut(key) { ArrayList() }.add(song)
            val spell = spellings.getOrPut(key) { HashMap() }
            spell[name] = (spell[name] ?: 0) + 1
        }
    }
    val unknownKey = UNKNOWN_ARTIST.lowercase()
    return members.map { (key, list) ->
        val display = spellings[key]?.maxByOrNull { it.value }?.key ?: key
        ArtistGroup(key, display, list.sortedBy { it.title.lowercase() })
    }.sortedWith(compareBy<ArtistGroup>({ it.key == unknownKey }, { it.name.lowercase() }))
}

/** Ключ альбома песни (по нему вкладка «Альбомы» открывает нужный альбом). */
fun albumKeyOf(song: Song): String =
    if (song.albumId > 0L) "id:${song.albumId}" else "name:${song.album.lowercase()}"

/** Альбомы с песнями (внутри альбома по номеру трека). */
fun buildAlbumGroups(songs: List<Song>): List<AlbumGroup> {
    val groups = LinkedHashMap<String, MutableList<Song>>()
    for (song in songs) {
        val key = albumKeyOf(song)
        groups.getOrPut(key) { ArrayList() }.add(song)
    }
    return groups.map { (key, list) ->
        val sorted = list.sortedWith(compareBy<Song>({ it.track }, { it.title.lowercase() }))
        val name = sorted.first().album.ifBlank { UNKNOWN_ALBUM }
        val artist = sorted.flatMap { splitArtists(it.artist) }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key ?: ""
        AlbumGroup(key, name, artist, sorted)
    }.sortedBy { it.name.lowercase() }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center)
    }
}

@Composable
fun ArtistsScreen(vm: PlayerViewModel, openKey: String?, onOpen: (String?) -> Unit) {
    val groups = remember(vm.songs) { buildArtistGroups(vm.songs) }
    val open = groups.firstOrNull { it.key == openKey }

    if (open != null) {
        GroupSongsScreen(vm, open.name, "Песен: ${open.songs.size}", open.songs) { onOpen(null) }
    } else if (groups.isEmpty()) {
        EmptyHint("Исполнителей пока нет")
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(groups, key = { it.key }) { group ->
                ListItem(
                    modifier = Modifier.clickable { onOpen(group.key) },
                    headlineContent = {
                        Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text("Песен: ${group.songs.size}") },
                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
fun AlbumsScreen(vm: PlayerViewModel, openKey: String?, onOpen: (String?) -> Unit) {
    val groups = remember(vm.songs) { buildAlbumGroups(vm.songs) }
    val open = groups.firstOrNull { it.key == openKey }

    if (open != null) {
        val subtitle = (if (open.artist.isNotBlank()) open.artist + " • " else "") +
                "Песен: ${open.songs.size}"
        GroupSongsScreen(vm, open.name, subtitle, open.songs) { onOpen(null) }
    } else if (groups.isEmpty()) {
        EmptyHint("Альбомов пока нет")
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(groups, key = { it.key }) { group ->
                ListItem(
                    modifier = Modifier.clickable { onOpen(group.key) },
                    headlineContent = {
                        Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        val text = (if (group.artist.isNotBlank()) group.artist + " • " else "") +
                                "Песен: ${group.songs.size}"
                        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = { AlbumThumb(group.songs.first()) }
                )
            }
        }
    }
}

@Composable
private fun AlbumThumb(song: Song) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = song.id) {
        value = withContext(Dispatchers.IO) {
            CoverStore.load(context, song, 120)?.asImageBitmap()
        }
    }
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Default.Album, contentDescription = null)
        }
    }
}

/** Песни одного исполнителя или альбома. */
@Composable
fun GroupSongsScreen(
    vm: PlayerViewModel,
    title: String,
    subtitle: String,
    songs: List<Song>,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Button(onClick = { vm.play(songs, 0) }, enabled = songs.isNotEmpty()) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Играть")
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                SongRow(
                    song = song,
                    current = vm.currentSong?.id == song.id,
                    onClick = { vm.play(songs, index) },
                    favorite = song.id in vm.favorites
                ) {
                    SongMenuButton(vm, song)
                }
            }
        }
    }
}