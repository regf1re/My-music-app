package com.example.musicplayer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

/**
 * Фоновый сервис воспроизведения. Благодаря ему музыка играет
 * в свёрнутом режиме и при выключенном экране, а в шторке
 * появляется уведомление с кнопками управления.
 * Здесь же эквалайзер подключается к аудио-сеансу плеера.
 */
@OptIn(UnstableApi::class)
class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var crossfade: CrossfadeController? = null
    private var stats: StatsTracker? = null
    private var sleepTimer: SleepTimer? = null
    private var exoPlayer: ExoPlayer? = null

    // Меняет поведение плеера сразу после изменения настройки (пауза при отключении наушников)
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "pause_on_unplug") {
            exoPlayer?.setHandleAudioBecomingNoisy(prefs.getBoolean("pause_on_unplug", true))
        }
        if (key == "skip_silence") {
            exoPlayer?.skipSilenceEnabled = prefs.getBoolean("skip_silence", false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Звук идёт через обработчик спектра (для визуализатора), сам звук он не меняет
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf<AudioProcessor>(SpectrumProcessor()))
                    .build()
            }
        }
        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                EqualizerManager.attach(applicationContext, audioSessionId)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // Повторная попытка подключения, если раньше не получилось
                if (playbackState == Player.STATE_READY && !EqualizerManager.available) {
                    EqualizerManager.attach(applicationContext, player.audioSessionId)
                }
            }
        })
        EqualizerManager.attach(applicationContext, player.audioSessionId)

        mediaSession = MediaSession.Builder(this, player).build()
        crossfade = CrossfadeController(this, player).also { it.attach() }
        stats = StatsTracker(this, player).also { it.attach() }
        sleepTimer = SleepTimer(this, player).also { it.attach() }
        exoPlayer = player
        val settings = getSharedPreferences("settings", Context.MODE_PRIVATE)
        player.setHandleAudioBecomingNoisy(settings.getBoolean("pause_on_unplug", true))
        player.skipSilenceEnabled = settings.getBoolean("skip_silence", false)
        settings.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        getSharedPreferences("settings", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefListener)
        sleepTimer?.release()
        sleepTimer = null
        stats?.release()
        stats = null
        crossfade?.release()
        crossfade = null
        EqualizerManager.release()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}