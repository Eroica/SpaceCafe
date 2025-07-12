package earth.groundctrl.spacecafe

import earth.groundctrl.spacecafe.handlers.GeminiHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

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

            return when (scheme) {
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
        val certs = conf.virtualHosts.associate {
            val key = it.keyStore

            try {
                it.host to loadCert(key.path, key.alias, key.password)
            } catch (e: Exception) {
                logger.error(e) { "Failed to load ${key.alias} cert from keystore ${key.path}" }
                throw (e)
            }
        }

        val sslContext = genSSLContext(certs)

        certs.forEach {
            logger.info { "Certificate for ${it.key} - serial-no: ${it.value.first.serialNumber}, final-date: ${it.value.first.notAfter}" }
        }

        val socket = sslContext.serverSocketFactory.createServerSocket(conf.port, 100) as SSLServerSocket

        return scope.launch {
            try {
                while (isActive) {
                    val client = socket.accept() as SSLSocket
                    client.useClientMode = false
                    client.enabledCipherSuites = conf.enabledCipherSuites.toTypedArray()
                    client.enabledProtocols = conf.enabledProtocols.toTypedArray()
                    launch { handleConnection(client) }
                }
            } finally {
                socket.close()
            }
        }
    }

    private suspend fun handleConnection(socket: SSLSocket) {
        val remoteAddr = socket.inetAddress.hostAddress
        logger.debug { "new connection $remoteAddr" }

        try {
            socket.use {
                val reader = BufferedReader(InputStreamReader(it.inputStream, Charsets.UTF_8))
                val writer = BufferedOutputStream(it.outputStream)

                val reqLine = withTimeout(conf.idleTimeout) { reader.readLine() }
                    ?: run {
                        logger.warn { "$remoteAddr - empty request received" }
                        writeResponse(writer, BadRequest("Empty request"))
                        return
                    }

                val reqStr = reqLine.take(MAX_REQ_LEN)
                logger.debug { "$remoteAddr - request: $reqStr" }

                val response = try {
                    handleReq(reqStr, remoteAddr)
                } catch (e: Exception) {
                    logger.error(e) { "Error processing request from $remoteAddr" }
                    PermanentFailure(reqStr, "Internal server error: ${e.message}")
                }

                writeResponse(writer, response)
            }
        } catch (e: Exception) {
            logger.error(e) { "$remoteAddr - connection error: ${e.message}" }
        }
    }

    private suspend fun writeResponse(writer: BufferedOutputStream, response: Response) {
        try {
            response.toFlow().collect { chunk -> writer.write(chunk) }
            writer.flush()
            logger.info { "Response sent: ${response.status} ${response.bodySize} bytes" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to write response: ${e.message}" }
            throw e
        }
    }
}
