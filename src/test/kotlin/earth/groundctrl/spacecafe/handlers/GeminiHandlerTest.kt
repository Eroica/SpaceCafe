package earth.groundctrl.spacecafe.handlers

import earth.groundctrl.spacecafe.*
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

internal class GeminiHandlerTest {
    val handler = GeminiHandler(TestData.conf)
    val handlerDir = GeminiHandler(TestData.dirConf)
    val handlerUserDir = GeminiHandler(TestData.confUserDir)
    val handlerCgi = GeminiHandler(TestData.cgiConf)
    val handlerCache = GeminiHandler(TestData.cacheConf)

    fun handleWith(geminiHandler: GeminiHandler, req: String, remoteAddr: String = "127.0.0.1"): Response {
        return geminiHandler.handle(req, URI.create(req), remoteAddr)
    }

    @Test
    fun `handle should handle host case insensitive`() {
        assertIs<Success>(handleWith(handler, "gemini://LOCALHOST/"))
    }

    @Test
    fun `handle should return proxy request refused when the vhost is not found`() {
        assertIs<ProxyRequestRefused>(handleWith(handler, "gemini://otherhost/"))
    }

    @Test
    fun `handle should return bad request when user info is present`() {
        assertIs<BadRequest>(handleWith(handler, "gemini://user@localhost/"))
    }

    @Test
    fun `handle should return bad request when the path is out of root dir`() {
        assertIs<BadRequest>(handleWith(handler, "gemini://localhost/../../"))
    }

    @Test
    fun `handle should redirect to normalize the URL`() {
        assertIs<PermanentRedirect>(handleWith(handler, "gemini://localhost/./"))
    }

    @Test
    fun `handle should return not found if the path doesn't exist`() {
        assertIs<NotFound>(handleWith(handler, "gemini://localhost/doesnotexist"))
    }

    @Test
    fun `handle should return not found if a dot file`() {
        assertIs<NotFound>(handleWith(handler, "gemini://localhost/.dotfile"))
    }

    @Test
    fun `handle should return success on reading file`() {
        val response = handleWith(handler, "gemini://localhost/index.gmi")
        assertIs<Success>(response)
        assertEquals("text/gemini", response.meta)
        assertNotNull(response.bodyPath)
        assertEquals(25, response.bodySize)
    }

    @Test
    fun `handle should redirect and normalize request on a directory`() {
        assertIs<PermanentRedirect>(handleWith(handler, "gemini://localhost/dir"))
    }

    @Test
    fun `handle should return an existing index file when requesting a directory`() {
        val response = handleWith(handler, "gemini://localhost/")
        assertIs<Success>(response)
        assertEquals("text/gemini", response.meta)
        assertNotNull(response.bodyPath)
        assertEquals(25, response.bodySize)
    }

    @Test
    fun `handle should include gemini params for gemini MIME type`() {
        val response = handleWith(
            GeminiHandler(
                TestData.conf.copy(
                    virtualHosts = listOf(
                        TestData.conf.virtualHosts[0].copy(geminiParams = "test")
                    )
                )
            ), "gemini://localhost/"
        )
        assertIs<Success>(response)
        assertEquals("text/gemini; test", response.meta)
        assertNotNull(response.bodyPath)
        assertEquals(25, response.bodySize)
    }

    @Test
    fun `handle should include cache if required`() {
        val response = handleWith(handlerCache, "gemini://localhost/dir/file.txt")
        assertIs<Success>(response)
        assertEquals("text/plain; cache=1234", response.meta)
        assertNotNull(response.bodyPath)
        assertEquals(5, response.bodySize)
    }

    @Test
    fun `handler, directory listings should return a directory listing if is enabled and no index`() {
        assertIs<DirListing>(handleWith(handlerDir, "gemini://localhost/dir/"))
    }

    @Test
    fun `handler, directory listings should include cache if required`() {
        val response = handleWith(handlerCache, "gemini://localhost/dir/")
        assertIs<DirListing>(response)
        assertEquals(1234, response.cache)
    }

