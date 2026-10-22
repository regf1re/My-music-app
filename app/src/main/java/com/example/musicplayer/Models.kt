package com.example.musicplayer

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val uri: Uri,
    val dateAdded: Long = 0L,
    val path: String = "",
    val album: String = "",
    val albumId: Long = 0L,
    val track: Int = 0
)

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>
)

enum class PlayMode(val label: String) {
    ORDER("По порядку"),
    REPEAT_ALL("По кругу"),
    REPEAT_ONE("Повтор песни"),
    SHUFFLE("Случайно")
}

enum class SortOrder(val label: String) {
    TITLE_ASC("Название (А–Я)"),
    TITLE_DESC("Название (Я–А)"),
    ARTIST("Исполнитель"),
    NEWEST("Сначала новые"),
    SHORTEST("Сначала короткие"),
    LONGEST("Сначала длинные")
}