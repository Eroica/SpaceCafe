package earth.groundctrl.spacecafe.handlers

import earth.groundctrl.spacecafe.Response
import earth.groundctrl.spacecafe.ServiceConf
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

abstract class ProtocolHandler(val conf: ServiceConf) {
    companion object {
        const val GEMINI_MIME_TYPE = "text/gemini"
        private val GEMINI_EXTENSIONS = listOf(".gmi", ".gemini")
    }

    val vHosts = conf.virtualHosts

    private val defaultMimeType = conf.defaultMimeType

    fun guessMimeType(path: Path, params: String? = null, size: Boolean = false, cache: Int? = null): String {
        val baseMime = if (conf.mimeTypes == null) {
            path.mimeType()
        } else {
            conf.mimeTypes.entries.find { (_, exts) ->
                exts.any { path.toString().endsWith(it) }
            }?.key ?: defaultMimeType
        }

        val mimeWithParams = if (baseMime == GEMINI_MIME_TYPE) {
            if (params == null) baseMime else "$baseMime; ${params.stripMargin(';').trim()}"
        } else {
            baseMime
        }

        return when {
            size && cache != null -> addCache(addSize(mimeWithParams, path), cache)
            size -> addSize(mimeWithParams, path)
            cache != null -> addCache(mimeWithParams, cache)
            else -> mimeWithParams
        }
    }

    abstract fun handle(req: String, uri: URI, remoteAddr: String): Response

    private fun Path.mimeType(): String {
        return if (GEMINI_EXTENSIONS.any { this.toString().endsWith(it) }) {
            GEMINI_MIME_TYPE
        } else {
            runCatching { Files.probeContentType(this) }
                .getOrNull()
                ?: defaultMimeType
        }
    }

    private fun String.stripMargin(marginChar: Char = ';'): String {
        return if (trimStart().startsWith(marginChar)) {
            substringAfter(marginChar).trimStart()
        } else {
            this
        }
    }

    private fun addSize(base: String, path: Path): String {
        return runCatching { Files.size(path) }
            .getOrNull()
            ?.let { fileSize -> "$base; size=$fileSize" }
            ?: base
    }

    private fun addCache(base: String, cache: Int): String {
        return "$base; cache=$cache"
    }
}
