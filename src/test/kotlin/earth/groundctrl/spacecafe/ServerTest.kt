package earth.groundctrl.spacecafe

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertIs

internal class ServerTest {
    private val testDispatcher = StandardTestDispatcher()
    private val scope = TestScope(testDispatcher)

    @Test
    fun `handleReq should return bad request on empty URLs`() {
        val res = Server(TestData.conf, scope).handleReq("", "127.0.0.1")
        assertIs<BadRequest>(res)
    }

    @Test
    fun `handleReq should return bad request for invalid URLs`() {
        val res = Server(TestData.conf, scope).handleReq("gemini://localhost/ invalid", "127.0.0.1")
        assertIs<BadRequest>(res)
    }

    @Test
    fun `handleReq should return bad request on URLs with no scheme`() {
        val res = Server(TestData.conf, scope).handleReq("//localhost/", "127.0.0.1")
        assertIs<BadRequest>(res)
    }

    @Test
    fun `handleReq should return proxy request refused on port mismatch`() {
        val res = Server(TestData.conf, scope).handleReq("gemini://localhost:8080/", "127.0.0.1")
        assertIs<ProxyRequestRefused>(res)
    }

    @Test
    fun `handleReq should return proxy request refused when port not provided and configured port is not default`() {
        val res = Server(TestData.conf.copy(port = 8080), scope).handleReq("gemini://localhost/", "127.0.0.1")
        assertIs<ProxyRequestRefused>(res)
    }

    @Test
    fun `handleReq should return success when port is provided and matches configured port (not default)`() {
        val res = Server(TestData.conf.copy(port = 8080), scope).handleReq("gemini://localhost:8080/", "127.0.0.1")
        assertIs<Success>(res)
    }

    @Test
    fun `handleReq should return proxy request refused for non gemini schemes`() {
        val res = Server(TestData.conf, scope).handleReq("https://localhost/", "127.0.0.1")
        assertIs<ProxyRequestRefused>(res)
    }
}
