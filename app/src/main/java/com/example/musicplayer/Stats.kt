package com.example.musicplayer

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

class SongStat(
    val id: Long,
    var title: String,
    var artist: String,
    var album: String,
    var totalMs: Long,
    var plays: Int,
    var lastPlayed: Long,
    val byYear: MutableMap<Int, Long> = HashMap(),
    val playsByYear: MutableMap<Int, Int> = HashMap()
)

class HistoryEntry(val id: Long, val title: String, val artist: String, val time: Long)

/** Строка рейтинга: исполнитель, альбом или песня. */
class RankItem(val name: String, val subtitle: String, val totalMs: Long, val plays: Int)

class StatsSummary(
    val totalMs: Long,
    val todayMs: Long,
    val weekMs: Long,
    val plays: Int,
    val songCount: Int
)

/** Итоги года. bestMonth — от 1 до 12 (0, если данных нет), favWeekday — от 0 (понедельник) до 6, -1 если данных нет. */
class YearSummary(
    val year: Int,
    val totalMs: Long,
    val activeDays: Int,
    val plays: Int,
    val songCount: Int,
    val bestMonth: Int,
    val bestMonthMs: Long,
    val favWeekday: Int,
    val topArtist: RankItem?,
    val topSong: RankItem?,
    val topAlbum: RankItem?
)

private val SHORT_MONTHS = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

/** Хранит статистику прослушивания: время по песням и дням, число прослушиваний, историю. */
object StatsStore {
    private const val MAX_HISTORY = 300

    private val songs = HashMap<Long, SongStat>()
    private val history = ArrayList<HistoryEntry>() // новые в начале
    private val days = HashMap<String, Long>()
    private var prefs: SharedPreferences? = null
    private var dirty = false

