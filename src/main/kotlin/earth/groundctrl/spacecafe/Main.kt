package earth.groundctrl.spacecafe

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.file
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.io.File

class ServerApp : CliktCommand() {
    companion object {
        const val APP_NAME = "${BuildConfig.APP_NAME} Gemini Server"
        const val VERSION = BuildConfig.APP_VERSION
        const val DEFAULT_CONF_FILE = "spacecafe.conf"

        private val logger = KotlinLogging.logger {}
    }

    val config by option(
        "-c",
        "--config",
        help = "Configuration file (default: $DEFAULT_CONF_FILE)"
    ).file()

    init {
        versionOption(VERSION)
    }

    override fun run() {
        val conf = try {
            ServiceConf.load(config ?: File(DEFAULT_CONF_FILE))
        } catch (e: Exception) {
            logger.error { "Error reading $config: $e" }
            return
        }

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val server = Server(conf, scope)

        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info { "Shutdown signal received, stopping server ..." }
            scope.cancel()
        })

        logger.info { "Starting $APP_NAME $VERSION, listening on ${conf.address}:${conf.port}" }

        runBlocking { server.serve().join() }
    }
}

fun main(args: Array<String>) = ServerApp().main(args)
