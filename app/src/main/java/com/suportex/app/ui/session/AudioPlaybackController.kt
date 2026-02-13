package com.suportex.app.ui.session

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class AudioPlaybackController(context: Context) {
    private val exoPlayer = ExoPlayer.Builder(context).build()

    var activeAudioUrl: String? by mutableStateOf(null)
        private set

    var isPlaying: Boolean by mutableStateOf(false)
        private set

    var positionMs: Long by mutableLongStateOf(0L)
        private set

    var durationMs: Long by mutableLongStateOf(0L)
        private set

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            if (playbackState == Player.STATE_ENDED) {
                positionMs = 0L
                isPlaying = false
                exoPlayer.seekTo(0L)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            positionMs = 0L
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
        }
    }

    init {
        exoPlayer.addListener(listener)
    }

    fun toggle(url: String) {
        val shouldSwitchTrack = activeAudioUrl != url
        if (shouldSwitchTrack) {
            activeAudioUrl = url
            positionMs = 0L
            durationMs = 0L
            exoPlayer.stop()
            exoPlayer.setMediaItem(MediaItem.fromUri(url))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            return
        }

        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        }
    }

    fun syncProgress() {
        if (activeAudioUrl == null) return
        positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        durationMs = exoPlayer.duration.coerceAtLeast(0L)
    }

    fun stop() {
        exoPlayer.stop()
        activeAudioUrl = null
        isPlaying = false
        positionMs = 0L
        durationMs = 0L
    }

    fun release() {
        exoPlayer.removeListener(listener)
        exoPlayer.release()
    }
}
