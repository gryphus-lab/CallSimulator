package ch.gryphus.callsimulator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRouteTest {

    // Representative type codes (values are irrelevant to the pure logic; the
    // helper only checks set membership). These mirror the shape of the real
    // AudioDeviceInfo constants without depending on the stubbed framework.
    private val btSco = 7
    private val btA2dp = 8
    private val bleHeadset = 26
    private val builtinSpeaker = 2
    private val wiredHeadphones = 4

    private val bluetoothTypes = setOf(btSco, btA2dp, bleHeadset)

    @Test
    fun `bluetooth sink present is detected`() {
        assertTrue(
            AudioRoute.isBluetoothOutput(listOf(builtinSpeaker, btA2dp), bluetoothTypes)
        )
        assertTrue(AudioRoute.isBluetoothOutput(listOf(bleHeadset), bluetoothTypes))
        assertTrue(AudioRoute.isBluetoothOutput(listOf(btSco), bluetoothTypes))
    }

    @Test
    fun `no bluetooth sink falls back to speaker`() {
        assertFalse(
            AudioRoute.isBluetoothOutput(listOf(builtinSpeaker, wiredHeadphones), bluetoothTypes)
        )
    }

    @Test
    fun `empty device list is not bluetooth`() {
        assertFalse(AudioRoute.isBluetoothOutput(emptyList(), bluetoothTypes))
    }

    @Test
    fun `empty bluetooth type set is never bluetooth`() {
        assertFalse(AudioRoute.isBluetoothOutput(listOf(btA2dp, bleHeadset), emptySet()))
    }
}
