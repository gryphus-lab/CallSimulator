package ch.gryphus.callsimulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerDetailsTest {

    // --- resolveName / resolveNumber ---------------------------------------

    @Test
    fun resolveName_usesInputWhenPresent() {
        assertEquals("Alice", CallerDetails.resolveName("Alice", "Mom"))
    }

    @Test
    fun resolveName_trimsWhitespace() {
        assertEquals("Alice", CallerDetails.resolveName("  Alice  ", "Mom"))
    }

    @Test
    fun resolveName_fallsBackOnNullOrBlank() {
        assertEquals("Mom", CallerDetails.resolveName(null, "Mom"))
        assertEquals("Mom", CallerDetails.resolveName("", "Mom"))
        assertEquals("Mom", CallerDetails.resolveName("   ", "Mom"))
    }

    @Test
    fun resolveNumber_usesInputWhenPresentElseDefault() {
        assertEquals("+1 555-0100", CallerDetails.resolveNumber(" +1 555-0100 ", "+1 555-0123"))
        assertEquals("+1 555-0123", CallerDetails.resolveNumber(null, "+1 555-0123"))
        assertEquals("+1 555-0123", CallerDetails.resolveNumber("  ", "+1 555-0123"))
    }

    // --- isUsableContactName / hasPhoneNumber / normalizePhoto -------------

    @Test
    fun isUsableContactName_detectsBlankOrNull() {
        assertTrue(CallerDetails.isUsableContactName("Bob"))
        assertFalse(CallerDetails.isUsableContactName(null))
        assertFalse(CallerDetails.isUsableContactName(""))
        assertFalse(CallerDetails.isUsableContactName("   "))
    }

    @Test
    fun hasPhoneNumber_truthForPositiveFlagOnly() {
        assertTrue(CallerDetails.hasPhoneNumber(1))
        assertTrue(CallerDetails.hasPhoneNumber(5))
        assertFalse(CallerDetails.hasPhoneNumber(0))
        assertFalse(CallerDetails.hasPhoneNumber(null))
    }

    @Test
    fun normalizePhoto_blankBecomesNull() {
        assertEquals("content://photo/1", CallerDetails.normalizePhoto("content://photo/1"))
        assertEquals("content://photo/1", CallerDetails.normalizePhoto("  content://photo/1 "))
        assertNull(CallerDetails.normalizePhoto(null))
        assertNull(CallerDetails.normalizePhoto(""))
        assertNull(CallerDetails.normalizePhoto("   "))
    }

    // --- fieldsFromContact -------------------------------------------------

    @Test
    fun fieldsFromContact_fullContact() {
        val f = CallerDetails.fieldsFromContact(
            displayName = "  Carol ",
            hasPhoneFlag = 1,
            primaryNumber = " 555-1 ",
            rawPhotoUri = " content://p/9 ",
        )
        assertEquals("Carol", f.name)
        assertEquals("555-1", f.number)
        assertEquals("content://p/9", f.photo)
    }

    @Test
    fun fieldsFromContact_noPhoneKeepsNumberNull() {
        val f = CallerDetails.fieldsFromContact(
            displayName = "Dan",
            hasPhoneFlag = 0,
            primaryNumber = "should-be-ignored",
            rawPhotoUri = null,
        )
        assertEquals("Dan", f.name)
        assertNull(f.number)
        assertNull(f.photo)
    }

    @Test
    fun fieldsFromContact_blankNameKeepsNameNull() {
        val f = CallerDetails.fieldsFromContact(
            displayName = "   ",
            hasPhoneFlag = 1,
            primaryNumber = "555-2",
            rawPhotoUri = "",
        )
        assertNull(f.name)
        assertEquals("555-2", f.number)
        assertNull(f.photo)
    }

    @Test
    fun fieldsFromContact_hasPhoneButBlankNumberYieldsNull() {
        val f = CallerDetails.fieldsFromContact(
            displayName = "Eve",
            hasPhoneFlag = 1,
            primaryNumber = "   ",
            rawPhotoUri = "content://p/1",
        )
        assertEquals("Eve", f.name)
        assertNull(f.number)
        assertEquals("content://p/1", f.photo)
    }

    // --- CallerInfo data class --------------------------------------------

    @Test
    fun callerInfo_defaultsPhotoToNull() {
        val info = CallerInfo(name = "Mom", number = "+1 555-0123")
        assertNull(info.photo)
        assertEquals("Mom", info.name)
        assertEquals("+1 555-0123", info.number)
    }
}
