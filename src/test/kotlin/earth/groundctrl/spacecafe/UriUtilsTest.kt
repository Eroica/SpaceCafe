package earth.groundctrl.spacecafe

import kotlin.test.Test
import kotlin.test.assertEquals

internal class UriUtilsTest {
    @Test
    fun `return true for the emtpy path`() {
        assertEquals(true, "".isValidPath())
    }

    @Test
    fun `return true for valid paths`() {
        assertEquals(
            true,
            listOf("/", "/file", "/./", "/.", "/dir/", "/dir/../").all { it.isValidPath() }
        )
    }

    @Test
    fun `return false for invalid paths`() {
        assertEquals(
            false,
            listOf("/../", "/..", "/dir/../..", "/dir/../..", "/./../", "/./dir/.././../").all { it.isValidPath() }
        )
    }
}
