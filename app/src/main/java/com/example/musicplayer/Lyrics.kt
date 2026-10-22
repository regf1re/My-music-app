package com.example.musicplayer

import android.os.Build
import android.os.Environment
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Одна строка текста песни. timeMs — момент, когда строка начинается. */
data class LyricLine(val timeMs: Long, val text: String)

/** Состояние загрузки текста песни. */
sealed interface LyricsState {
    object Loading : LyricsState
    object NoPermission : LyricsState
    object NotFound : LyricsState
    data class Plain(val text: String) : LyricsState
    data class Synced(val lines: List<LyricLine>) : LyricsState
}

/** Ищет рядом с песней файл .lrc с тем же именем и разбирает его. */
object LyricsLoader {

    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET_TAG = Regex("""\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)
    private val META_TAG = Regex("""^\[[A-Za-z#]+:.*]$""")
    private val WORD_TAG = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    /** Для чтения .lrc на Android 11+ нужен доступ ко всем файлам. */
    fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true

    fun load(song: Song): LyricsState {
        if (!hasAccess()) return LyricsState.NoPermission
        return try {
            val file = find(song) ?: return LyricsState.NotFound
            parse(readText(file))
        } catch (e: Exception) {
            LyricsState.NotFound
        }
    }

    /** Упрощённое имя для сравнения: без регистра, пробелов, «_», «-» и прочих знаков. */
    fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /** Находит файл .lrc песни (рядом с ней или в любой папке телефона). */
    fun find(song: Song): File? {
        val audio = if (song.path.isNotBlank()) File(song.path) else null
        val dir = audio?.parentFile
        val base = audio?.nameWithoutExtension.orEmpty()

        // 1. Рядом с песней: то же имя файла (или название песни)
        if (dir != null) {
            val near = dir.listFiles()
                ?.filter { it.isFile && it.extension.equals("lrc", ignoreCase = true) }
                .orEmpty()
            near.firstOrNull { it.nameWithoutExtension.equals(base, ignoreCase = true) }?.let { return it }
            near.firstOrNull { it.nameWithoutExtension.equals(song.title, ignoreCase = true) }?.let { return it }
        }

        // 2. Среди всех файлов .lrc на телефоне: совпадение имени файла
        val key = normalize(base)
        if (key.isEmpty()) return null
        val candidates = LyricsIndex.get()[key] ?: return null
        return candidates.firstOrNull { it.parentFile == dir }
            ?: candidates.maxByOrNull { it.lastModified() }
    }

    /** Куда сохранять .lrc: туда, где он уже лежит, иначе рядом с песней. */
    fun targetFile(song: Song): File? {
        find(song)?.let { return it }
        if (song.path.isBlank()) return null
        val audio = File(song.path)
        val dir = audio.parentFile ?: return null
        return File(dir, audio.nameWithoutExtension + ".lrc")
    }

    private fun readText(file: File): String {
        var bytes = file.readBytes()
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes = bytes.copyOfRange(3, bytes.size)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1251"))
        }
    }

    private fun timeOf(m: MatchResult): Long {
        val min = m.groupValues[1].toLong()
        val sec = m.groupValues[2].toLong()
        val frac = m.groupValues[3]
        val ms = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return (min * 60 + sec) * 1000 + ms
    }

    fun parse(raw: String): LyricsState {
        val offset = OFFSET_TAG.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val synced = ArrayList<LyricLine>()
        val plain = StringBuilder()

        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val times = ArrayList<Long>()
            var pos = 0
            while (true) {
                val m = TIME_TAG.find(trimmed, pos)
                if (m == null || m.range.first != pos) break
                times.add(timeOf(m))
                pos = m.range.last + 1
            }

            if (times.isEmpty()) {
                if (!META_TAG.matches(trimmed)) plain.append(trimmed).append('\n')
                continue
            }
            val text = trimmed.substring(pos).replace(WORD_TAG, "").trim()
            for (t in times) {
                synced.add(LyricLine((t - offset).coerceAtLeast(0L), text))
            }
        }

        return when {
            synced.isNotEmpty() -> LyricsState.Synced(synced.sortedBy { it.timeMs })
            plain.isNotBlank() -> LyricsState.Plain(plain.toString().trim())
            else -> LyricsState.NotFound
        }
    }
}

/** Список всех файлов .lrc на телефоне: имя (в упрощённом виде) -> файлы. */
object LyricsIndex {
    @Volatile
    private var index: Map<String, List<File>>? = null

    fun isBuilt(): Boolean = index != null

    fun count(): Int = index?.values?.sumOf { it.size } ?: 0

    @Synchronized
    fun get(): Map<String, List<File>> = index ?: scan().also { index = it }

    /** Заново просматривает память телефона и возвращает число найденных файлов .lrc. */
    @Synchronized
    fun rebuild(): Int {
        val fresh = scan()
        index = fresh
        return fresh.values.sumOf { it.size }
    }

    private fun scan(): Map<String, List<File>> {
        val result = HashMap<String, MutableList<File>>()
        val roots = LinkedHashSet<File>()
        roots.add(Environment.getExternalStorageDirectory())
        // Карты памяти и другие тома
        File("/storage").listFiles()?.forEach {
            if (it.isDirectory && it.name != "emulated" && it.name != "self") roots.add(it)
        }
        for (root in roots) walk(root, result, 0)
        return result
    }

    private fun walk(dir: File, out: HashMap<String, MutableList<File>>, depth: Int) {
        if (depth > 12) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.isDirectory) {
                if (f.name.startsWith(".")) continue
                if (depth == 0 && f.name.equals("Android", ignoreCase = true)) continue
                walk(f, out, depth + 1)
            } else if (f.extension.equals("lrc", ignoreCase = true)) {
                val key = LyricsLoader.normalize(f.nameWithoutExtension)
                if (key.isNotEmpty()) out.getOrPut(key) { ArrayList() }.add(f)
            }
        }
    }
}