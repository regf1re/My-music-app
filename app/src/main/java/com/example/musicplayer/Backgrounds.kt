package com.example.musicplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** Имя гифки, которая лежит в папке assets проекта (необязательно). */
const val GIF_ASSET = "lyrics_bg.gif"
const val DEFAULT_BG_ID = "asset:$GIF_ASSET"

/** Один фон для текста песни. id: «asset:имя» или «file:имя». */
data class BgItem(val id: String, val label: String)

/** Библиотека фонов: гифка из assets и картинки/гифки, которые вы добавили из памяти телефона. */
object BackgroundStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "backgrounds").apply { mkdirs() }

    fun list(context: Context): List<BgItem> {
        val result = ArrayList<BgItem>()
        try {
            if (context.assets.list("")?.contains(GIF_ASSET) == true) {
                result.add(BgItem(DEFAULT_BG_ID, "По умолчанию"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        dir(context).listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.lastModified() }
            ?.forEach { result.add(BgItem("file:${it.name}", it.nameWithoutExtension)) }
        return result
    }

    /** Копирует выбранную картинку или гифку в память приложения. */
    fun add(context: Context, uri: Uri): BgItem? {
        return try {
            val resolver = context.contentResolver
            val ext = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(resolver.getType(uri)) ?: "img"
            val target = File(dir(context), "${UUID.randomUUID()}.$ext")
            val input = resolver.openInputStream(uri) ?: return null
            input.use { src -> target.outputStream().use { dst -> src.copyTo(dst) } }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || target.length() > 60L * 1024 * 1024) {
                target.delete()
                null
            } else {
                BgItem("file:${target.name}", target.nameWithoutExtension)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun delete(context: Context, id: String): Boolean {
        if (!id.startsWith("file:")) return false
        return File(dir(context), id.removePrefix("file:")).delete()
    }

    private fun openStream(context: Context, item: BgItem): InputStream =
        if (item.id.startsWith("asset:")) {
            context.assets.open(item.id.removePrefix("asset:"))
        } else {
            File(dir(context), item.id.removePrefix("file:")).inputStream()
        }

    private fun decodeSampled(context: Context, item: BgItem, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(context, item).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return openStream(context, item).use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /** Фон для показа: анимированная гифка (Android 9+) или обычная картинка. */
    fun loadDrawable(context: Context, item: BgItem): Drawable? =
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                Api28.decodeDrawable(context, item)
            } else {
                decodeSampled(context, item, 1600)?.let { BitmapDrawable(context.resources, it) }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }

    /** Маленькая картинка (первый кадр) для списка выбора. */
    fun loadThumbnail(context: Context, item: BgItem): Bitmap? =
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                Api28.decodeThumbnail(context, item)
            } else {
                decodeSampled(context, item, 300)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }

    fun start(drawable: Drawable) {
        if (Build.VERSION.SDK_INT >= 28) Api28.start(drawable)
    }

    fun stop(drawable: Drawable) {
        if (Build.VERSION.SDK_INT >= 28) Api28.stop(drawable)
    }

    @RequiresApi(28)
    private object Api28 {
        private fun source(context: Context, item: BgItem): ImageDecoder.Source =
            if (item.id.startsWith("asset:")) {
                ImageDecoder.createSource(context.assets, item.id.removePrefix("asset:"))
            } else {
                ImageDecoder.createSource(File(context.filesDir, "backgrounds/" + item.id.removePrefix("file:")))
            }

        fun decodeDrawable(context: Context, item: BgItem): Drawable =
            ImageDecoder.decodeDrawable(source(context, item)) { decoder, info, _ ->
                limit(decoder, info.size.width, info.size.height, 1600)
            }

        fun decodeThumbnail(context: Context, item: BgItem): Bitmap =
            ImageDecoder.decodeBitmap(source(context, item)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                limit(decoder, info.size.width, info.size.height, 300)
            }

        private fun limit(decoder: ImageDecoder, w: Int, h: Int, maxSide: Int) {
            val longest = max(w, h)
            if (longest > maxSide) {
                val k = maxSide.toFloat() / longest
                decoder.setTargetSize(
                    (w * k).roundToInt().coerceAtLeast(1),
                    (h * k).roundToInt().coerceAtLeast(1)
                )
            }
        }

        fun start(d: Drawable) {
            if (d is AnimatedImageDrawable) {
                d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                d.start()
            }
        }

        fun stop(d: Drawable) {
            if (d is AnimatedImageDrawable) d.stop()
        }
    }
}

/** Фоны, закреплённые за конкретными песнями: id песни -> id фона. */
class SongBgStore(context: Context) {
    private val prefs = context.getSharedPreferences("song_backgrounds", Context.MODE_PRIVATE)

    fun loadAll(): Map<Long, String> =
        prefs.all.mapNotNull { (key, value) ->
            val id = key.toLongOrNull()
            if (id != null && value is String) id to value else null
        }.toMap()

    fun set(songId: Long, bgId: String) {
        prefs.edit().putString(songId.toString(), bgId).apply()
    }

    fun remove(songId: Long) {
        prefs.edit().remove(songId.toString()).apply()
    }
}
