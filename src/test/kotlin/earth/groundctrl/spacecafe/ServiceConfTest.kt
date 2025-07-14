package earth.groundctrl.spacecafe

import earth.groundctrl.spacecafe.TestData.resPathStr
import org.junit.jupiter.api.assertDoesNotThrow
import java.nio.file.FileSystems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class ServiceConfTest {
    @Test
    fun `test load`() {
        assertDoesNotThrow {
            ServiceConf.load(getPath("/spacecafe.conf").toFile())
        }
    }

    @Test
    fun `getDirectoryListing should resolve directory listing using vhost conf if no directory override`() {
        val vh = TestData.conf.virtualHosts.first()
        assertEquals(vh.getDirectoryListing(getPath("/dir")), vh.directoryListing)
    }

    @Test
    fun `getDirectoryListing should resolve directory listing using directory override`() {
        val vh = TestData.conf.virtualHosts.first().copy(
            directoryListing = false,
            directories = listOf(
                Directory(
                    getPath("/dir").toString(),
                    directoryListing = true,
                    allowCgi = null,
                    cache = null
                )
            )
        )
        assertEquals(true, vh.getDirectoryListing(getPath("/dir")))
    }

    @Test
    fun `getDirectoryListing should ignore non matching directories resolving directory listing`() {
        val vh = TestData.conf.virtualHosts.first().copy(
            directoryListing = false,
            directories = listOf(
                Directory(
                    getPath("no-match").toString(),
                    directoryListing = true,
                    allowCgi = null,
                    cache = null
                )
            )
        )
        assertEquals(false, vh.getDirectoryListing(getPath("/dir")))
    }

    @Test
    fun `getCache should resolve cache on a directory`() {
        val vh = TestData.conf.virtualHosts.first().copy(
            directories = listOf(
                Directory(
                    getPath("/dir").toString(),
                    null,
                    null,
                    cache = 1234
                )
            )
        )
        assertEquals(1234, vh.getCache(getPath("/dir")))
    }

    @Test
    fun `getCache should resolve cache on a file`() {
        val vh = TestData.conf.virtualHosts.first().copy(
            directories = listOf(
                Directory(
                    getPath("/dir").toString(),
                    null,
                    null,
                    cache = 1234
                )
            )
        )
        assertEquals(1234, vh.getCache(getPath("/dir/index.gmi")))
    }

    @Test
    fun `getCache should resolve cache at the right level`() {
        val vh = TestData.conf.virtualHosts.first().copy(
            directories = listOf(
                Directory(
                    getPath("/dir").toString(),
                    null,
                    null,
                    cache = 4321
                ),
                Directory(
                    getPath("/dir/sub").toString(),
                    null,
                    null,
                    cache = 1234
                )
            )
        )
        assertEquals(1234, vh.getCache(getPath("/dir/sub/index.gmi")))
    }

    @Test
    fun `getCgi should return None as allow CGI is off by default`() {
        val vh = TestData.conf.virtualHosts.first()
        assertEquals(null, vh.getCgi(getPath("/dir/cgi")))
    }

    @Test
    fun `getCgi should set allow CGI via directory override`() {
        val vhosts = listOf(true, false).map {
            TestData.conf.virtualHosts.first().copy(
                directories = listOf(
                    Directory(
                        getPath("/dir").toString(),
                        directoryListing = null,
                        allowCgi = it,
                        cache = null
                    )
                )
            )
        }

        assertNotNull(vhosts[0].getCgi(getPath("/dir/cgi")))
        assertNull(vhosts[1].getCgi(getPath("/dir/cgi")))
    }

    @Test
    fun `getCgi should return the CGI path minus path info`() {
        val vh = TestData.conf.virtualHosts.first().copy(
            directories = listOf(
                Directory(
                    getPath("/dir").toString(),
                    directoryListing = null,
                    allowCgi = true,
                    cache = null
                )
            )
        )

        assertEquals(getPath("/dir/cgi"), vh.getCgi(getPath("/dir/cgi/path/info")))
    }

    @Test
    fun `getCgi should not return the CGI path if is exactly the CGI dir`() {
        val vh = TestData.conf.virtualHosts.first().copy(
            directories = listOf(
                Directory(
                    getPath("dir").toString(),
                    directoryListing = null,
                    allowCgi = true,
                    cache = null
                )
            )
        )
        assertNull(vh.getCgi(getPath("/dir")))
        assertNull(vh.getCgi(getPath("/dir/")))
    }

    @Test
    fun `getCgi should not return the CGI path if allow CGI is false`() {
        val vh = TestData.conf.virtualHosts.first().copy(
            directories = listOf(
                Directory(
                    getPath("dir").toString(),
                    directoryListing = null,
                    allowCgi = false,
                    cache = null
                )
            )
        )
        assertNull(vh.getCgi(getPath("/dir/cgi")))
        assertNull(vh.getCgi(getPath("/dir/cgi/with/path")))
    }

    private fun getPath(value: String) = FileSystems.getDefault().getPath(resPathStr, value)
}