    /** Растёт при каждом сохранении: по нему экран статистики обновляется. */
    var version by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("stats", Context.MODE_PRIVATE)
        prefs = p
        load(p.getString("data", null))
    }

    private fun load(raw: String?) {
        if (raw == null) return
        try {
            val root = JSONObject(raw)
            root.optJSONArray("songs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val stat = SongStat(
                        o.getLong("id"), o.optString("t"), o.optString("a"), o.optString("al"),
                        o.optLong("ms"), o.optInt("p"), o.optLong("lp")
                    )
                    o.optJSONObject("y")?.let { y ->
                        y.keys().forEach { k -> k.toIntOrNull()?.let { yr -> stat.byYear[yr] = y.getLong(k) } }
                    }
                    o.optJSONObject("yp")?.let { y ->
                        y.keys().forEach { k -> k.toIntOrNull()?.let { yr -> stat.playsByYear[yr] = y.getInt(k) } }
                    }
                    songs[stat.id] = stat
                }
            }
            root.optJSONArray("hist")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    history.add(HistoryEntry(o.getLong("id"), o.optString("t"), o.optString("a"), o.getLong("ts")))
                }
            }
            root.optJSONObject("days")?.let { o ->
                o.keys().forEach { key -> days[key] = o.getLong(key) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addListening(id: Long, title: String, artist: String, album: String, dtMs: Long) {
        if (dtMs <= 0L) return
        val s = songs.getOrPut(id) { SongStat(id, title, artist, album, 0L, 0, 0L) }
        if (title.isNotBlank()) {
            s.title = title
            s.artist = artist
        }
        if (album.isNotBlank()) s.album = album
        s.totalMs += dtMs
        val today = LocalDate.now()
        s.byYear[today.year] = (s.byYear[today.year] ?: 0L) + dtMs
        val day = today.toString()
        days[day] = (days[day] ?: 0L) + dtMs
        dirty = true
    }

    /** Песню прослушали достаточно долго: считаем прослушивание и добавляем в историю. */
    fun recordPlay(id: Long, title: String, artist: String, album: String, time: Long) {
        val s = songs.getOrPut(id) { SongStat(id, title, artist, album, 0L, 0, 0L) }
        s.plays++
        s.lastPlayed = time
        val year = LocalDate.now().year
        s.playsByYear[year] = (s.playsByYear[year] ?: 0) + 1
        history.add(0, HistoryEntry(id, title, artist, time))
        while (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
        dirty = true
        flush()
    }

    fun flush() {
        val p = prefs ?: return
        if (!dirty) return
        val root = JSONObject()
        val songsArr = JSONArray()
        songs.values.forEach { s ->
            val y = JSONObject()
            s.byYear.forEach { (yr, ms) -> y.put(yr.toString(), ms) }
            val yp = JSONObject()
            s.playsByYear.forEach { (yr, n) -> yp.put(yr.toString(), n) }
            songsArr.put(
                JSONObject().put("id", s.id).put("t", s.title).put("a", s.artist).put("al", s.album)
                    .put("ms", s.totalMs).put("p", s.plays).put("lp", s.lastPlayed)
                    .put("y", y).put("yp", yp)
            )
        }
        val histArr = JSONArray()
        history.forEach { h ->
            histArr.put(JSONObject().put("id", h.id).put("t", h.title).put("a", h.artist).put("ts", h.time))
        }
        val daysObj = JSONObject()
        days.forEach { (k, v) -> daysObj.put(k, v) }
        root.put("songs", songsArr).put("hist", histArr).put("days", daysObj)
        p.edit().putString("data", root.toString()).apply()
        dirty = false
        version++
    }

    fun clear() {
        songs.clear()
        history.clear()
        days.clear()
        dirty = true
        flush()
    }

    // ---------- Подсчёты ----------

    private fun forEachDay(block: (LocalDate, Long) -> Unit) {
        for ((key, ms) in days) {
            val date = try {
                LocalDate.parse(key)
            } catch (e: Exception) {
                null
            }
            if (date != null) block(date, ms)
        }
    }

    private fun songMs(s: SongStat, year: Int?): Long = if (year == null) s.totalMs else (s.byYear[year] ?: 0L)

    private fun songPlays(s: SongStat, year: Int?): Int = if (year == null) s.plays else (s.playsByYear[year] ?: 0)

    fun summary(): StatsSummary {
        var total = 0L
        var plays = 0
        var songCount = 0
        for (s in songs.values) {
            total += s.totalMs
            plays += s.plays
            if (s.totalMs > 0L) songCount++
        }
        val today = LocalDate.now()
        val todayMs = days[today.toString()] ?: 0L
        var week = 0L
        for (i in 0..6) week += days[today.minusDays(i.toLong()).toString()] ?: 0L
        return StatsSummary(total, todayMs, week, plays, songCount)
    }

    /** Годы, за которые есть данные (новые первыми), и текущий год. */
    fun years(): List<Int> {
        val result = HashSet<Int>()
        result.add(LocalDate.now().year)
        forEachDay { date, ms -> if (ms > 0L) result.add(date.year) }
        return result.sortedDescending()
    }

    fun topSongs(limit: Int, year: Int? = null): List<RankItem> =
        songs.values
            .map { it to songMs(it, year) }
            .filter { it.second >= 10_000L }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (s, ms) -> RankItem(s.title.ifBlank { "Без названия" }, s.artist, ms, songPlays(s, year)) }

    /** Любимые исполнители по времени прослушивания (песня с несколькими исполнителями учитывается у каждого). */
    fun topArtists(limit: Int, year: Int? = null): List<RankItem> {
        val unknown = "неизвестный исполнитель"
        val totals = HashMap<String, Long>()
        val plays = HashMap<String, Int>()
        val names = HashMap<String, String>()
        for (s in songs.values) {
            val ms = songMs(s, year)
            if (ms <= 0L) continue
            for (name in splitArtists(s.artist).distinctBy { it.lowercase() }) {
                val key = name.lowercase()
                if (key == unknown) continue
                if (!names.containsKey(key)) names[key] = name
                totals[key] = (totals[key] ?: 0L) + ms
                plays[key] = (plays[key] ?: 0) + songPlays(s, year)
            }
        }
        return totals.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { RankItem(names[it.key] ?: it.key, "", it.value, plays[it.key] ?: 0) }
    }

    /** Любимые альбомы по времени прослушивания (учитываются песни, которые слушали после появления этой функции). */
    fun topAlbums(limit: Int, year: Int? = null): List<RankItem> {
        val totals = HashMap<String, Long>()
        val plays = HashMap<String, Int>()
        val names = HashMap<String, String>()
        for (s in songs.values) {
            val ms = songMs(s, year)
            if (ms <= 0L || s.album.isBlank()) continue
            val key = s.album.lowercase()
            if (!names.containsKey(key)) names[key] = s.album
            totals[key] = (totals[key] ?: 0L) + ms
            plays[key] = (plays[key] ?: 0) + songPlays(s, year)
        }
        return totals.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { RankItem(names[it.key] ?: it.key, "", it.value, plays[it.key] ?: 0) }
    }

    /** Время прослушивания по дням недели: индекс 0 — понедельник, 6 — воскресенье. */
    fun weekdayTotals(year: Int? = null): LongArray {
        val result = LongArray(7)
        forEachDay { date, ms ->
            if (year == null || date.year == year) result[date.dayOfWeek.value - 1] += ms
        }
        return result
    }

    /** Время прослушивания за последние месяцы: подпись и миллисекунды, старые месяцы первыми. */
    fun lastMonthsTotals(count: Int): List<Pair<String, Long>> {
        val byMonth = HashMap<YearMonth, Long>()
        forEachDay { date, ms ->
            val ym = YearMonth.of(date.year, date.monthValue)
            byMonth[ym] = (byMonth[ym] ?: 0L) + ms
        }
        val now = YearMonth.now()
        return (count - 1 downTo 0).map { back ->
            val ym = now.minusMonths(back.toLong())
            SHORT_MONTHS[ym.monthValue - 1] to (byMonth[ym] ?: 0L)
        }
    }

    fun yearSummary(year: Int): YearSummary {
        var total = 0L
        var active = 0
        val months = LongArray(12)
        val weekdays = LongArray(7)
        forEachDay { date, ms ->
            if (date.year == year && ms > 0L) {
                total += ms
                active++
                months[date.monthValue - 1] += ms
                weekdays[date.dayOfWeek.value - 1] += ms
            }
        }
        var plays = 0
        var songCount = 0
        for (s in songs.values) {
            if (songMs(s, year) > 0L) songCount++
            plays += songPlays(s, year)
        }
        val bestMonthIdx = months.indices.maxByOrNull { months[it] } ?: 0
        val favIdx = weekdays.indices.maxByOrNull { weekdays[it] } ?: 0
        return YearSummary(
            year = year,
            totalMs = total,
            activeDays = active,
            plays = plays,
            songCount = songCount,
            bestMonth = if (months[bestMonthIdx] > 0L) bestMonthIdx + 1 else 0,
            bestMonthMs = months[bestMonthIdx],
            favWeekday = if (weekdays[favIdx] > 0L) favIdx else -1,
            topArtist = topArtists(1, year).firstOrNull(),
            topSong = topSongs(1, year).firstOrNull(),
            topAlbum = topAlbums(1, year).firstOrNull()
        )
    }

    fun recentHistory(limit: Int): List<HistoryEntry> = history.take(limit)
}

/** Работает внутри сервиса плеера: считает, сколько времени играет каждая песня. */
class StatsTracker(private val context: Context, private val player: Player) {
    private val handler = Handler(Looper.getMainLooper())
    private val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private var lastTick = 0L
    private var sessionId = -1L
    private var sessionMs = 0L
    private var counted = false
    private var sinceFlush = 0L
    private var released = false

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Новая песня (или повтор той же): отсчёт прослушивания начинается заново
            sessionId = -1L
            sessionMs = 0L
            counted = false
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) StatsStore.flush()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (released) return
            try {
                step()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            handler.postDelayed(this, 1000L)
        }
    }

    fun attach() {
        StatsStore.init(context)
        lastTick = SystemClock.elapsedRealtime()
        player.addListener(listener)
        handler.post(tick)
    }

    private fun step() {
        val now = SystemClock.elapsedRealtime()
        val dt = (now - lastTick).coerceIn(0L, 3000L)
        lastTick = now
        if (!player.isPlaying) return
        if (!settings.getBoolean("stats_enabled", true)) return
        val item = player.currentMediaItem ?: return
        val id = item.mediaId.toLongOrNull() ?: return
        if (id != sessionId) {
            sessionId = id
            sessionMs = 0L
            counted = false
        }
        val title = item.mediaMetadata.title?.toString() ?: ""
        val artist = item.mediaMetadata.artist?.toString() ?: ""
        val album = item.mediaMetadata.albumTitle?.toString() ?: ""

        StatsStore.addListening(id, title, artist, album, dt)
        sessionMs += dt
        sinceFlush += dt
        if (!counted && sessionMs >= 15_000L) {
            counted = true
            StatsStore.recordPlay(id, title, artist, album, System.currentTimeMillis())
        }
        if (sinceFlush >= 10_000L) {
            sinceFlush = 0L
            StatsStore.flush()
        }
    }

    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        player.removeListener(listener)
        StatsStore.flush()
    }
}