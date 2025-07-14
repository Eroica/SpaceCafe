package earth.groundctrl.spacecafe

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addFileSource
import com.sksamuel.hoplite.hocon.HoconParser
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path

data class KeyStore(
    val path: String,
    val alias: String,
    val password: String
)

data class Directory(
    val path: String,
    val directoryListing: Boolean? = null,
    val allowCgi: Boolean? = null,
    val cache: Int? = null
)

data class VirtualHost(
    val host: String,
    val root: String,
    val keyStore: KeyStore,
    val indexFile: String = "index.gmi",
    val directoryListing: Boolean = true,
    val size: Boolean = false,
    val geminiParams: String? = null,
    val directories: List<Directory> = emptyList(),
    val userDirectories: Boolean = false,
    val userDirectoryPath: String? = null,
    val environment: Map<String, String>? = null
) {
    companion object {
        const val USER_TAG = "{user}"
        val USER_RE = Regex("""/~([a-z_][a-z0-9_-]*)(/{1}.*)?""")
    }

    fun getDirectoryListing(path: Path): Boolean = directories.find { it.path == path.toString() }
        ?.directoryListing
        ?: directoryListing

    fun getCgi(path: Path): Path? = directories.filter { it.allowCgi == true }
        .sortedByDescending { it.path.length }
        .find { path.startsWith(it.path) && path.toString() != it.path }
        ?.let { d ->
            val dp = FileSystems.getDefault().getPath(d.path).normalize()
            FileSystems.getDefault().getPath(d.path, path.getName(dp.nameCount).toString())
        }

    fun getCache(path: Path): Int? = directories.filter { it.cache != null }
        .sortedByDescending { it.path.length }
        .find { path.startsWith(it.path) }
        ?.cache

    fun getRoot(path: String): Pair<String, String> = when {
        USER_RE.matches(path) && userDirectories && userDirectoryPath != null -> {
            val matchResult = USER_RE.find(path)!!
            val user = matchResult.groupValues[1]
            val userPath = matchResult.groupValues[2]
            if (userPath.isEmpty()) {
                userDirectoryPath.replace(USER_TAG, user) to "."
            } else {
                userDirectoryPath.replace(USER_TAG, user) to userPath
            }
        }

        else -> root to path
    }
}

data class ServiceConf(
    val address: String,
    val port: Int,
    val idleTimeout: Long,
    val defaultMimeType: String,
    val mimeTypes: Map<String, List<String>>? = null,
    val virtualHosts: List<VirtualHost>,
    val enabledProtocols: List<String>,
    val enabledCipherSuites: List<String>
) {
    companion object {
        private val logger = KotlinLogging.logger {}

        fun initConf(conf: ServiceConf): ServiceConf {
            return conf.copy(virtualHosts = conf.virtualHosts.map { vhost ->
                if (vhost.userDirectories && (vhost.userDirectoryPath?.contains(VirtualHost.USER_TAG) != true)) {
                    logger.warn { "In virtual host '${vhost.host}': user-directories is enabled but ${VirtualHost.USER_TAG} not found in user-directory-path" }
                }

                vhost.copy(directories = vhost.directories.map { dir ->
                    val path = FileSystems.getDefault().getPath(vhost.root, dir.path).normalize()

                    if (!path.toFile().isDirectory) {
                        logger.warn { "In virtual host '${vhost.host}': directory entry '${dir.path}' is not a directory" }
                    }

                    dir.copy(path = path.toString())
                })
            })
        }

        @OptIn(ExperimentalHoplite::class)
        fun load(confFile: File): ServiceConf = initConf(
            ConfigLoaderBuilder.default()
                .withExplicitSealedTypes()
                .addFileExtensionMapping("conf", HoconParser())
                .addFileSource(confFile)
                .build()
                .loadConfigOrThrow<ServiceConf>()
        )
    }
}
