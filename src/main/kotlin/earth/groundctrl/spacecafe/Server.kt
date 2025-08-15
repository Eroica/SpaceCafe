package earth.groundctrl.spacecafe

import earth.groundctrl.spacecafe.handlers.GeminiHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import tlschannel.ServerTlsChannel
import java.net.InetSocketAddress
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.ClosedChannelException
import java.nio.channels.ServerSocketChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLHandshakeException

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
    @Volatile
    private var channel: ServerSocketChannel? = null

    fun handleReq(req: String, remoteAddr: String): Response {
        return try {
            val uri = URI.create(req)

            when (val scheme = uri.scheme) {
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
        val sslContexts = conf.virtualHosts.associate { it.host to genSSLContext(it) }
        val sniFactory = sniKeyManager(sslContexts)
        val serverChannel = ServerSocketChannel.open().also { channel = it }
        serverChannel.bind(InetSocketAddress(conf.address, conf.port), 100)

        return scope.launch {
            try {
                while (isActive) {
                    val plainChannel = serverChannel.accept()
                    val builder = ServerTlsChannel.newBuilder(plainChannel, sniFactory)
                    val tlsChannel = builder.build()
                    val remoteAddr = plainChannel.socket().inetAddress.hostAddress

                    launch {
                        try {
                            handleConnection(tlsChannel, remoteAddr)
                        } catch (_: SSLHandshakeException) {
                            logger.info { "$remoteAddr - invalid handshake" }
                        } catch (_: ClosedChannelException) {
                            logger.info { "$remoteAddr - channel closed without request" }
                        } catch (e: Exception) {
                            logger.error(e) { "Error handling TLS connection" }
                        } finally {
                            tlsChannel.close()
                            plainChannel.close()
                        }
                    }
                }
            } catch (_: AsynchronousCloseException) {
            } catch (_: ClosedChannelException) {
            } catch (e: Exception) {
                logger.error(e) { "Unexpected error" }
            } finally {
                serverChannel.close()
            }
        }
    }

    fun closeChannel() {
        channel?.close()
        channel = null
    }

    private suspend fun handleConnection(tlsChannel: ServerTlsChannel, remoteAddr: String) {
        logger.debug { "new connection $remoteAddr" }

        /* Reading a single byte more than 1024 to detect a too large request */
        val readBuffer = ByteBuffer.allocate(MAX_REQ_LEN + 1)
        val requestBuilder = StringBuilder()
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        val resp = try {
            while (true) {
                currentCoroutineContext().ensureActive()
                readBuffer.clear()
                val bytesRead =  tlsChannel.read(readBuffer)
                if (bytesRead == -1) {
                    return
                }

                readBuffer.flip()
                val chunk = decoder.decode(readBuffer).toString()
                requestBuilder.append(chunk)

                if (requestBuilder.contains("\r\n")) {
                    break
                }
            }

            val requestLine = requestBuilder.toString().lineSequence().first().trim()
            if (requestLine.length > MAX_REQ_LEN) {
                throw RuntimeException()
            }

            handleReq(requestLine, remoteAddr)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            BadRequest(requestBuilder.toString())
        }

        resp.toFlow().collect { chunk ->
            val writeBuffer = ByteBuffer.wrap(chunk)
            while (writeBuffer.hasRemaining()) {
                tlsChannel.write(writeBuffer)
            }
        }

        logger.info { """$remoteAddr "${resp.req}" ${resp.status} ${resp.bodySize}""" }
    }
}
