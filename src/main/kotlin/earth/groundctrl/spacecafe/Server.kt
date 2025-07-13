package earth.groundctrl.spacecafe

import earth.groundctrl.spacecafe.handlers.GeminiHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import tlschannel.ServerTlsChannel
import java.net.InetSocketAddress
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

class Server(
    private val conf: ServiceConf,
    private val scope: CoroutineScope
) {
    companion object {
        const val MAX_REQ_LEN: Int = 1024
        const val DEFAULT_PORT: Int = 1965
    }

    private val geminiHandler = GeminiHandler(conf)

    fun handleReq(req: String, remoteAddr: String): Response {
        return try {
            val uri = URI.create(req)
            val scheme = uri.scheme

            when (scheme) {
                null -> {
                    logger.debug { "no scheme" }
                    BadRequest(req)
                }

                "gemini" if uri.port != -1 && uri.port != conf.port -> {
                    logger.debug { "invalid port, is a proxy request" }
                    ProxyRequestRefused(req)
                }

                "gemini" if uri.port == -1 && conf.port != DEFAULT_PORT -> {
                    logger.debug { "default port but non default was configured, is a proxy request" }
                    ProxyRequestRefused(req)
                }

                "gemini" -> {
                    logger.debug { "gemini request: $req" }
                    geminiHandler.handle(req, uri, remoteAddr)
                }

                else -> {
                    logger.debug { "scheme $scheme not allowed" }
                    ProxyRequestRefused(req)
                }
            }
        } catch (e: IllegalArgumentException) {
            logger.debug { "invalid request: ${e.message}" }
            BadRequest(req)
        } catch (error: Exception) {
            logger.error(error) { "Internal server error" }
            PermanentFailure(req, "Internal server error")
        }
    }

    fun serve(): Job {
        val sslContexts = conf.virtualHosts.associate {
            it.host to genSSLContext(it)
        }
        val sniFactory = sniKeyManager(sslContexts)

        val serverChannel = ServerSocketChannel.open()
        serverChannel.bind(InetSocketAddress(conf.address, conf.port), 100)

        return scope.launch {
            try {
                while (isActive) {
                    val plainChannel = serverChannel.accept()
                    val builder = ServerTlsChannel.newBuilder(plainChannel, sniFactory)
                    val tlsChannel = builder.build()
                    val remoteAddr = plainChannel.socket().inetAddress.hostAddress

                    try {
                        handleConnection(tlsChannel, remoteAddr)
                    } catch (e: Exception) {
                        logger.error(e) { "Error handling TLS connection" }
                    } finally {
                        tlsChannel.close()
                        plainChannel.close()
                    }
                }
            } finally {
                serverChannel.close()
            }
        }
    }

    private suspend fun handleConnection(tlsChannel: ServerTlsChannel, remoteAddr: String) {
        logger.debug { "new connection $remoteAddr" }

        val readBuffer = ByteBuffer.allocate(MAX_REQ_LEN)
        val requestBuilder = StringBuilder()

        while (true) {
            readBuffer.clear()
            val bytesRead =  tlsChannel.read(readBuffer)
            if (bytesRead == -1) {
                return
            }

            readBuffer.flip()
            val chunk = StandardCharsets.UTF_8.decode(readBuffer).toString()
            requestBuilder.append(chunk)

            if (requestBuilder.contains("\r\n")) {
                break
            }
        }

        val requestLine = requestBuilder.toString().lineSequence().first().trim()
        val response = handleReq(requestLine, remoteAddr)

        response.toFlow().collect { chunk ->
            val writeBuffer = ByteBuffer.wrap(chunk)
            while (writeBuffer.hasRemaining()) {
                tlsChannel.write(writeBuffer)
            }
        }
    }
}
