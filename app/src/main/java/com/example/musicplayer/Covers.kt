package com.example.musicplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import kotlin.math.max

/**
 * Обложки песен. Приоритет такой: своя обложка, выбранная в приложении,
 * иначе картинка, записанная внутри самого файла песни (если она там есть).
 */
object CoverStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "covers").apply { mkdirs() }

    private fun customFile(context: Context, songId: Long): File =
        File(dir(context), "$songId.jpg")

    fun hasCustom(context: Context, songId: Long): Boolean =
        customFile(context, songId).exists()

    /** Сохраняет выбранную картинку как обложку песни (в памяти приложения). */
    fun setCustom(context: Context, songId: Long, uri: Uri): Boolean {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

            // Проверяем, удалось ли открыть поток и получить размеры
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return false

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                false
            } else {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, 1200)
                }

                val bitmap = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                } ?: return false

                customFile(context, songId).outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun removeCustom(context: Context, songId: Long): Boolean =
        customFile(context, songId).delete()

    /** Переносит свою обложку, если у песни сменился внутренний номер. */
    fun move(context: Context, oldId: Long, newId: Long) {
        val old = customFile(context, oldId)
        if (old.exists()) old.renameTo(customFile(context, newId))
    }

    /** Обложка песни, уменьшенная до maxSide пикселей по длинной стороне, или null. */
    fun load(context: Context, song: Song, maxSide: Int): Bitmap? {
        val custom = customFile(context, song.id)
        if (custom.exists()) {
            decodeFile(custom.absolutePath, maxSide)?.let { return it }
        }
        return loadEmbedded(context, song, maxSide)
    }

    private fun loadEmbedded(context: Context, song: Song, maxSide: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, song.uri)
            val bytes = retriever.embeddedPicture
            if (bytes == null) null else decodeBytes(bytes, maxSide)
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sampleFor(w: Int, h: Int, maxSide: Int): Int {
        var sample = 1
        val longest = max(w, h)
        while (longest / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    private fun decodeFile(path: String, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, maxSide)
        }
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun decodeBytes(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, maxSide)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}