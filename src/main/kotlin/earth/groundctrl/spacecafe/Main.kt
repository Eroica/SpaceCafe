package earth.groundctrl.spacecafe

import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.file
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.io.File

const val APP_NAME = "${BuildConfig.APP_NAME} Gemini Server"
const val VERSION = BuildConfig.APP_VERSION
const val DEFAULT_CONF_FILE = "spacecafe.conf"

private val logger = KotlinLogging.logger {}

class ServerCommand : NoOpCliktCommand(APP_NAME) {
    val config by option(
        "-c",
        "--config",
        help = "Configuration file (default: $DEFAULT_CONF_FILE)"
    ).file()

    init {
        versionOption(VERSION)
    }
}

suspend fun main(args: Array<String>) {
    val configFile = ServerCommand().apply { this.main(args) }.config
    val conf = try {
        ServiceConf.load(configFile ?: File(DEFAULT_CONF_FILE))
    } catch (e: Exception) {
        logger.error { "Error reading $configFile: $e" }
        return
    }

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val server = Server(conf, scope)

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info { "Shutdown signal received, stopping server ..." }
        server.closeChannel()

        runBlocking {
            try {
                withTimeout(5000) { scope.coroutineContext[Job]?.cancelAndJoin() }
            } catch (_: TimeoutCancellationException) {
                logger.warn { "Forced shutdown after 5s timeout (some connections may be incomplete)" }
            }
        }
    })

    logger.info { "Starting $APP_NAME $VERSION, listening on ${conf.address}:${conf.port}" }

    server.serve().join()
}
