package com.ruben.spotitube.playback

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class DeviceStateController(
    private val context: Context,
    private val player: () -> ExoPlayer,
    private val scope: () -> CoroutineScope,
    private val isTogetherGuest: () -> Boolean,
) {
    private var pauseOnDeviceMuteEnabled = false
    private var wasAutoPausedByDeviceMute = false
    private var autoStartOnBluetoothEnabled = false
    private var bluetoothReceiverRegistered = false

    fun setPauseOnDeviceMute(enabled: Boolean) {
        pauseOnDeviceMuteEnabled = enabled
        if (!enabled) {
            wasAutoPausedByDeviceMute = false
        }
        handleDeviceMuteStateChanged()
    }

    fun setAutoStartOnBluetooth(enabled: Boolean) {
        autoStartOnBluetoothEnabled = enabled
        if (enabled) {
            registerBluetoothReceiver()
        } else {
            unregisterBluetoothReceiver()
        }
    }

    fun onPlaybackIdleOrEnded() {
        wasAutoPausedByDeviceMute = false
    }

    fun isDeviceMutedNow(): Boolean {
        val p = player()
        return p.isDeviceMuted || p.deviceVolume <= 0
    }

    fun handleDeviceMuteStateChanged() {
        val p = player()
        if (!pauseOnDeviceMuteEnabled || isTogetherGuest()) {
            wasAutoPausedByDeviceMute = false
            return
        }

        if (isDeviceMutedNow()) {
            val canPauseNow =
                p.currentMediaItem != null &&
                    p.playWhenReady &&
                    p.playbackState != STATE_IDLE &&
                    p.playbackState != Player.STATE_ENDED

            if (canPauseNow) {
                p.pause()
                wasAutoPausedByDeviceMute = true
            }
            return
        }

        if (!wasAutoPausedByDeviceMute) return

        wasAutoPausedByDeviceMute = false
        val canResumeNow =
            p.currentMediaItem != null &&
                p.playbackState != STATE_IDLE &&
                p.playbackState != Player.STATE_ENDED
        if (canResumeNow) {
            p.play()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
            if (!autoStartOnBluetoothEnabled) return

            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return

            val isAudioDevice = try {
                val majorClass = device.bluetoothClass?.majorDeviceClass
                majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                    majorClass == BluetoothClass.Device.Major.WEARABLE
            } catch (_: SecurityException) {
                true
            }

            if (!isAudioDevice) return

            scope().launch {
                delay(1500.milliseconds)
                handleBluetoothAutoStart()
            }
        }
    }

    private fun handleBluetoothAutoStart() {
        val p = player()
        if (isTogetherGuest()) return

        if (p.currentMediaItem != null &&
            p.playbackState != STATE_IDLE &&
            p.playbackState != Player.STATE_ENDED
        ) {
            if (!p.playWhenReady) {
                p.play()
            }
            return
        }

        if (p.mediaItemCount > 0) {
            p.prepare()
            p.play()
        }
    }

    @Suppress("DEPRECATION")
    fun registerBluetoothReceiver() {
        if (bluetoothReceiverRegistered) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return

        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(bluetoothReceiver, filter)
        }
        bluetoothReceiverRegistered = true
    }

    fun unregisterBluetoothReceiver() {
        if (!bluetoothReceiverRegistered) return
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {}
        bluetoothReceiverRegistered = false
    }
}
