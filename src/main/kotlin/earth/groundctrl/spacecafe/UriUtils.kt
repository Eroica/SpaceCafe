package earth.groundctrl.spacecafe

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun String.encode(): String = runCatching {
    URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
}.getOrDefault(this)

fun String.decode(): String = runCatching {
    URLDecoder.decode(this, StandardCharsets.UTF_8.name())
}.getOrDefault(this)

fun String.isValidPath(): Boolean {
    val segments = this.split('/').drop(1)
    val acc = mutableListOf(0)

    for (segment in segments) {
        when (segment) {
            ".." -> acc.add(acc.last() - 1)
            "." -> acc.add(acc.last())
            else -> acc.add(acc.last() + 1)
        }
    }

    return acc.none { it < 0 }
}

fun URI.toVirtualHost(vHosts: List<VirtualHost>): VirtualHost? = vHosts.find {
    it.host.lowercase() == this.host.lowercase()
}
