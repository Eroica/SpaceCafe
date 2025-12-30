package earth.groundctrl.spacecafe

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

sealed interface Response {
    val req: String
    val status: Int
    val meta: String
    val bodyPath: Path?
    val bodySize: Long

    fun toFlow(): Flow<ByteArray> = flow {
        emit("$status $meta\r\n".toByteArray(Charsets.UTF_8))
        val path = bodyPath
        if (path != null && Files.exists(path)) {
            Files.newInputStream(path, StandardOpenOption.READ).use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    emit(buffer.copyOf(bytesRead))
                }
            }
        }
    }
}

abstract class NoContentResponse : Response {
    override val bodyPath = null
    override val bodySize = 0L
}

data class Success(
    override val req: String,
    override val meta: String = "Success",
    override val bodyPath: Path? = null,
    override val bodySize: Long = 0
) : Response {
    override val status: Int = 20
}

class DirListing(
    override val req: String,
    override val meta: String = "Success",
    val uriPath: String,
    override val bodyPath: Path? = null,
    val cache: Int? = null
) : Response {
    override val status: Int = 20

    private val body: String = buildString {
        if (bodyPath != null && Files.exists(bodyPath)) {
            append("# Index of $uriPath\n")
            if (uriPath != "/") {
                append("=> ../ ..\n")
            }

            append(
                bodyPath.toFile()
                .listFiles()
                .toList()
                .sortedBy {
                    when {
                        it.isDirectory -> 0
                        it.isFile -> 1
                        else -> 2
                    }
                }
                .flatMap {
                    when {
                        !it.canRead() || it.name.startsWith(".") -> emptyList()
                        it.isDirectory -> listOf("=> ${it.name.encode()}/ ${it.name}/")
                        else -> listOf("=> ${it.name.encode()} ${it.name}")
                    }
                }
                .joinToString("\n")
            )
            append("\n")
        }
    }

    override val bodySize = body.toByteArray().size.toLong()

    override fun toFlow(): Flow<ByteArray> = flow {
        val statusLine = buildString {
            append("$status $meta")
            cache?.let { append("; cache=$it") }
            append("\r\n")
        }
        emit(statusLine.toByteArray())
        emit(body.toByteArray())
    }
}

class Cgi(
    override val req: String,
    val filename: String,
    val queryString: String,
    val pathInfo: String,
    val scriptName: String,
    val host: String,
    val port: String,
    val remoteAddr: String,
    val vhEnv: Map<String, String>
) : Response {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override val bodyPath: Path? = null

    private val env: Map<String, String> = buildMap {
        putAll(vhEnv)
        put("GATEWAY_INTERFACE", "CGI/1.1")
        put("SERVER_SOFTWARE", "${BuildConfig.APP_NAME}/${BuildConfig.APP_VERSION}")
        put("SERVER_PROTOCOL", "GEMINI")
        put("GEMINI_URL", req)
        put("SCRIPT_NAME", scriptName)
        put("PATH_INFO", pathInfo)
        put("QUERY_STRING", queryString)
        put("SERVER_NAME", host)
        put("SERVER_PORT", port)
        put("REMOTE_ADDR", remoteAddr)
        put("REMOTE_HOST", remoteAddr)
    }

    private val parsed: Triple<Int, String, String> = runCatching {
        val output = ByteArrayOutputStream()

        val processBuilder = ProcessBuilder(filename)
        val processEnv = processBuilder.environment()
        processEnv.clear()
        env.forEach { (k, v) -> processEnv[k] = v }

        val process = processBuilder.start()
        process.inputStream.copyTo(output)
        val exitCode = process.waitFor()
        output.close()

        if (exitCode == 0) {
            val responseBody = output.toString(Charsets.UTF_8.name())
            val header = responseBody.split("\r\n").first()
            val match = Regex("""^(\d{2}) (.*)""").matchEntire(header)

            if (match != null && header.length <= Server.MAX_REQ_LEN) {
                val status = match.groupValues[1].toInt()
                val meta = match.groupValues[2]
                Triple(status, meta, responseBody)
            } else {
                logger.warn { "$scriptName: invalid CGI response" }
                respError(42, "Invalid response from CGI")
            }
        } else {
            logger.warn { "$scriptName: CGI exited with $exitCode" }
            respError(42, "Error executing CGI")
        }
    }.getOrElse {
        logger.warn { "$scriptName: CGI execution failed: ${it.message}" }
        respError(42, "Error executing CGI")
    }

    override val status: Int = parsed.first
    override val meta: String = parsed.second
    val body: String = parsed.third

    override val bodySize: Long
        get() = body.toByteArray().size.toLong()

    override fun toFlow(): Flow<ByteArray> = flow {
        emit(body.toByteArray())
    }

    private fun respError(status: Int, meta: String): Triple<Int, String, String> {
        val limitedMeta = meta.take(Server.MAX_REQ_LEN - 5)
        return Triple(status, limitedMeta, "$status $limitedMeta\r\n")
    }
}

data class TempRedirect(
    override val req: String,
    override val meta: String = "Redirect - temporary"
) : NoContentResponse() {
    override val status: Int = 30
}

data class PermanentRedirect(
    override val req: String,
    override val meta: String = "Redirect - permanent"
) : NoContentResponse() {
    override val status: Int = 31
}

data class TempFailure(
    override val req: String,
    override val meta: String = "Temporary failure"
) : NoContentResponse() {
    override val status: Int = 40
}

data class NotAvailable(
    override val req: String,
    override val meta: String = "Server not available"
) : NoContentResponse() {
    override val status: Int = 41
}

data class PermanentFailure(
    override val req: String,
    override val meta: String = "Permanent failure"
) : NoContentResponse() {
    override val status: Int = 50
}

data class NotFound(
    override val req: String,
    override val meta: String = "Not found"
) : NoContentResponse() {
    override val status: Int = 51
}

data class ProxyRequestRefused(
    override val req: String,
    override val meta: String = "Proxy request refused"
) : NoContentResponse() {
    override val status: Int = 53
}

data class BadRequest(
    override val req: String,
    override val meta: String = "Bad request"
) : NoContentResponse() {
    override val status: Int = 59
}
