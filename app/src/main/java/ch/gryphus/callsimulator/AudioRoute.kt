package ch.gryphus.callsimulator

/**
 * Pure audio-route decision helper (no Android imports, fully unit-tested).
 *
 * The single decision - "is a Bluetooth audio device among the connected
 * outputs?" - is a function over device *type codes* and the set of Bluetooth
 * type codes. [InCallActivity] (which IS allowed to touch the framework)
 * resolves the live output types and the real [android.media.AudioDeviceInfo]
 * Bluetooth constants, then delegates the decision here.
 */
object AudioRoute {

    /**
     * True if any connected output device type is in [bluetoothTypes], meaning
     * the in-call control should present a Bluetooth icon rather than the
     * built-in speaker icon.
     */
    fun isBluetoothOutput(connectedOutputTypes: List<Int>, bluetoothTypes: Set<Int>): Boolean =
        connectedOutputTypes.any { it in bluetoothTypes }
}
