package earth.groundctrl.spacecafe.handlers

import earth.groundctrl.spacecafe.Response
import earth.groundctrl.spacecafe.ServiceConf
import earth.groundctrl.spacecafe.TestData
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ProtocolHandlerTest {
    private val handler = TestHandler(TestData.conf)

    @Test
    fun `guessMimeType using the internal resolver should resolve a known MIME type`() {
        assertEquals("text/html", handler.guessMimeType(getPath("file.html")))
    }

    @Test
    fun `guessMimeType using the internal resolver should resolve de default MIME type for unknown types`() {
        assertEquals(
            TestData.conf.defaultMimeType,
            handler.guessMimeType(getPath("unknow"))
        )
    }

    @Test
    fun `guessMimeType using the internal resolver should resolve gemini MIME type`() {
        assertEquals(
            "text/gemini",
            handler.guessMimeType(getPath("file.gmi"))
        )
        assertEquals(
            "text/gemini",
            handler.guessMimeType(getPath("file.gemini"))
        )
    }

    @Test
    fun `guessMimeType using the internal resolver should resolve gemini MIME type, including parameters`() {
        assertEquals(
            "text/gemini; param",
            handler.guessMimeType(getPath("file.gmi"), "param")
        )
        assertEquals(
            "text/gemini; param",
            handler.guessMimeType(getPath("file.gemini"), "param")
        )
    }

    @Test
    fun `guessMimeType using the internal resolver should gemini MIME type parameters are sanitized`() {
        assertEquals(
            "text/gemini; param",
            handler.guessMimeType(getPath("file.gmi"), "     ; param")
        )
    }

    @Test
    fun `guessMimeType using the configured types should resolve a known MIME type`() {
        assertEquals(
            "config",
            TestHandler(TestData.conf.copy(mimeTypes = TestData.mimeTypes))
                .guessMimeType(getPath("file.gmi"))
        )
    }

    @Test
    fun `guessMimeType using the configured types should include parameters for gemini MIME types`() {
        assertEquals(
            "text/gemini; param",
            TestHandler(TestData.conf.copy(mimeTypes = mapOf("text/gemini" to listOf(".gmi"))))
                .guessMimeType(getPath("file.gmi"), "param")
        )
    }

    @Test
    fun `guessMimeType using the configured types should resolve de default MIME type for unknown types`() {
        assertEquals(
            TestData.conf.defaultMimeType,
            TestHandler(TestData.conf.copy(mimeTypes = TestData.mimeTypes))
                .guessMimeType(getPath("unknow"))
        )
    }

    @Test
    fun `guessMimeType using the configured types should include the file size if required`() {
        assertEquals(
            "text/plain; size=5",
            handler.guessMimeType(Paths.get(javaClass.getResource("/dir/file.txt").toURI()), null, true)
        )
    }

    @Test
    fun `guessMimeType using the configured types should include the file size if required after any optional parameters`() {
        assertEquals(
            "text/gemini; charset=utf-8; lang=en; size=25",
            handler.guessMimeType(Paths.get(javaClass.getResource("/index.gmi").toURI()), "charset=utf-8; lang=en", true)
        )
    }

    @Test
    fun `guessMimeType using the configured types should not include the file size if required but failed`() {
        assertEquals(
            "text/plain",
            handler.guessMimeType(getPath("not-found"), null, true)
        )
    }

    @Test
    fun `guessMimeType using the configured types should include the cache if required`() {
        assertEquals(
            "text/plain; cache=1234",
            handler.guessMimeType(Paths.get(javaClass.getResource("/dir/file.txt").toURI()), null, false, 1234)
        )
    }

    @Test
    fun `guessMimeType using the configured types should include the cache if required after any optional parameters`() {
        assertEquals(
            "text/gemini; charset=utf-8; lang=en; cache=1234",
            handler.guessMimeType(Paths.get(javaClass.getResource("/index.gmi").toURI()), "charset=utf-8; lang=en", false, 1234)
        )
    }

    @Test
    fun `guessMimeType using the configured types should include both cache and size if both required`() {
        assertEquals(
            "text/gemini; size=25; cache=1234",
            handler.guessMimeType(Paths.get(javaClass.getResource("/index.gmi").toURI()), null, true, 1234)
        )
    }

    private fun getPath(value: String) = FileSystems.getDefault().getPath(value)
}

class TestHandler(conf: ServiceConf) : ProtocolHandler(conf) {
    override fun handle(req: String, uri: URI, remoteAddr: String): Response {
        throw UnsupportedOperationException()
    }
}
