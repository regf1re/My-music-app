package com.example.musicplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Настройки оформления и поведения. Хранятся в тех же настройках приложения («settings»),
 * поэтому сервис плеера читает часть из них напрямую (таймер сна, пауза при отключении наушников и др.).
 */
object AppSettings {
    private var prefs: SharedPreferences? = null

    /** 0 — как в системе, 1 — светлая, 2 — тёмная */
    var themeMode by mutableIntStateOf(0)
        private set
    var dynamicColors by mutableStateOf(true)
        private set
    var keepScreenOn by mutableStateOf(false)
        private set
    var lyricsTextScale by mutableFloatStateOf(1f)
        private set
    var coverBackground by mutableStateOf(true)
        private set
    var coverDim by mutableFloatStateOf(0.45f)
        private set
    var pauseOnUnplug by mutableStateOf(true)
        private set
    var statsEnabled by mutableStateOf(true)
        private set
    var skipSilence by mutableStateOf(false)
        private set

    /** Когда сработает таймер сна (время в миллисекундах), 0 — таймер выключен. */
    var sleepUntil by mutableLongStateOf(0L)
        private set
    var sleepAfterSong by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        themeMode = p.getInt("theme_mode", 0)
        dynamicColors = p.getBoolean("dynamic_colors", true)
        keepScreenOn = p.getBoolean("keep_screen_on", false)
        lyricsTextScale = p.getFloat("lyrics_text_scale", 1f)
        coverBackground = p.getBoolean("cover_background", true)
        coverDim = p.getFloat("cover_dim", 0.45f)
        pauseOnUnplug = p.getBoolean("pause_on_unplug", true)
        statsEnabled = p.getBoolean("stats_enabled", true)
        skipSilence = p.getBoolean("skip_silence", false)
        refreshSleep()
    }

    fun changeThemeMode(mode: Int) {
        themeMode = mode.coerceIn(0, 2)
        prefs?.edit()?.putInt("theme_mode", themeMode)?.apply()
    }

    fun changeDynamicColors(on: Boolean) {
        dynamicColors = on
        prefs?.edit()?.putBoolean("dynamic_colors", on)?.apply()
    }

    fun changeKeepScreenOn(on: Boolean) {
        keepScreenOn = on
        prefs?.edit()?.putBoolean("keep_screen_on", on)?.apply()
    }

    fun changeLyricsTextScale(v: Float) {
        lyricsTextScale = (Math.round(v.coerceIn(0.8f, 1.5f) * 20f) / 20f)
        prefs?.edit()?.putFloat("lyrics_text_scale", lyricsTextScale)?.apply()
    }

    fun changeCoverBackground(on: Boolean) {
        coverBackground = on
        prefs?.edit()?.putBoolean("cover_background", on)?.apply()
    }

    fun changeCoverDim(v: Float) {
        coverDim = v.coerceIn(0f, 0.8f)
        prefs?.edit()?.putFloat("cover_dim", coverDim)?.apply()
    }

    fun changePauseOnUnplug(on: Boolean) {
        pauseOnUnplug = on
        prefs?.edit()?.putBoolean("pause_on_unplug", on)?.apply()
    }

    fun changeSkipSilence(on: Boolean) {
        skipSilence = on
        prefs?.edit()?.putBoolean("skip_silence", on)?.apply()
    }

    fun changeStatsEnabled(on: Boolean) {
        statsEnabled = on
        prefs?.edit()?.putBoolean("stats_enabled", on)?.apply()
    }

    // ---------- Таймер сна (сам таймер работает в сервисе плеера) ----------

    fun startSleepTimer(minutes: Int) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        sleepUntil = until
        sleepAfterSong = false
        prefs?.edit()?.putLong("sleep_until", until)?.putBoolean("sleep_after_song", false)?.apply()
    }

    fun startSleepAfterSong() {
        sleepUntil = 0L
        sleepAfterSong = true
        prefs?.edit()?.putLong("sleep_until", 0L)?.putBoolean("sleep_after_song", true)?.apply()
    }

    fun cancelSleep() {
        sleepUntil = 0L
        sleepAfterSong = false
        prefs?.edit()?.putLong("sleep_until", 0L)?.putBoolean("sleep_after_song", false)?.apply()
    }

    /** Перечитывает таймер: сервис мог его выключить, когда время вышло. */
    fun refreshSleep() {
        val p = prefs ?: return
        val until = p.getLong("sleep_until", 0L)
        sleepUntil = if (until > System.currentTimeMillis()) until else 0L
        sleepAfterSong = p.getBoolean("sleep_after_song", false)
    }
}