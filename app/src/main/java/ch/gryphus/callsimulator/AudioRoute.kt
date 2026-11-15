package ch.gryphus.callsimulator

import android.media.AudioDeviceInfo
import android.os.Build

/**
 * Framework-light audio-route helper. The single *decision* - "is a Bluetooth
 * audio device among the connected outputs?" - is a pure function over device
 * *type codes* and an explicit set of Bluetooth type codes, so it can be
 * unit-tested without an Android AudioManager and without relying on the
 * framework constants (which stub to 0 under JVM unit tests).
 *
 * [bluetoothOutputTypes] resolves the real [AudioDeviceInfo] constants on a
 * device; tests pass their own set.
 */
object AudioRoute {

    /**
     * The set of output device type codes that represent a Bluetooth audio sink
     * on this device. Resolved from the live [AudioDeviceInfo] constants (BLE
     * types only exist on API 31+, so they are added conditionally).
     */
    fun bluetoothOutputTypes(): Set<Int> = buildSet {
        add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)   // hands-free / headset profile
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)  // stereo media profile
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(AudioDeviceInfo.TYPE_BLE_BROADCAST)
        }
    }

    /**
     * True if any connected output device type is in [bluetoothTypes], meaning
     * the in-call control should present a Bluetooth icon rather than the
     * built-in speaker icon.
     */
    fun isBluetoothOutput(connectedOutputTypes: List<Int>, bluetoothTypes: Set<Int>): Boolean =
        connectedOutputTypes.any { it in bluetoothTypes }
}
