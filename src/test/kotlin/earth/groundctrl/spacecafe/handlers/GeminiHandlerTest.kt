package earth.groundctrl.spacecafe.handlers

import earth.groundctrl.spacecafe.*
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        val response = handleWith(handlerCgi, "gemini://localhost/dir/file.txt")
        assertIs<Success>(response)
        assertEquals("text/plain", response.meta)
        assertEquals(5, response.bodySize)
    }

    @Test
    fun `handler, cgi should not execute a CGI if the target resource is a directory`() {
        assertIs<DirListing>(handleWith(handlerCgi, "gemini://localhost/dir/sub/"))
    }

    @Test
    fun `handler, cgi should not apply allow CGI to subdirectories`() {
        val response = handleWith(handlerCgi, "gemini://localhost/dir/sub/cgi")
        assertIs<Success>(response)
        assertEquals("text/plain", response.meta)
        assertEquals(72, response.bodySize)
    }

    @Test
    fun `handler, cgi should execute a CGI`() {
        val cgi = handleWith(handlerCgi, "gemini://localhost/dir/cgi")
        assertIs<Cgi>(cgi)
        assertEquals(20, cgi.status)
        assertEquals("text/gemini", cgi.meta)
        assertTrue("GATEWAY_INTERFACE=CGI/1.1" in cgi.body)
    }

    @Test
    fun `handler, cgi should execute a CGI empty parameters, host and port`() {
        val cgi = handleWith(handlerCgi, "gemini://localhost/dir/cgi")
        assertIs<Cgi>(cgi)
        assertEquals("", cgi.queryString)
        assertEquals("", cgi.pathInfo)
        assertEquals("cgi", cgi.scriptName)
        assertEquals(TestData.host, cgi.host)
        assertEquals(TestData.portStr, cgi.port)
    }

    @Test
    fun `handler, cgi should execute a CGI host is case-insensitive and the value in the conf is used`() {
        val cgi = handleWith(handlerCgi, "gemini://LOCALHOST/dir/cgi")
        assertIs<Cgi>(cgi)
        assertEquals("", cgi.queryString)
        assertEquals("", cgi.pathInfo)
        assertEquals("cgi", cgi.scriptName)
        assertEquals(TestData.host, cgi.host)
        assertEquals(TestData.portStr, cgi.port)
    }

    @Test
    fun `handler, cgi should execute a CGI query string`() {
        val cgi = handleWith(handlerCgi, "gemini://localhost/dir/cgi?query&string")
        assertIs<Cgi>(cgi)
        assertEquals("query&string", cgi.queryString)
        assertEquals("", cgi.pathInfo)
        assertEquals("cgi", cgi.scriptName)
        assertEquals(TestData.host, cgi.host)
        assertEquals(TestData.portStr, cgi.port)
    }

    @Test
    fun `handler, cgi should execute a CGI path info`() {
        val cgi = handleWith(handlerCgi, "gemini://localhost/dir/cgi/path/info")
        assertIs<Cgi>(cgi)
        assertEquals("", cgi.queryString)
        assertEquals("/path/info", cgi.pathInfo)
        assertEquals("cgi", cgi.scriptName)
        assertEquals(TestData.host, cgi.host)
        assertEquals(TestData.portStr, cgi.port)
    }

    @Test
    fun `handler, cgi should execute a CGI query string and path info`() {
        val cgi = handleWith(handlerCgi, "gemini://localhost/dir/cgi/path/info?query=string")
        assertIs<Cgi>(cgi)
        assertEquals("query=string", cgi.queryString)
        assertEquals("/path/info", cgi.pathInfo)
        assertEquals("cgi", cgi.scriptName)
        assertEquals(TestData.host, cgi.host)
        assertEquals(TestData.portStr, cgi.port)
    }

    @Test
    fun `handler, cgi should not execute an executable if allow CGI is off`() {
        val cgi = handleWith(handler, "gemini://localhost/dir/cgi")
        assertIs<Success>(cgi)
        assertEquals("text/plain", cgi.meta)
    }

    @Test
    fun `handler, cgi should return a response with an error if the CGI exits with non 0`() {
        val bad = handleWith(handlerCgi, "gemini://localhost/dir/bad-cgi")
        assertIs<Cgi>(bad)

        val meta = "Error executing CGI"
        assertEquals(42, bad.status)
        assertEquals(meta, bad.meta)
        assertTrue(meta in bad.body)
    }

    @Test
    fun `handler, cgi should return a response with an error if the CGI response is invalid`() {
        val bad = handleWith(handlerCgi, "gemini://localhost/dir/bad-response")
        assertIs<Cgi>(bad)

        val meta = "Invalid response from CGI"
        assertEquals(42, bad.status)
        assertEquals(meta, bad.meta)
        assertTrue(meta in bad.body)
    }

    @Test
    fun `handler, cgi should environment variables are optional`() {
        val cgi = handleWith(handlerCgi, "gemini://localhost/dir/cgi/")
        assertIs<Cgi>(cgi)
        assertEquals("cgi", cgi.scriptName)
        assertEquals(TestData.host, cgi.host)
        assertEquals(TestData.portStr, cgi.port)
        assertEquals(mapOf(), cgi.vhEnv)
    }

    @Test
    fun `handler, cgi should pass environment variables to the CGI`() {
        val cgi = handleWith(GeminiHandler(TestData.cgiEnvConf), "gemini://localhost/dir/cgi/")
        assertIs<Cgi>(cgi)
        assertEquals("cgi", cgi.scriptName)
        assertEquals(TestData.host, cgi.host)
        assertEquals(TestData.portStr, cgi.port)
        assertEquals(mapOf("env1" to "value"), cgi.vhEnv)
    }

    @Test
    fun `handler, cgi should execute a CGI with the environment variables`() {
        val cgi = handleWith(GeminiHandler(TestData.cgiEnvConf), "gemini://localhost/dir/cgi")
        assertIs<Cgi>(cgi)
        assertEquals(20, cgi.status)
        assertEquals("text/gemini", cgi.meta)
        assertTrue("env1=value" in cgi.body)
    }

    @Test
    fun `handler, cgi should execute a CGI when it is the index document`() {
        val cgi = handleWith(GeminiHandler(TestData.cgiIndexConf), "gemini://localhost/dir/")
        assertIs<Cgi>(cgi)
        assertEquals(20, cgi.status)
        assertEquals("text/gemini", cgi.meta)
        assertTrue("GATEWAY_INTERFACE=CGI/1.1" in cgi.body)
    }

    @Test
    fun `handler, cgi should execute a CGI when it is the index document (full name)`() {
        val cgi = handleWith(GeminiHandler(TestData.cgiIndexConf), "gemini://localhost/dir/cgi")
        assertIs<Cgi>(cgi)
        assertEquals(20, cgi.status)
        assertEquals("text/gemini", cgi.meta)
        assertTrue("GATEWAY_INTERFACE=CGI/1.1" in cgi.body)
    }

    @Test
    fun `handler, cgi should execute a CGI when it is the index document (full name, path info)`() {
        val cgi = handleWith(GeminiHandler(TestData.cgiIndexConf), "gemini://localhost/dir/cgi/path/info")
        assertIs<Cgi>(cgi)
        assertEquals(20, cgi.status)
        assertEquals("text/gemini", cgi.meta)
        assertTrue("GATEWAY_INTERFACE=CGI/1.1" in cgi.body)
        assertTrue("PATH_INFO=/path/info" in cgi.body)
    }

    @Test
    fun `handler, cgi should resolve CGI directories from more to less specific`() {
        val cgi = handleWith(GeminiHandler(TestData.cgiPrefConf), "gemini://localhost/dir/sub/cgiOk/path/info")
        assertIs<Cgi>(cgi)
        assertEquals(20, cgi.status)
        assertEquals("text/gemini", cgi.meta)
        assertTrue("GATEWAY_INTERFACE=CGI/1.1" in cgi.body)
        assertTrue("PATH_INFO=/path/info" in cgi.body)
    }
}
