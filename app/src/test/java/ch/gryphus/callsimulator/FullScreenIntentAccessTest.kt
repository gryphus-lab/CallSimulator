package ch.gryphus.callsimulator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenIntentAccessTest {

    private val android13 = 33 // TIRAMISU
    private val android14 = 34 // UPSIDE_DOWN_CAKE - first FSI-gated level
    private val android15 = 35 // VANILLA_ICE_CREAM

    @Test
    fun `below API 34 never prompts regardless of grant flag`() {
        // The permission is implicit pre-14, so there is nothing to request even
        // when canUseFullScreenIntent reports false (the flag is not consulted).
        assertFalse(FullScreenIntentAccess.shouldRequestAccess(android13, canUseFullScreenIntent = false))
        assertFalse(FullScreenIntentAccess.shouldRequestAccess(android13, canUseFullScreenIntent = true))
        assertFalse(FullScreenIntentAccess.shouldRequestAccess(26, canUseFullScreenIntent = false))
    }

    @Test
    fun `API 34+ prompts only when access is not granted`() {
        assertTrue(FullScreenIntentAccess.shouldRequestAccess(android14, canUseFullScreenIntent = false))
        assertTrue(FullScreenIntentAccess.shouldRequestAccess(android15, canUseFullScreenIntent = false))
    }

    @Test
    fun `API 34+ does not prompt once access is granted`() {
        assertFalse(FullScreenIntentAccess.shouldRequestAccess(android14, canUseFullScreenIntent = true))
        assertFalse(FullScreenIntentAccess.shouldRequestAccess(android15, canUseFullScreenIntent = true))
    }

    @Test
    fun `gated SDK constant is Android 14`() {
        assertTrue(FullScreenIntentAccess.FSI_GATED_SDK == android14)
    }
}
