package com.example.musicplayer

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.FileNotFoundException

const val COVER_AUTHORITY = "com.example.musicplayer.covers"

/** Адрес обложки песни для уведомления и экрана блокировки. v нужен, чтобы после смены обложки адрес был новым. */
fun coverArtUri(songId: Long, version: Int): Uri =
    Uri.parse("content://$COVER_AUTHORITY/$songId?v=$version")

/**
 * Отдаёт обложку песни системе медиа-уведомлений: свою (выбранную в приложении)
 * или записанную внутри файла песни. Доступна только самому приложению.
 */
class CoverProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: throw FileNotFoundException("нет контекста")
        val id = uri.lastPathSegment?.toLongOrNull() ?: throw FileNotFoundException("неверный адрес")
        val version = uri.getQueryParameter("v") ?: "0"

        val dir = File(ctx.cacheDir, "notification_art").apply { mkdirs() }
        val file = File(dir, "$id-$version.jpg")
        if (!file.exists()) {
            val song = Song(
                id = id,
                title = "",
                artist = "",
                durationMs = 0L,
                uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            )
            val bitmap = CoverStore.load(ctx, song, 720) ?: throw FileNotFoundException("у песни нет обложки")
            val old = dir.listFiles()
            if (old != null && old.size > 60) old.forEach { it.delete() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = "image/jpeg"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
