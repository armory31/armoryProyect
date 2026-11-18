package com.ruben.spotitube.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow

class AudioFocusController(
    context: Context,
    private val player: () -> ExoPlayer,
    private val audioFocusVolumeFactor: MutableStateFlow<Float>,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var lastAudioFocusState = AudioManager.AUDIOFOCUS_NONE
    private var wasPlayingBeforeAudioFocusLoss = false
    private var hasAudioFocus = false

    fun setupAudioFocusRequest() {
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { focusChange ->
                handleAudioFocusChange(focusChange)
            }
            .setAcceptsDelayedFocusGain(true)
            .build()
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        val p = player()
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                audioFocusVolumeFactor.value = 1f

                if (wasPlayingBeforeAudioFocusLoss) {
                    p.play()
                    wasPlayingBeforeAudioFocusLoss = false
                }

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                audioFocusVolumeFactor.value = 1f
                wasPlayingBeforeAudioFocusLoss = false

                if (p.isPlaying) {
                    p.pause()
                }

                abandonAudioFocus()

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                audioFocusVolumeFactor.value = 1f
                wasPlayingBeforeAudioFocusLoss = p.isPlaying

                if (p.isPlaying) {
                    p.pause()
                }

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = p.isPlaying
                audioFocusVolumeFactor.value = 0.2f
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {
                hasAudioFocus = true
                audioFocusVolumeFactor.value = 1f

                if (wasPlayingBeforeAudioFocusLoss) {
                    p.play()
                    wasPlayingBeforeAudioFocusLoss = false
                }

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                hasAudioFocus = true
                audioFocusVolumeFactor.value = 1f
                lastAudioFocusState = focusChange
            }
        }
    }

    fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        audioFocusRequest?.let { request ->
            val result = audioManager.requestAudioFocus(request)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            return hasAudioFocus
        }
        return false
    }

    fun abandonAudioFocus() {
        if (hasAudioFocus) {
            audioFocusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
                hasAudioFocus = false
            }
        }
    }

    fun hasAudioFocusForPlayback(): Boolean {
        return hasAudioFocus
    }
}
