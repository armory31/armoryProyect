package com.ruben.spotitube.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.media3.exoplayer.ExoPlayer
import com.ruben.spotitube.constants.EqualizerBandLevelsMbKey
import com.ruben.spotitube.constants.EqualizerBassBoostEnabledKey
import com.ruben.spotitube.constants.EqualizerBassBoostStrengthKey
import com.ruben.spotitube.constants.EqualizerEnabledKey
import com.ruben.spotitube.constants.EqualizerOutputGainEnabledKey
import com.ruben.spotitube.constants.EqualizerOutputGainMbKey
import com.ruben.spotitube.constants.EqualizerSelectedProfileIdKey
import com.ruben.spotitube.constants.EqualizerVirtualizerEnabledKey
import com.ruben.spotitube.constants.EqualizerVirtualizerStrengthKey
import com.ruben.spotitube.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AudioEffectsController(
    private val context: Context,
    private val player: () -> ExoPlayer,
    private val scope: () -> CoroutineScope,
    private val ioScope: () -> CoroutineScope,
) {
    private var isAudioEffectSessionOpened = false
    private var openedAudioSessionId: Int? = null
    val eqCapabilities = MutableStateFlow<EqCapabilities?>(null)
    private val desiredEqSettings =
        MutableStateFlow(
            EqSettings(
                enabled = false,
                bandLevelsMb = emptyList(),
                outputGainEnabled = false,
                outputGainMb = 0,
                bassBoostEnabled = false,
                bassBoostStrength = 0,
                virtualizerEnabled = false,
                virtualizerStrength = 0,
            ),
        )

    private var audioEffectsSessionId: Int? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    fun onSettingsChanged(settings: EqSettings) {
        desiredEqSettings.value = settings
        applyEqSettingsToEffects(settings)
    }

    fun onAudioSessionIdChanged(newSessionId: Int) {
        val oldSessionId = openedAudioSessionId
        if (isAudioEffectSessionOpened && newSessionId > 0 && oldSessionId != null && oldSessionId > 0 && oldSessionId != newSessionId) {
            context.sendBroadcast(
                Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, oldSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                },
            )
            openedAudioSessionId = newSessionId
            ensureAudioEffects(newSessionId)
            context.sendBroadcast(
                Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, newSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                },
            )
        }
    }

    private fun decodeBandLevelsMb(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { EqualizerJson.json.decodeFromString<List<Int>>(raw) }.getOrNull() ?: emptyList()
    }

    private fun encodeBandLevelsMb(levelsMb: List<Int>): String {
        return runCatching { EqualizerJson.json.encodeToString(levelsMb) }.getOrNull().orEmpty()
    }

    fun readEqSettingsFromPrefs(prefs: Preferences): EqSettings {
        val levels = decodeBandLevelsMb(prefs[EqualizerBandLevelsMbKey])
        return EqSettings(
            enabled = prefs[EqualizerEnabledKey] ?: false,
            bandLevelsMb = levels,
            outputGainEnabled = prefs[EqualizerOutputGainEnabledKey] ?: false,
            outputGainMb = prefs[EqualizerOutputGainMbKey] ?: 0,
            bassBoostEnabled = prefs[EqualizerBassBoostEnabledKey] ?: false,
            bassBoostStrength = (prefs[EqualizerBassBoostStrengthKey] ?: 0).coerceIn(0, 1000),
            virtualizerEnabled = prefs[EqualizerVirtualizerEnabledKey] ?: false,
            virtualizerStrength = (prefs[EqualizerVirtualizerStrengthKey] ?: 0).coerceIn(0, 1000),
        )
    }

    fun applyEqFlatPreset() {
        ioScope().launch {
            val caps = eqCapabilities.value
            val bandCount = caps?.bandCount ?: runCatching { equalizer?.numberOfBands?.toInt() }.getOrNull() ?: 0
            val encoded = encodeBandLevelsMb(List(bandCount.coerceAtLeast(0)) { 0 })
            context.dataStore.edit { prefs ->
                prefs[EqualizerEnabledKey] = true
                prefs[EqualizerBandLevelsMbKey] = encoded
                prefs[EqualizerSelectedProfileIdKey] = "flat"
            }
        }
    }

    fun applySystemEqPreset(presetIndex: Int) {
        scope().launch {
            ensureAudioEffects(player().audioSessionId)
            val eq = equalizer ?: return@launch
            val maxPreset = runCatching { eq.numberOfPresets.toInt() }.getOrNull() ?: 0
            if (presetIndex !in 0 until maxPreset) return@launch

            runCatching { eq.usePreset(presetIndex.toShort()) }.getOrNull() ?: return@launch

            val bandCount = runCatching { eq.numberOfBands.toInt() }.getOrNull() ?: 0
            val levels =
                (0 until bandCount).map { band ->
                    runCatching { eq.getBandLevel(band.toShort()).toInt() }.getOrNull() ?: 0
                }

            val encoded = encodeBandLevelsMb(levels)
            if (encoded.isBlank()) return@launch

            ioScope().launch {
                context.dataStore.edit { prefs ->
                    prefs[EqualizerEnabledKey] = true
                    prefs[EqualizerBandLevelsMbKey] = encoded
                    prefs[EqualizerSelectedProfileIdKey] = "system:$presetIndex"
                }
            }
        }
    }

    private fun resampleLevelsByIndex(levelsMb: List<Int>, targetCount: Int): List<Int> {
        if (targetCount <= 0) return emptyList()
        if (levelsMb.isEmpty()) return List(targetCount) { 0 }
        if (levelsMb.size == targetCount) return levelsMb
        if (targetCount == 1) return listOf(levelsMb.sum() / levelsMb.size)

        val lastIndex = levelsMb.lastIndex.toFloat().coerceAtLeast(1f)
        return List(targetCount) { i ->
            val pos = i.toFloat() * lastIndex / (targetCount - 1).toFloat()
            val lo = kotlin.math.floor(pos).toInt().coerceIn(0, levelsMb.lastIndex)
            val hi = kotlin.math.ceil(pos).toInt().coerceIn(0, levelsMb.lastIndex)
            val t = (pos - lo.toFloat()).coerceIn(0f, 1f)
            val a = levelsMb[lo]
            val b = levelsMb[hi]
            (a + ((b - a) * t)).toInt()
        }
    }

    private fun updateEqCapabilitiesFromEffect(eq: Equalizer) {
        val bandCount = eq.numberOfBands.toInt().coerceAtLeast(0)
        val range = runCatching { eq.bandLevelRange }.getOrNull()
        val minMb = range?.getOrNull(0)?.toInt() ?: -1500
        val maxMb = range?.getOrNull(1)?.toInt() ?: 1500
        val center =
            (0 until bandCount).map { band ->
                (runCatching { eq.getCenterFreq(band.toShort()) }.getOrNull() ?: 0) / 1000
            }
        val presets =
            (0 until eq.numberOfPresets.toInt()).map { idx ->
                runCatching { eq.getPresetName(idx.toShort()).toString() }.getOrNull() ?: "Preset ${idx + 1}"
            }
        eqCapabilities.value =
            EqCapabilities(
                bandCount = bandCount,
                minBandLevelMb = minMb,
                maxBandLevelMb = maxMb,
                centerFreqHz = center,
                systemPresets = presets,
            )
    }

    fun releaseAudioEffects() {
        audioEffectsSessionId = null
        try {
            equalizer?.release()
        } catch (_: Exception) {
        }
        try {
            bassBoost?.release()
        } catch (_: Exception) {
        }
        try {
            virtualizer?.release()
        } catch (_: Exception) {
        }
        try {
            loudnessEnhancer?.release()
        } catch (_: Exception) {
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        eqCapabilities.value = null
    }

    private fun ensureAudioEffects(sessionId: Int) {
        if (sessionId <= 0) return
        if (audioEffectsSessionId == sessionId && equalizer != null) return

        releaseAudioEffects()
        audioEffectsSessionId = sessionId

        equalizer = runCatching { Equalizer(0, sessionId) }.getOrNull()
        bassBoost = runCatching { BassBoost(0, sessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, sessionId) }.getOrNull()
        loudnessEnhancer = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()

        equalizer?.let(::updateEqCapabilitiesFromEffect)
        applyEqSettingsToEffects(desiredEqSettings.value)
    }

    private fun applyEqSettingsToEffects(settings: EqSettings) {
        val eq = equalizer ?: return
        val caps = eqCapabilities.value
        val bandCount = caps?.bandCount ?: eq.numberOfBands.toInt()
        val minMb = caps?.minBandLevelMb ?: runCatching { eq.bandLevelRange.getOrNull(0)?.toInt() }.getOrNull() ?: -1500
        val maxMb = caps?.maxBandLevelMb ?: runCatching { eq.bandLevelRange.getOrNull(1)?.toInt() }.getOrNull() ?: 1500

        val levels = resampleLevelsByIndex(settings.bandLevelsMb, bandCount)
        runCatching { eq.enabled = settings.enabled }

        for (band in 0 until bandCount) {
            val levelMb = levels.getOrNull(band)?.coerceIn(minMb, maxMb) ?: 0
            runCatching { eq.setBandLevel(band.toShort(), levelMb.toShort()) }
        }

        bassBoost?.let { bb ->
            runCatching { bb.enabled = settings.bassBoostEnabled }
            runCatching { bb.setStrength(settings.bassBoostStrength.toShort()) }
        }

        virtualizer?.let { v ->
            runCatching { v.enabled = settings.virtualizerEnabled }
            runCatching { v.setStrength(settings.virtualizerStrength.toShort()) }
        }

        loudnessEnhancer?.let { le ->
            val gainMb = if (settings.outputGainEnabled) settings.outputGainMb.coerceIn(-1500, 1500) else 0
            runCatching { le.setTargetGain(gainMb) }
            runCatching { le.enabled = settings.outputGainEnabled }
        }
    }

    fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        val sessionId = player().audioSessionId
        if (sessionId <= 0) return
        isAudioEffectSessionOpened = true
        openedAudioSessionId = sessionId
        ensureAudioEffects(sessionId)
        context.sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            },
        )
    }

    fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        val sessionId = openedAudioSessionId ?: player().audioSessionId
        openedAudioSessionId = null
        releaseAudioEffects()
        if (sessionId <= 0) return
        context.sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            },
        )
    }
}
