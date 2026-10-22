package com.example.musicplayer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.media.audiofx.AudioEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Плавный переход (кроссфейд) между песнями.
 *
 * Когда до конца песни остаётся заданное время, основной плеер сразу переходит к следующей песне
 * (она нарастает от тишины), а «тень» — второй маленький плеер — доигрывает конец прошлой песни
 * и затухает. В итоге конец одной песни перекрывается началом следующей.
 */
class CrossfadeController(
    private val context: Context,
    private val player: ExoPlayer
) {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private var ghost: ExoPlayer? = null
    private var ghostEffects: List<AudioEffect> = emptyList()
    private var ghostMediaId: String? = null

    private var fading = false
    private var selfTransition = false
    private var fadeStart = 0L
    private var fadeDuration = 1000L
    private var targetVolume = 1f
    private var released = false

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (selfTransition) {
                selfTransition = false
                return
            }
            // Песню сменили не мы (пользователь нажал «дальше» или она закончилась сама)
            if (fading) finishFade() else releaseGhost()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (fading) ghost?.playWhenReady = playWhenReady
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            // Пользователь перемотал песню: подготовленную «тень» делаем заново
            if (reason == Player.DISCONTINUITY_REASON_SEEK && !fading) releaseGhost()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (released) return
            try {
                check()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            handler.postDelayed(this, if (player.isPlaying) 250L else 700L)
        }
    }

    private val fadeStep = object : Runnable {
        override fun run() {
            if (!fading) return
            val t = ((SystemClock.elapsedRealtime() - fadeStart).toFloat() / fadeDuration).coerceIn(0f, 1f)
            val angle = t * (PI / 2).toFloat()
            player.volume = targetVolume * sin(angle)
            ghost?.volume = targetVolume * cos(angle)
            if (t >= 1f) finishFade() else handler.postDelayed(this, 40L)
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
        if (fading) {
            fading = false
            player.volume = prefs.getFloat("volume", targetVolume)
        }
        releaseGhost()
    }

    private fun check() {
        if (fading) return
        if (!prefs.getBoolean("crossfade_enabled", false)) {
            releaseGhost()
            return
        }
        // Таймер сна «после песни»: песня должна доиграть до конца, без перекрытия
        if (prefs.getBoolean("sleep_after_song", false)) {
            releaseGhost()
            return
        }
        val d = prefs.getInt("crossfade_seconds", 4) * 1000L
        if (!player.isPlaying) return
        if (player.repeatMode == Player.REPEAT_MODE_ONE || !player.hasNextMediaItem()) return
        val duration = player.duration
        // При скорости не 1× песня идёт быстрее или медленнее: переводим секунды в время песни
        val speed = player.playbackParameters.speed
        val dm = (d * speed).toLong()
        if (duration == C.TIME_UNSET || duration < dm * 3) return

        if (prefs.getBoolean("crossfade_skip_album", false) && sameAlbum()) {
            releaseGhost()
            return
        }

        val remaining = duration - player.currentPosition
        if (ghost == null && remaining <= dm + 3000L) prepareGhost(duration, dm)
        if (ghost != null && remaining <= dm) startCrossfade(d)
    }

    private fun sameAlbum(): Boolean {
        val next = player.nextMediaItemIndex
        if (next == C.INDEX_UNSET) return false
        val current = player.currentMediaItem?.mediaMetadata?.albumTitle?.toString()
        val following = player.getMediaItemAt(next).mediaMetadata.albumTitle?.toString()
        return !current.isNullOrBlank() && current == following
    }

    private fun prepareGhost(duration: Long, d: Long) {
        val item = player.currentMediaItem ?: return
        val g = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false
            )
            .build()
        g.volume = 0f
        g.setMediaItem(item)
        g.prepare()
        g.seekTo((duration - d - 600L).coerceAtLeast(0L))
        g.playWhenReady = false
        ghost = g
        ghostMediaId = item.mediaId
        ghostEffects = EqualizerManager.createMirrorEffects(g.audioSessionId)
    }

    private fun startCrossfade(d: Long) {
        val g = ghost ?: return
        if (ghostMediaId != player.currentMediaItem?.mediaId) {
            releaseGhost()
            return
        }
        if (g.playbackState != Player.STATE_READY) return

        targetVolume = if (player.volume > 0f) player.volume else prefs.getFloat("volume", 1f)
        g.seekTo(player.currentPosition + 40L)
        g.volume = targetVolume
        g.setPlaybackSpeed(player.playbackParameters.speed)
        g.playWhenReady = true

        fading = true
        fadeStart = SystemClock.elapsedRealtime()
        fadeDuration = d.coerceAtLeast(500L)
        player.volume = 0f
        selfTransition = true
        player.seekToNextMediaItem()
        handler.post(fadeStep)
    }

    private fun finishFade() {
        fading = false
        handler.removeCallbacks(fadeStep)
        player.volume = prefs.getFloat("volume", targetVolume)
        releaseGhost()
    }

    private fun releaseGhost() {
        ghostEffects.forEach {
            try {
                it.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        ghostEffects = emptyList()
        ghost?.release()
        ghost = null
        ghostMediaId = null
    }
}