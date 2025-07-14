package earth.groundctrl.spacecafe.handlers

import earth.groundctrl.spacecafe.Response
import earth.groundctrl.spacecafe.ServiceConf
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

abstract class ProtocolHandler(val conf: ServiceConf) {
    val vHosts = conf.virtualHosts

    private val defaultMimeType = conf.defaultMimeType

    fun guessMimeType(
        path: Path,
        params: String? = null,
        size: Boolean = false,
        cache: Int? = null,
    ): String {
        val addSize: (String) -> String = { base ->
            if (size) {
                runCatching { Files.size(path) }
                    .getOrNull()
                    ?.let { fileSize -> "$base; size=$fileSize" }
                    ?: base
            } else {
                base
            }
        }

        val addCache: (String) -> String = { base ->
            cache?.let { "$base; cache=$it" } ?: base
        }

        val baseMime = if (conf.mimeTypes == null) {
            val pathStr = path.toString()
            if (listOf(".gmi", ".gemini").any { pathStr.endsWith(it) }) {
                "text/gemini"
            } else {
                try {
                    Files.probeContentType(path) ?: defaultMimeType
                } catch (_: Exception) {
                    defaultMimeType
                }
            }
        } else {
            val found = conf.mimeTypes.entries.find { (_, exts) ->
                exts.any { path.toString().endsWith(it) }
            }
            found?.key ?: defaultMimeType
        }

        val mimeWithParams = if (baseMime == "text/gemini") {
            if (params == null) baseMime else "$baseMime; ${params.stripMargin(';').trim()}"
        } else {
            baseMime
        }

        return addCache(addSize(mimeWithParams))
    }

    abstract fun handle(req: String, uri: URI, remoteAddr: String): Response

    private fun String.stripMargin(marginChar: Char = ';'): String {
        return this.lines()
            .joinToString("\n") { line ->
                if (line.trimStart().startsWith(marginChar)) {
                    line.substringAfter(marginChar).trimStart()
                } else {
                    line
                }
            }
    }
}
