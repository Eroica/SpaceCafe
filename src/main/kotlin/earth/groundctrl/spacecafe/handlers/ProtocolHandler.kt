package earth.groundctrl.spacecafe.handlers

import earth.groundctrl.spacecafe.Response
import earth.groundctrl.spacecafe.ServiceConf
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

abstract class ProtocolHandler(val conf: ServiceConf) {
    val vHosts = conf.virtualHosts

    private val defaultMimeType = conf.defaultMimeType

    fun guessMimeType(path: Path, params: String?, size: Boolean, cache: Int? = null): String {
        val addSize: (String) -> String = { base ->
            if (size) {
                runCatching { Files.size(path) }.getOrNull()?.let { fileSize ->
                    "$base; size=$fileSize"
                } ?: base
            } else {
                base
            }
        }

        val addCache: (String) -> String = { base ->
            cache?.let { value -> "$base; cache=$value" } ?: base
        }

        val baseMime = conf.mimeTypes?.let { types ->
            types.entries.find { (t, exts) ->
                exts.any { path.toString().endsWith(it) }
            }?.key ?: defaultMimeType
        } ?: run {
            listOf(".gmi", ".gemini").find { path.toString().endsWith(it) }?.let {
                "text/gemini"
            } ?: runCatching { Files.probeContentType(path) }.getOrNull() ?: defaultMimeType
        }

        val mime = if (baseMime == "text/gemini") {
            params?.let { "$baseMime; ${it.trim()}" } ?: baseMime
        } else {
            baseMime
        }

        return addCache(addSize(mime))
    }

    abstract fun handle(req: String, uri: URI, remoteAddr: String): Response
}
