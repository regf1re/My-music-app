package com.example.musicplayer

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Таймер сна в сервисе плеера: останавливает музыку через заданное время
 * (последние 10 секунд громкость плавно затихает) или после текущей песни.
 */
class SleepTimer(context: Context, private val player: Player) {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private var fadeActive = false
    private var released = false

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Песня закончилась сама (или началась заново в режиме повтора)
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            ) {
                stopAfterSong()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) stopAfterSong()
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
            handler.postDelayed(this, if (fadeActive) 250L else 1000L)
        }
    }

    fun attach() {
        player.addListener(listener)
        handler.post(tick)
    }

    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        player.removeListener(listener)
    }

    private fun stopAfterSong() {
        if (prefs.getBoolean("sleep_after_song", false)) {
            prefs.edit().putBoolean("sleep_after_song", false).apply()
            player.pause()
        }
    }

    private fun step() {
        val until = prefs.getLong("sleep_until", 0L)
        val userVolume = prefs.getFloat("volume", 1f)
        if (until <= 0L) {
            if (fadeActive) {
                fadeActive = false
                player.volume = userVolume
            }
            return
        }
        val left = until - System.currentTimeMillis()
        if (left <= 0L) {
            prefs.edit().putLong("sleep_until", 0L).apply()
            player.pause()
            player.volume = userVolume
            fadeActive = false
        } else if (left <= 10_000L && player.isPlaying) {
            fadeActive = true
            player.volume = userVolume * (left / 10_000f)
        } else if (fadeActive) {
            fadeActive = false
            player.volume = userVolume
        }
    }
}
