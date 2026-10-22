package com.example.musicplayer

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

/** Ищет всю музыку на устройстве через системную медиатеку. */
object MusicRepository {
    fun loadSongs(context: Context): List<Song> {
        val result = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(collection, projection, selection, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val artist = c.getString(artistCol)
                val album = c.getString(albumCol)
                result.add(
                    Song(
                        id = id,
                        title = c.getString(titleCol) ?: "Без названия",
                        artist = if (artist.isNullOrBlank() || artist == MediaStore.UNKNOWN_STRING)
                            "Неизвестный исполнитель" else artist,
                        durationMs = c.getLong(durCol),
                        uri = ContentUris.withAppendedId(collection, id),
                        dateAdded = c.getLong(addedCol),
                        path = c.getString(dataCol) ?: "",
                        album = if (album.isNullOrBlank() || album == MediaStore.UNKNOWN_STRING) "" else album,
                        albumId = c.getLong(albumIdCol),
                        track = c.getInt(trackCol)
                    )
                )
            }
        }
        return result
    }
}

/** Хранит плейлисты в памяти приложения (SharedPreferences, формат JSON). */
class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    fun load(): List<Playlist> {
        val raw = prefs.getString("data", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ids = o.getJSONArray("songs")
                Playlist(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    songIds = (0 until ids.length()).map { ids.getLong(it) }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p ->
            val ids = JSONArray()
            p.songIds.forEach { ids.put(it) }
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("songs", ids)
            )
        }
        prefs.edit().putString("data", arr.toString()).apply()
    }
}

/** Хранит имена исполнителей, которые вы изменили вручную (внутри приложения, сами файлы не меняются). */
class ArtistStore(context: Context) {
    private val prefs = context.getSharedPreferences("artists", Context.MODE_PRIVATE)

    fun loadAll(): Map<Long, String> =
        prefs.all.mapNotNull { (key, value) ->
            val id = key.toLongOrNull()
            if (id != null && value is String) id to value else null
        }.toMap()

    fun set(id: Long, name: String) {
        prefs.edit().putString(id.toString(), name).apply()
    }

    fun remove(id: Long) {
        prefs.edit().remove(id.toString()).apply()
    }
}

/** Хранит id песен, добавленных в избранное. */
class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun load(): Set<Long> =
        prefs.getStringSet("ids", emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()

    fun save(ids: Set<Long>) {
        prefs.edit().putStringSet("ids", ids.map { it.toString() }.toSet()).apply()
    }
}

/** Хранит названия песен, которые вы изменили вручную. */
class TitleStore(context: Context) {
    private val prefs = context.getSharedPreferences("titles", Context.MODE_PRIVATE)

    fun loadAll(): Map<Long, String> =
        prefs.all.mapNotNull { (key, value) ->
            val id = key.toLongOrNull()
            if (id != null && value is String) id to value else null
        }.toMap()

    fun set(id: Long, title: String) {
        prefs.edit().putString(id.toString(), title).apply()
    }

    fun remove(id: Long) {
        prefs.edit().remove(id.toString()).apply()
    }
}

/** Хранит id песен, скрытых в приложении (они не показываются и не играют). */
class HiddenStore(context: Context) {
    private val prefs = context.getSharedPreferences("hidden_songs", Context.MODE_PRIVATE)

    fun load(): Set<Long> =
        prefs.getStringSet("ids", emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()

    fun save(ids: Set<Long>) {
        prefs.edit().putStringSet("ids", ids.map { it.toString() }.toSet()).apply()
    }
}

/** Хранит названия альбомов, которые вы изменили вручную. */
class AlbumStore(context: Context) {
    private val prefs = context.getSharedPreferences("albums", Context.MODE_PRIVATE)

    fun loadAll(): Map<Long, String> =
        prefs.all.mapNotNull { (key, value) ->
            val id = key.toLongOrNull()
            if (id != null && value is String) id to value else null
        }.toMap()

    fun set(id: Long, album: String) {
        prefs.edit().putString(id.toString(), album).apply()
    }

    fun remove(id: Long) {
        prefs.edit().remove(id.toString()).apply()
    }
}