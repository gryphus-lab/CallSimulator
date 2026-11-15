package ch.gryphus.callsimulator

import android.media.AudioDeviceInfo

/**
 * Framework-light audio-route helper. The single *decision* - "is a Bluetooth
 * audio device the one we should represent on the speaker button?" - is pulled
 * out as a pure function over a list of device *types* so it can be unit-tested
 * without an Android AudioManager. [InCallActivity] feeds it the live device
 * type list from `AudioManager.getDevices(GET_DEVICES_OUTPUTS)`.
 */
object AudioRoute {

    /** Output device types that represent a Bluetooth audio sink. */
    private val BLUETOOTH_OUTPUT_TYPES = buildSet {
        add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)   // hands-free / headset profile
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)  // stereo media profile
        // BLE audio sinks (API 31+ constants; guarded so older devices still compile/run).
        runCatching { add(AudioDeviceInfo.TYPE_BLE_HEADSET) }
        runCatching { add(AudioDeviceInfo.TYPE_BLE_SPEAKER) }
        runCatching { add(AudioDeviceInfo.TYPE_BLE_BROADCAST) }
    }

    /**
     * True if any connected output device is a Bluetooth audio sink, meaning the
     * in-call control should present a Bluetooth icon rather than the built-in
     * speaker icon.
     */
    fun isBluetoothOutput(connectedOutputTypes: List<Int>): Boolean =
        connectedOutputTypes.any { it in BLUETOOTH_OUTPUT_TYPES }
}
