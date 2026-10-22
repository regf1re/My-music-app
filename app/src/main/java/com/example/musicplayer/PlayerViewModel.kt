package com.example.musicplayer

import android.app.Application
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.widget.Toast
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class PlayerViewModel(private val app: Application) : AndroidViewModel(app) {

    private val store = PlaylistStore(app)
    private val artistStore = ArtistStore(app)
    private val titleStore = TitleStore(app)
    private val albumStore = AlbumStore(app)
    private val songBgStore = SongBgStore(app)
    private val offsetPrefs = app.getSharedPreferences("lyrics_offsets", Context.MODE_PRIVATE)
    private val favStore = FavoritesStore(app)
    private val hiddenStore = HiddenStore(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var hasPermission by mutableStateOf(false)

    private var allSongs: List<Song> = emptyList()
    var hiddenIds by mutableStateOf(hiddenStore.load())
        private set

    /** Песни, которые видны в приложении (без скрытых). */
    var songs by mutableStateOf<List<Song>>(emptyList())
        private set

    /** Скрытые песни (для списка в настройках). */
    var hiddenSongs by mutableStateOf<List<Song>>(emptyList())
        private set
    var playlists by mutableStateOf(store.load())
        private set
    var currentSong by mutableStateOf<Song?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var playMode by mutableStateOf(loadMode())
        private set

    // Настройки режимов: число повторов песни (0 = без ограничений) и сколько последних песен не повторять
    var repeatOneLimit by mutableIntStateOf(prefs.getInt("repeat_one_limit", 0))
        private set
    var shuffleAvoidLast by mutableIntStateOf(prefs.getInt("shuffle_avoid", 5))
        private set
    // Режим, который был до «Повтора песни»: к нему вернёмся, когда повторы закончатся
    var modeBeforeRepeatOne by mutableStateOf(loadModeBeforeRepeatOne())
        private set
    private var repeatsDone = 0
    private var autoNextId: Long? = null
    private var ensureGuard = -1
    var volume by mutableFloatStateOf(prefs.getFloat("volume", 1f))
        private set

    // Скорость воспроизведения (0,5×–2×)
    var speed by mutableFloatStateOf(prefs.getFloat("speed", 1f))
        private set

    // Фон-гифка на экране с текстом песни: 0 = выключен, 1 = полностью видна
    var lyricsBgAlpha by mutableFloatStateOf(prefs.getFloat("lyrics_bg_alpha", 0f))
        private set

    // Анимированные полоски под музыку на экране плеера
    var visualizerEnabled by mutableStateOf(prefs.getBoolean("visualizer", true))
        private set

    // Запрос на переход во вкладку «Исполнители» или «Альбомы» (из меню песни)
    var navRequest by mutableStateOf<NavRequest?>(null)
        private set

    // Плавный переход между песнями (кроссфейд); сервис плеера читает эти же настройки
    var crossfadeEnabled by mutableStateOf(prefs.getBoolean("crossfade_enabled", false))
        private set
    var crossfadeSeconds by mutableIntStateOf(prefs.getInt("crossfade_seconds", 4))
        private set
    var crossfadeSkipAlbum by mutableStateOf(prefs.getBoolean("crossfade_skip_album", false))
        private set

    // Сдвиг текста песни относительно музыки (в миллисекундах) для каждой песни
    var lyricsOffsets by mutableStateOf(loadLyricsOffsets())
        private set

    // Растёт при каждой смене обложки: по нему экран перезагружает картинки
    var coverVersion by mutableIntStateOf(0)
        private set

    // Библиотека фонов, выбранный фон и фоны, закреплённые за песнями
    var backgrounds by mutableStateOf(BackgroundStore.list(app))
        private set
    var selectedBgId by mutableStateOf(prefs.getString("lyrics_bg_id", DEFAULT_BG_ID) ?: DEFAULT_BG_ID)
        private set
    var songBgs by mutableStateOf(songBgStore.loadAll())
        private set

    // Поиск и сортировка списка песен
    var searchQuery by mutableStateOf("")
    var sortOrder by mutableStateOf(loadSort())
        private set

    // Очередь воспроизведения (то, что сейчас лежит в плеере)
    var queue by mutableStateOf<List<Song>>(emptyList())
        private set
    var queueIndex by mutableIntStateOf(-1)
        private set

    // Порядок песен, с которого начали играть (нужен, чтобы вернуть порядок после режима «Случайно»)
    private var baseOrder: List<Long> = emptyList()

    // Избранное
    var favorites by mutableStateOf(favStore.load())
        private set
    var favoritesOnly by mutableStateOf(false)
        private set

    // Тексты песен (.lrc)
    var lyrics by mutableStateOf<LyricsState>(LyricsState.NotFound)
        private set
    private var lyricsFor: Long = -1L
    var lrcCount by mutableIntStateOf(0)
        private set

    // Запоминание очереди
    private var queueRestored = false

    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@PlayerViewModel.isPlaying = isPlaying
            if (!isPlaying) savePosition()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                // Песня началась заново в режиме «Повтор песни»
                if (playMode == PlayMode.REPEAT_ONE && repeatOneLimit > 0) {
                    repeatsDone++
                    if (repeatsDone >= repeatOneLimit) {
                        val back = modeBeforeRepeatOne
                        setMode(back)
                        Toast.makeText(
                            app,
                            "Повторы закончились. Режим: «${back.label}»",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                repeatsDone = 0
            }
            updateCurrent()
            refreshQueue()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            refreshQueue()
        }
    }

    init {
        connect()
        viewModelScope.launch {
            while (true) {
                delay(5000)
                if (isPlaying) savePosition()
            }
        }
    }

    private fun loadMode(): PlayMode =
        runCatching { PlayMode.valueOf(prefs.getString("mode", null) ?: "REPEAT_ALL") }
            .getOrDefault(PlayMode.REPEAT_ALL)

    private fun loadModeBeforeRepeatOne(): PlayMode =
        runCatching { PlayMode.valueOf(prefs.getString("mode_before_repeat_one", null) ?: "REPEAT_ALL") }
            .getOrDefault(PlayMode.REPEAT_ALL)

    private fun loadSort(): SortOrder =
        runCatching { SortOrder.valueOf(prefs.getString("sort", null) ?: "TITLE_ASC") }
            .getOrDefault(SortOrder.TITLE_ASC)

    private fun connect() {
        val token = SessionToken(app, ComponentName(app, MusicService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                c.volume = volume
                c.setPlaybackSpeed(speed)
                isPlaying = c.isPlaying
                updateCurrent()
                refreshQueue()
                tryRestoreQueue()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun updateCurrent() {
        val id = controller?.currentMediaItem?.mediaId?.toLongOrNull()
        currentSong = if (id == null) null else songs.firstOrNull { it.id == id }
        if ((currentSong?.id ?: -1L) != lyricsFor) loadLyricsFor(currentSong)
    }

    fun loadSongs() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val artistOverrides = artistStore.loadAll()
                val titleOverrides = titleStore.loadAll()
                val albumOverrides = albumStore.loadAll()
                MusicRepository.loadSongs(app).map { s ->
                    var r = s
                    artistOverrides[s.id]?.let { r = r.copy(artist = it) }
                    titleOverrides[s.id]?.let { r = r.copy(title = it) }
                    albumOverrides[s.id]?.let { r = r.copy(album = it, albumId = 0L) }
                    r
                }
            }
            publishSongs(loaded)
            updateCurrent()
            refreshQueue()
            tryRestoreQueue()
            indexLyricsInBackground()
        }
    }

    // ---------- Избранное ----------

    fun isFavorite(id: Long): Boolean = id in favorites

    fun toggleFavorite(id: Long) {
        favorites = if (id in favorites) favorites - id else favorites + id
        favStore.save(favorites)
    }

    fun toggleFavoritesOnly() {
        favoritesOnly = !favoritesOnly
    }

    // ---------- Тексты песен ----------

    private fun loadLyricsFor(song: Song?) {
        if (song == null) {
            lyricsFor = -1L
            lyrics = LyricsState.NotFound
            return
        }
        lyricsFor = song.id
        lyrics = LyricsState.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { LyricsLoader.load(song) }
            if (lyricsFor == song.id) lyrics = result
        }
    }

    /** Заново ищет файлы .lrc по всему телефону и обновляет текст текущей песни. */
    fun rescanLyrics() {
        viewModelScope.launch {
            if (currentSong != null) lyrics = LyricsState.Loading
            val n = withContext(Dispatchers.IO) {
                if (LyricsLoader.hasAccess()) LyricsIndex.rebuild() else 0
            }
            lrcCount = n
            loadLyricsFor(currentSong)
        }
    }

    fun reloadLyrics() {
        rescanLyrics()
    }

    /** При запуске в фоне находит все файлы .lrc (один раз за запуск). */
    private fun indexLyricsInBackground() {
        viewModelScope.launch {
            if (!LyricsLoader.hasAccess()) return@launch
            if (LyricsIndex.isBuilt()) {
                lrcCount = LyricsIndex.count()
                return@launch
            }
            val n = withContext(Dispatchers.IO) { LyricsIndex.rebuild() }
            lrcCount = n
            // если текст текущей песни ещё не найден, пробуем снова
            if (currentSong != null && lyrics !is LyricsState.Synced && lyrics !is LyricsState.Plain) {
                loadLyricsFor(currentSong)
            }
        }
    }

    // ---------- Запоминание очереди ----------

    private fun saveQueue() {
        if (!queueRestored) return
        val c = controller ?: return
        val ids = (0 until c.mediaItemCount).joinToString(",") { c.getMediaItemAt(it).mediaId }
        prefs.edit()
            .putString("q_ids", ids)
            .putInt("q_index", c.currentMediaItemIndex)
            .putLong("q_pos", c.currentPosition.coerceAtLeast(0L))
            .apply()
    }

    private fun savePosition() {
        if (!queueRestored) return
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        prefs.edit()
            .putInt("q_index", c.currentMediaItemIndex)
            .putLong("q_pos", c.currentPosition.coerceAtLeast(0L))
            .apply()
    }

    /** При запуске приложения возвращает прошлую очередь (на паузе, с того же места). */
    private fun tryRestoreQueue() {
        if (queueRestored) return
        val c = controller ?: return
        if (songs.isEmpty()) return
        queueRestored = true
        if (c.mediaItemCount > 0) return

        val ids = prefs.getString("q_ids", null)
            ?.split(",")
            ?.mapNotNull { it.toLongOrNull() }
            ?: return
        val map = songs.associateBy { it.id }
        val savedIndex = prefs.getInt("q_index", 0)
        val kept = ids.withIndex().filter { map.containsKey(it.value) }
        if (kept.isEmpty()) return

        val found = kept.indexOfFirst { it.index == savedIndex }
        val newIndex = if (found >= 0) found else 0
        val position = if (found >= 0) prefs.getLong("q_pos", 0L) else 0L

        baseOrder = if (playMode == PlayMode.SHUFFLE) songs.map { it.id } else kept.map { it.value }
        c.setMediaItems(kept.map { mediaItemOf(map.getValue(it.value)) }, newIndex, position)
        applyRepeat(c)
        c.prepare()
        updateCurrent()
        refreshQueue()
    }

    // ---------- Переименование песни и файла .lrc ----------

    private class RenameOutcome(val ok: Boolean, val message: String, val newPath: String = "")

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .trimEnd('.', ' ')
            .take(150)

    private suspend fun scanAndWait(paths: Array<String>) {
        withTimeoutOrNull(4000) {
            suspendCancellableCoroutine<Unit> { cont ->
                val left = AtomicInteger(paths.size)
                MediaScannerConnection.scanFile(app, paths, null) { _, _ ->
                    if (left.decrementAndGet() == 0 && cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    /** Переименовывает файл песни. Сначала через медиатеку (id песни сохраняется), иначе напрямую. */
    private suspend fun renameAudioFile(song: Song, oldFile: File, newFile: File): Boolean {
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, newFile.name)
            }
            val rows = app.contentResolver.update(song.uri, values, null, null)
            if (rows > 0 && newFile.exists() && !oldFile.exists()) return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (oldFile.exists() && !newFile.exists() && oldFile.renameTo(newFile)) {
            scanAndWait(arrayOf(oldFile.absolutePath, newFile.absolutePath))
            return true
        }
        return newFile.exists() && !oldFile.exists()
    }

    private suspend fun renameOnDisk(song: Song, title: String): RenameOutcome {
        if (song.path.isBlank()) return RenameOutcome(false, "Не найден файл песни")
        val oldFile = File(song.path)
        if (!oldFile.exists()) return RenameOutcome(false, "Не найден файл песни")

        val safeBase = sanitizeFileName(title)
        if (safeBase.isEmpty()) return RenameOutcome(false, "В названии нет допустимых символов")
        val ext = oldFile.extension
        val newName = if (ext.isEmpty()) safeBase else "$safeBase.$ext"
        val newFile = File(oldFile.parentFile, newName)
        val sameFile = newFile.absolutePath == oldFile.absolutePath

        val lrc = LyricsLoader.find(song)

        if (!sameFile) {
            if (newFile.exists()) return RenameOutcome(false, "Файл «$newName» уже существует")
            if (!renameAudioFile(song, oldFile, newFile)) {
                return RenameOutcome(false, "Не удалось переименовать файл песни")
            }
        }

        var lrcNote = ""
        if (lrc != null) {
            val newLrc = File(lrc.parentFile, "$safeBase.lrc")
            lrcNote = when {
                newLrc.absolutePath == lrc.absolutePath -> ""
                newLrc.exists() -> " Файл .lrc не переименован: «${newLrc.name}» уже существует."
                lrc.renameTo(newLrc) -> " Файл .lrc тоже переименован."
                else -> " Файл .lrc переименовать не удалось."
            }
        }
        return RenameOutcome(true, "Готово.$lrcNote", newFile.absolutePath)
    }

    private fun migrateSongId(oldId: Long, newId: Long) {
        if (oldId in favorites) {
            favorites = favorites - oldId + newId
            favStore.save(favorites)
        }
        if (playlists.any { oldId in it.songIds }) {
            savePlaylists(playlists.map { p ->
                if (oldId in p.songIds) {
                    p.copy(songIds = p.songIds.map { if (it == oldId) newId else it }.distinct())
                } else p
            })
        }
        artistStore.loadAll()[oldId]?.let {
            artistStore.set(newId, it)
            artistStore.remove(oldId)
        }
        titleStore.loadAll()[oldId]?.let {
            titleStore.set(newId, it)
            titleStore.remove(oldId)
        }
        albumStore.loadAll()[oldId]?.let {
            albumStore.set(newId, it)
            albumStore.remove(oldId)
        }
        CoverStore.move(app, oldId, newId)
        coverVersion++
        lyricsOffsets[oldId]?.let { off ->
            offsetPrefs.edit().remove(oldId.toString()).putLong(newId.toString(), off).apply()
            lyricsOffsets = lyricsOffsets - oldId + (newId to off)
        }
        if (oldId in hiddenIds) {
            hiddenIds = hiddenIds - oldId + newId
            hiddenStore.save(hiddenIds)
        }
        songBgStore.loadAll()[oldId]?.let {
            songBgStore.set(newId, it)
            songBgStore.remove(oldId)
            songBgs = songBgStore.loadAll()
        }
        prefs.getString("q_ids", null)?.let { raw ->
            val fixed = raw.split(",").joinToString(",") {
                if (it == oldId.toString()) newId.toString() else it
            }
            prefs.edit().putString("q_ids", fixed).apply()
        }
    }

    /**
     * Меняет название песни: переименовывает файл песни и её файл .lrc,
     * а в списке показывает новое название. Результат передаётся в onResult.
     */
    fun renameSong(song: Song, newTitle: String, onResult: (String) -> Unit) {
        val title = newTitle.trim()
        if (title.isEmpty()) {
            onResult("Введите название")
            return
        }
        if (isPlaying && currentSong?.id == song.id) {
            onResult("Нельзя переименовать песню, которая сейчас играет")
            return
        }
        if (!LyricsLoader.hasAccess()) {
            onResult("Нужен доступ ко всем файлам: откройте «Текст» на экране плеера и разрешите доступ")
            return
        }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { renameOnDisk(song, title) }
            if (!outcome.ok) {
                onResult(outcome.message)
                return@launch
            }

            titleStore.set(song.id, title)
            reloadSongsNow()

            val newSong = songs.firstOrNull { it.path == outcome.newPath }
            if (newSong != null && newSong.id != song.id) {
                migrateSongId(song.id, newSong.id)
                reloadSongsNow()
                val fresh = songs.firstOrNull { it.id == newSong.id }
                val c = controller
                if (c != null && fresh != null) {
                    for (i in 0 until c.mediaItemCount) {
                        if (c.getMediaItemAt(i).mediaId == song.id.toString()) {
                            c.replaceMediaItem(i, mediaItemOf(fresh))
                        }
                    }
                }
            }
            refreshQueue()
            updateCurrent()
            rescanLyrics()
            onResult(outcome.message)
        }
    }

    private suspend fun reloadSongsNow() {
        val loaded = withContext(Dispatchers.IO) {
            val artistOverrides = artistStore.loadAll()
            val titleOverrides = titleStore.loadAll()
            val albumOverrides = albumStore.loadAll()
            MusicRepository.loadSongs(app).map { s ->
                var r = s
                artistOverrides[s.id]?.let { r = r.copy(artist = it) }
                titleOverrides[s.id]?.let { r = r.copy(title = it) }
                albumOverrides[s.id]?.let { r = r.copy(album = it, albumId = 0L) }
                r
            }
        }
        publishSongs(loaded)
    }

    // ---------- Скрытые песни и удаление ----------

    private fun publishSongs(all: List<Song>) {
        allSongs = all
        republishSongs()
    }

    private fun republishSongs() {
        songs = allSongs.filter { it.id !in hiddenIds }
        hiddenSongs = allSongs.filter { it.id in hiddenIds }
    }

    private fun removeFromPlayerQueue(id: Long) {
        val c = controller ?: return
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId == id.toString()) c.removeMediaItem(i)
        }
    }

    /** Скрывает песню: она пропадает из списков и очереди и больше не играет. */
    fun hideSong(song: Song) {
        hiddenIds = hiddenIds + song.id
        hiddenStore.save(hiddenIds)
        republishSongs()
        removeFromPlayerQueue(song.id)
        updateCurrent()
        refreshQueue()
    }

    fun unhideSong(id: Long) {
        hiddenIds = hiddenIds - id
        hiddenStore.save(hiddenIds)
        republishSongs()
    }

    /** Убирает из приложения всё, что было связано с удалённой песней. */
    private fun forgetSong(id: Long) {
        if (id in favorites) {
            favorites = favorites - id
            favStore.save(favorites)
        }
        if (id in hiddenIds) {
            hiddenIds = hiddenIds - id
            hiddenStore.save(hiddenIds)
        }
        if (playlists.any { id in it.songIds }) {
            savePlaylists(playlists.map { p ->
                if (id in p.songIds) p.copy(songIds = p.songIds.filterNot { it == id }) else p
            })
        }
        artistStore.remove(id)
        titleStore.remove(id)
        albumStore.remove(id)
        songBgStore.remove(id)
        songBgs = songBgs - id
        CoverStore.removeCustom(app, id)
        offsetPrefs.edit().remove(id.toString()).apply()
        lyricsOffsets = lyricsOffsets - id
        removeFromPlayerQueue(id)
    }

    private suspend fun deleteFiles(song: Song): RenameOutcome {
        val file = if (song.path.isNotBlank()) File(song.path) else null
        val lrc = LyricsLoader.find(song)

        var removed = false
        try {
            val rows = app.contentResolver.delete(song.uri, null, null)
            if (rows > 0 && (file == null || !file.exists())) removed = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (!removed && file != null && file.exists()) {
            if (file.delete()) {
                scanAndWait(arrayOf(file.absolutePath))
                removed = true
            }
        }
        if (!removed && (file == null || !file.exists())) removed = true
        if (!removed) return RenameOutcome(false, "Не удалось удалить файл песни")

        var note = ""
        if (lrc != null) {
            note = if (lrc.exists() && !lrc.delete()) {
                " Файл .lrc удалить не удалось."
            } else {
                " Файл .lrc тоже удалён."
            }
        }
        return RenameOutcome(true, "Песня удалена.$note")
    }

    /** Удаляет файл песни и её файл .lrc с устройства. Результат передаётся в onResult. */
    fun deleteSong(song: Song, onResult: (String) -> Unit) {
        if (isPlaying && currentSong?.id == song.id) {
            onResult("Нельзя удалить песню, которая сейчас играет")
            return
        }
        if (!LyricsLoader.hasAccess()) {
            onResult("Нужен доступ ко всем файлам: откройте «Текст» на экране плеера и разрешите доступ")
            return
        }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { deleteFiles(song) }
            if (!outcome.ok) {
                onResult(outcome.message)
                return@launch
            }
            forgetSong(song.id)
            reloadSongsNow()
            refreshQueue()
            updateCurrent()
            rescanLyrics()
            onResult(outcome.message)
        }
    }

    // ---------- Обложки ----------

    /** Ставит выбранную картинку как обложку песни (только внутри приложения). */
    fun setCover(song: Song, uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { CoverStore.setCustom(app, song.id, uri) }
            if (ok) coverVersion++
            onDone(ok)
        }
    }

    /** Убирает свою обложку: вернётся обложка из файла песни (если она там есть). */
    fun resetCover(song: Song) {
        if (CoverStore.removeCustom(app, song.id)) coverVersion++
    }

    // ---------- Имя исполнителя ----------

    /** Сохраняет своё имя исполнителя для песни (внутри приложения). */
    fun renameArtist(songId: Long, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        artistStore.set(songId, clean)
        publishSongs(allSongs.map { if (it.id == songId) it.copy(artist = clean) else it })
        updateCurrent()
        refreshQueue()
    }


    /** Возвращает исходное имя исполнителя из файла. */
    fun resetArtist(songId: Long) {
        artistStore.remove(songId)
        loadSongs()
    }


    // ---------- Поиск и сортировка ----------

    fun updateSearch(text: String) {
        searchQuery = text
    }

    fun changeSort(order: SortOrder) {
        sortOrder = order
        prefs.edit().putString("sort", order.name).apply()
    }

    /** Песни с учётом поиска (по названию и исполнителю) и выбранной сортировки. */
    fun filteredSongs(): List<Song> {
        val q = searchQuery.trim()
        val pool = if (favoritesOnly) songs.filter { it.id in favorites } else songs
        val base = if (q.isEmpty()) pool else pool.filter {
            it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true)
        }
        return when (sortOrder) {
            SortOrder.TITLE_ASC -> base.sortedBy { it.title.lowercase() }
            SortOrder.TITLE_DESC -> base.sortedByDescending { it.title.lowercase() }
            SortOrder.ARTIST -> base.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
            SortOrder.NEWEST -> base.sortedByDescending { it.dateAdded }
            SortOrder.SHORTEST -> base.sortedBy { it.durationMs }
            SortOrder.LONGEST -> base.sortedByDescending { it.durationMs }
        }
    }

    // ---------- Воспроизведение ----------

    private fun mediaItemOf(s: Song): MediaItem =
        MediaItem.Builder()
            .setMediaId(s.id.toString())
            .setUri(s.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(s.title)
                    .setArtist(s.artist)
                    .setAlbumTitle(s.album.ifBlank { null })
                    .setArtworkUri(coverArtUri(s.id, coverVersion))
                    .build()
            )
            .build()

    fun play(queue: List<Song>, startIndex: Int) {
        val c = controller ?: return
        if (queue.isEmpty()) return
        val start = startIndex.coerceIn(0, queue.size - 1)
        baseOrder = queue.map { it.id }
        if (playMode == PlayMode.SHUFFLE) {
            // Случайный порядок: сначала играет выбранная песня, следующие подбираются случайно
            c.setMediaItems(listOf(mediaItemOf(queue[start])), 0, 0L)
            autoNextId = null
            ensureGuard = -1
        } else {
            c.setMediaItems(queue.map { mediaItemOf(it) }, start, 0L)
        }
        applyRepeat(c)
        c.prepare()
        c.play()
    }

    // ---------- Очередь ----------

    private fun refreshQueue() {
        val c = controller ?: return
        val map = songs.associateBy { it.id }
        queue = (0 until c.mediaItemCount).map { i ->
            val item = c.getMediaItemAt(i)
            val id = item.mediaId.toLongOrNull()
            map[id] ?: Song(
                id = id ?: -1L,
                title = item.mediaMetadata.title?.toString() ?: "Без названия",
                artist = item.mediaMetadata.artist?.toString() ?: "",
                durationMs = 0L,
                uri = Uri.EMPTY
            )
        }
        queueIndex = c.currentMediaItemIndex
        saveQueue()
        ensureShuffleNext()
    }

    /** Песня зазвучит сразу после текущей. */
    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(song), 0)
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, mediaItemOf(song))
        }
    }

    /** Песня встанет в самый конец очереди. */
    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(song), 0)
        } else {
            c.addMediaItem(mediaItemOf(song))
        }
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount && index != c.currentMediaItemIndex) {
            c.removeMediaItem(index)
        }
    }

    fun jumpToQueueItem(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    fun clearUpcoming() {
        val c = controller ?: return
        val from = c.currentMediaItemIndex + 1
        if (from in 1 until c.mediaItemCount) {
            c.removeMediaItems(from, c.mediaItemCount)
        }
    }

    private fun replaceRange(c: Player, from: Int, to: Int, items: List<MediaItem>) {
        if (items.isEmpty()) {
            if (to > from) c.removeMediaItems(from, to)
        } else {
            c.replaceMediaItems(from, to, items)
        }
    }

    private fun idsInPlayer(c: Player): List<Long> =
        (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1L }

    private fun itemsForIds(ids: List<Long>): List<MediaItem> {
        val map = songs.associateBy { it.id }
        return ids.mapNotNull { id -> map[id]?.let { mediaItemOf(it) } }
    }

    // ---------- Случайный порядок: следующая песня выбирается случайно, но не из последних N ----------

    /** Песни, из которых выбирается следующая: тот список, с которого начали играть. */
    private fun shufflePool(): List<Song> {
        val ids = if (baseOrder.isEmpty()) songs.map { it.id } else baseOrder
        val map = songs.associateBy { it.id }
        return ids.distinct().mapNotNull { map[it] }
    }

    /** В режиме «Случайно» в очереди всегда должна быть следующая песня. */
    private fun ensureShuffleNext() {
        if (playMode != PlayMode.SHUFFLE) return
        val c = controller ?: return
        val count = c.mediaItemCount
        val cur = c.currentMediaItemIndex
        if (count == 0 || cur !in 0 until count) return
        if (cur < count - 1) {
            ensureGuard = -1
            return
        }
        if (count == ensureGuard) return
        val pool = shufflePool()
        if (pool.isEmpty()) return

        // Последние «avoid» проигранных песен (вместе с текущей) в следующую не попадают
        val avoid = minOf(shuffleAvoidLast, pool.size - 1).coerceAtLeast(0)
        val recent = HashSet<Long>()
        for (i in maxOf(0, cur - avoid + 1)..cur) {
            c.getMediaItemAt(i).mediaId.toLongOrNull()?.let { recent.add(it) }
        }
        val currentId = c.getMediaItemAt(cur).mediaId.toLongOrNull()
        var candidates = pool.filter { it.id !in recent }
        if (candidates.isEmpty()) candidates = pool.filter { it.id != currentId }
        if (candidates.isEmpty()) candidates = pool

        val pick = candidates.random()
        autoNextId = pick.id
        ensureGuard = count
        c.addMediaItem(mediaItemOf(pick))

        // Чтобы очередь не росла бесконечно, старые сыгранные песни убираем
        if (cur > 60) c.removeMediaItems(0, cur - 50)
    }

    /** Подбирает следующую песню заново (например, после смены настройки «не повторять»). */
    private fun repickShuffleNext() {
        val c = controller ?: return
        val auto = autoNextId ?: return
        val count = c.mediaItemCount
        val cur = c.currentMediaItemIndex
        if (count > 0 && cur < count - 1 && c.getMediaItemAt(count - 1).mediaId == auto.toString()) {
            ensureGuard = -1
            c.removeMediaItem(count - 1)
        }
    }

    /** Переход в «Случайно»: всё, что ещё не сыграло, убираем, дальше песни подбираются случайно. */
    private fun enterShuffle(c: Player) {
        val count = c.mediaItemCount
        val cur = c.currentMediaItemIndex
        if (count == 0 || cur !in 0 until count) return
        if (baseOrder.isEmpty()) baseOrder = idsInPlayer(c)
        if (cur + 1 < count) c.removeMediaItems(cur + 1, count)
        autoNextId = null
        ensureGuard = -1
        ensureShuffleNext()
    }

    /** Выход из «Случайно»: возвращаем исходный список, текущая песня продолжает играть. */
    private fun leaveShuffle(c: Player) {
        val ids = idsInPlayer(c)
        val cur = c.currentMediaItemIndex
        if (ids.isEmpty() || cur !in ids.indices || baseOrder.isEmpty()) return
        val curKey = baseOrder.indexOf(ids[cur])
        val beforeIds = if (curKey >= 0) baseOrder.subList(0, curKey) else emptyList()
        val afterIds = if (curKey >= 0) baseOrder.subList(curKey + 1, baseOrder.size) else baseOrder
        val beforeItems = itemsForIds(beforeIds)
        val afterItems = itemsForIds(afterIds)
        if (cur + 1 < ids.size) c.removeMediaItems(cur + 1, ids.size)
        if (cur > 0) c.removeMediaItems(0, cur)
        if (afterItems.isNotEmpty()) c.addMediaItems(afterItems)
        if (beforeItems.isNotEmpty()) c.addMediaItems(0, beforeItems)
        autoNextId = null
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L

    fun durationMs(): Long {
        val d = controller?.duration ?: C.TIME_UNSET
        return if (d == C.TIME_UNSET || d <= 0L) (currentSong?.durationMs ?: 0L) else d
    }

    // ---------- Режимы и громкость ----------

    private fun applyRepeat(c: Player) {
        c.shuffleModeEnabled = false
        c.repeatMode = when (playMode) {
            PlayMode.ORDER -> Player.REPEAT_MODE_OFF
            PlayMode.REPEAT_ALL -> Player.REPEAT_MODE_ALL
            PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            PlayMode.SHUFFLE -> Player.REPEAT_MODE_ALL
        }
    }

    /** Включает выбранный режим воспроизведения. */
    fun setMode(newMode: PlayMode) {
        val old = playMode
        if (newMode == old) return
        if (newMode == PlayMode.REPEAT_ONE) {
            modeBeforeRepeatOne = old
            prefs.edit().putString("mode_before_repeat_one", old.name).apply()
        }
        repeatsDone = 0
        playMode = newMode
        prefs.edit().putString("mode", newMode.name).apply()
        controller?.let { c ->
            applyRepeat(c)
            if (old != PlayMode.SHUFFLE && newMode == PlayMode.SHUFFLE) {
                enterShuffle(c)
            } else if (old == PlayMode.SHUFFLE && newMode != PlayMode.SHUFFLE) {
                leaveShuffle(c)
            }
        }
    }

    /** Короткое нажатие на кнопку режима: следующий режим по кругу. */
    fun cycleMode() {
        val all = PlayMode.values()
        setMode(all[(playMode.ordinal + 1) % all.size])
    }

    /** Сколько раз повторить песню (0 = без ограничений). */
    fun changeRepeatOneLimit(n: Int) {
        repeatOneLimit = n.coerceIn(0, 99)
        repeatsDone = 0
        prefs.edit().putInt("repeat_one_limit", repeatOneLimit).apply()
    }

    /** Сколько последних песен не повторять в режиме «Случайно». */
    fun changeShuffleAvoid(n: Int) {
        shuffleAvoidLast = n.coerceIn(0, 30)
        prefs.edit().putInt("shuffle_avoid", shuffleAvoidLast).apply()
        if (playMode == PlayMode.SHUFFLE) repickShuffleNext()
    }

    /** Фон для песни: закреплённый за ней, иначе общий выбранный. */
    fun effectiveBg(songId: Long?): BgItem? {
        val pinned = if (songId != null) songBgs[songId] else null
        return backgrounds.firstOrNull { it.id == pinned }
            ?: backgrounds.firstOrNull { it.id == selectedBgId }
            ?: backgrounds.firstOrNull()
    }

    fun selectBackground(id: String) {
        selectedBgId = id
        prefs.edit().putString("lyrics_bg_id", id).apply()
    }

    fun pinBackground(songId: Long, bgId: String) {
        songBgStore.set(songId, bgId)
        songBgs = songBgs + (songId to bgId)
    }

    fun unpinBackground(songId: Long) {
        songBgStore.remove(songId)
        songBgs = songBgs - songId
    }

    /** Копирует выбранные картинки и гифки в память приложения. */
    fun addBackgrounds(uris: List<Uri>, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) { uris.mapNotNull { BackgroundStore.add(app, it) } }
            backgrounds = BackgroundStore.list(app)
            onDone(added.size)
        }
    }

    fun deleteBackground(id: String): Boolean {
        if (!BackgroundStore.delete(app, id)) return false
        backgrounds = BackgroundStore.list(app)
        if (selectedBgId == id) selectBackground(backgrounds.firstOrNull()?.id ?: DEFAULT_BG_ID)
        val pinsToRemove = songBgs.filterValues { it == id }.keys
        pinsToRemove.forEach { songBgStore.remove(it) }
        songBgs = songBgs - pinsToRemove
        return true
    }

    private fun loadLyricsOffsets(): Map<Long, Long> =
        offsetPrefs.all.mapNotNull { (key, value) ->
            val id = key.toLongOrNull()
            val offset = value as? Long
            if (id != null && offset != null) id to offset else null
        }.toMap()

    fun lyricsOffsetFor(songId: Long): Long = lyricsOffsets[songId] ?: 0L

    /** Сдвигает текст: плюс — текст позже, минус — текст раньше. */
    fun shiftLyrics(songId: Long, deltaMs: Long) {
        val value = (lyricsOffsetFor(songId) + deltaMs).coerceIn(-10_000L, 10_000L)
        if (value == 0L) {
            offsetPrefs.edit().remove(songId.toString()).apply()
            lyricsOffsets = lyricsOffsets - songId
        } else {
            offsetPrefs.edit().putLong(songId.toString(), value).apply()
            lyricsOffsets = lyricsOffsets + (songId to value)
        }
    }

    fun resetLyricsShift(songId: Long) {
        offsetPrefs.edit().remove(songId.toString()).apply()
        lyricsOffsets = lyricsOffsets - songId
    }

    fun changeCrossfadeEnabled(on: Boolean) {
        crossfadeEnabled = on
        prefs.edit().putBoolean("crossfade_enabled", on).apply()
    }

    fun changeCrossfadeSeconds(n: Int) {
        crossfadeSeconds = n.coerceIn(1, 12)
        prefs.edit().putInt("crossfade_seconds", crossfadeSeconds).apply()
    }

    fun changeCrossfadeSkipAlbum(on: Boolean) {
        crossfadeSkipAlbum = on
        prefs.edit().putBoolean("crossfade_skip_album", on).apply()
    }

    fun changeVisualizer(on: Boolean) {
        visualizerEnabled = on
        prefs.edit().putBoolean("visualizer", on).apply()
    }

    /** Сохраняет текст в файл .lrc (рядом с песней или поверх существующего файла). */
    fun saveLrc(song: Song, content: String, onResult: (Boolean, String) -> Unit) {
        if (!LyricsLoader.hasAccess()) {
            onResult(false, "Нужен доступ ко всем файлам: откройте «Текст» на экране плеера и разрешите доступ")
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val target = LyricsLoader.targetFile(song)
                        ?: return@withContext Pair(false, "Не удалось определить, куда сохранить файл")
                    target.writeText(content, Charsets.UTF_8)
                    Pair(true, "Сохранено: ${target.name}.")
                } catch (e: Exception) {
                    Pair(false, "Не удалось сохранить файл: ${e.message}")
                }
            }
            if (result.first) loadLyricsFor(currentSong)
            onResult(result.first, result.second)
        }
    }

    fun changeLyricsBgAlpha(v: Float) {
        lyricsBgAlpha = v.coerceIn(0f, 1f)
        prefs.edit().putFloat("lyrics_bg_alpha", lyricsBgAlpha).apply()
    }

    fun openArtist(name: String) {
        navRequest = NavRequest(2, name.trim().lowercase())
    }

    fun openAlbum(song: Song) {
        navRequest = NavRequest(3, albumKeyOf(song))
    }

    fun consumeNavRequest() {
        navRequest = null
    }

    /** Меняет альбом песни (только внутри приложения). Пустое название возвращает альбом из файла. */
    fun setAlbum(songId: Long, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            albumStore.remove(songId)
            loadSongs()
            return
        }
        albumStore.set(songId, clean)
        publishSongs(allSongs.map { if (it.id == songId) it.copy(album = clean, albumId = 0L) else it })
    }

    fun changeSpeed(v: Float) {
        // шаг 0,05×
        val value = Math.round(v.coerceIn(0.5f, 2f) * 20f) / 20f
        speed = value
        prefs.edit().putFloat("speed", value).apply()
        controller?.setPlaybackSpeed(value)
    }

    fun changeVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        controller?.volume = volume
        prefs.edit().putFloat("volume", volume).apply()
    }

    // ---------- Плейлисты ----------

    private fun savePlaylists(list: List<Playlist>) {
        playlists = list
        store.save(list)
    }

    fun createPlaylist(name: String) {
        savePlaylists(playlists + Playlist(UUID.randomUUID().toString(), name, emptyList()))
    }

    fun deletePlaylist(id: String) {
        savePlaylists(playlists.filterNot { it.id == id })
    }

    fun addSongs(playlistId: String, ids: List<Long>) {
        savePlaylists(playlists.map { p ->
            if (p.id == playlistId) p.copy(songIds = (p.songIds + ids).distinct()) else p
        })
    }

    fun removeSong(playlistId: String, songId: Long) {
        savePlaylists(playlists.map { p ->
            if (p.id == playlistId) p.copy(songIds = p.songIds.filterNot { it == songId }) else p
        })
    }

    override fun onCleared() {
        savePosition()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}

/** Просьба открыть вкладку (2 — исполнители, 3 — альбомы) на нужной группе. */
class NavRequest(val tab: Int, val key: String)