    @Test
    fun `handler, directory listings should return a directory listing, directory listing flags vhost flag false, directories flag true`() {
        assertIs<DirListing>(
            handleWith(
                GeminiHandler(
                    ServiceConf.initConf(
                        TestData.conf.copy(
                            virtualHosts = listOf(
                                TestData.conf.virtualHosts[0].copy(
                                    directoryListing = false,
                                    directories = listOf(
                                        Directory("dir/", true)
                                    )
                                )
                            )
                        )
                    )
                ), "gemini://localhost/dir/"
            )
        )
    }

    @Test
    fun `handler, directory listings should return not found with no index, directory listing flags vhost flag true, directories flag false`() {
        assertIs<NotFound>(
            handleWith(
                GeminiHandler(
                    ServiceConf.initConf(
                        TestData.conf.copy(
                            virtualHosts = listOf(
                                TestData.conf.virtualHosts[0].copy(
                                    directoryListing = true,
                                    directories = listOf(
                                        Directory("dir/", false)
                                    )
                                )
                            )
                        )
                    )
                ), "gemini://localhost/dir/"
            )
        )
    }

    @Test
    fun `handler, directory listings should return not apply directory listing override to subdirectories`() {
        assertIs<NotFound>(
            handleWith(
                GeminiHandler(
                    ServiceConf.initConf(
                        TestData.conf.copy(
                            virtualHosts = listOf(
                                TestData.conf.virtualHosts[0].copy(
                                    directoryListing = false,
                                    directories = listOf(
                                        Directory("dir/", true)
                                    )
                                )
                            )
                        )
                    )
                ), "gemini://localhost/dir/sub/"
            )
        )
    }

    @Test
    fun `handler, directory listings should return not found if directory listing is not enabled and no index`() {
        assertIs<NotFound>(
            handleWith(
                GeminiHandler(
                    ServiceConf.initConf(
                        TestData.conf.copy(
                            virtualHosts = listOf(
                                TestData.conf.virtualHosts[0].copy(
                                    directoryListing = false,
                                )
                            )
                        )
                    )
                ), "gemini://localhost/dir/"
            )
        )
    }

    @Test
    fun `handler, user directories should return success on reading file`() {
        val response = handleWith(handlerUserDir, "gemini://localhost/~username/index.gmi")
        assertIs<Success>(response)
        assertEquals("text/gemini", response.meta)
        assertEquals(38L, response.bodySize)
    }

    @Test
    fun `handler, user directories should return redirect accessing the user directory without ending slash`() {
        assertIs<PermanentRedirect>(handleWith(handlerUserDir, "gemini://localhost/~username"))
    }

    @Test
    fun `handler, user directories should return success accessing the user directory index`() {
        val response = handleWith(handlerUserDir, "gemini://localhost/~username/")
        assertIs<Success>(response)
        assertEquals("text/gemini", response.meta)
        assertEquals(38L, response.bodySize)
    }

    @Test
    fun `handler, user directories should return bad request trying to exit the root directory`() {
        assertIs<BadRequest>(handleWith(handlerUserDir, "gemini://localhost/~username/../../"))
    }

    @Test
    fun `handler, user directories should return redirect to the virtual host root when leaving the user dir`() {
        assertIs<PermanentRedirect>(handleWith(handlerUserDir, "gemini://localhost/~username/../"))
    }

    @Test
    fun `handler, user directories should not translate root if used an invalid user pattern`() {
        assertIs<NotFound>(handleWith(handlerUserDir, "gemini://localhost/~username../"))
        assertIs<NotFound>(handleWith(handlerUserDir, "gemini://localhost/~0invalid/"))
    }

    @Test
    fun `handler, user directories should not translate root if no user directory path was provided`() {
        assertIs<NotFound>(
            handleWith(
                GeminiHandler(
                    TestData.conf.copy(
                        virtualHosts = listOf(
                            TestData.conf.virtualHosts[0].copy(userDirectories = true)
                        )
                    )
                ), "gemini://localhost/~username/"
            )
        )
    }

    @Test
    fun `handler, cgi should not execute a CGI if the target resource is not executable`() {
        assertIs<Success>(handleWith(handlerCgi, "gemini://localhost/dir/file.txt"))
    }
}
