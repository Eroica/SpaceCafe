package earth.groundctrl.spacecafe

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths

internal object TestData {
    private val host = "localhost"
    private val port = 1965
    private val portStr = port.toString()

    val resPath: Path = Paths.get(javaClass.getResource("/dir").toURI()).parent
    val resPathStr = resPath.toString()

    val conf = ServiceConf(
        address = "127.0.0.1",
        port = port,
        defaultMimeType = "text/plain",
        idleTimeout = 10000,
        virtualHosts = listOf(
            VirtualHost(
                host = host,
                root = resPathStr,
                keyStore = KeyStore(
                    path = "/tmp/unused.jks",
                    alias = "localhost",
                    password = "secret"
                )
            )
        ),
        enabledProtocols = listOf(),
        enabledCipherSuites = listOf()
    )

    val dirConf = ServiceConf.initConf(
        conf.copy(
            virtualHosts =
                listOf(
                    conf.virtualHosts[0]
                        .copy(
                            directoryListing = true,
                            directories = listOf(
                                Directory(
                                    "dir/",
                                    directoryListing = true,
                                )
                            )
                        )
                )
        )
    )

    val cgiConf = ServiceConf.initConf(
        conf.copy(
            virtualHosts =
                listOf(
                    conf.virtualHosts[0]
                        .copy(
                            directoryListing = true,
                            directories = listOf(
                                Directory(
                                    "dir/",
                                    directoryListing = false,
                                    allowCgi = true,
                                    cache = null
                                )
                            )
                        )
                )
        )
    )

    val cgiPrefConf = ServiceConf.initConf(
        conf.copy(
            virtualHosts =
                listOf(
                    conf.virtualHosts[0]
                        .copy(
                            directoryListing = true,
                            directories = listOf(
                                Directory(
                                    "dir/",
                                    directoryListing = false,
                                    allowCgi = true,
                                    cache = null
                                ),
                                Directory(
                                    "dir/sub/",
                                    directoryListing = false,
                                    allowCgi = true,
                                    cache = null
                                )
                            )
                        )
                )
        )
    )

    val cgiEnvConf = cgiConf.copy(
        virtualHosts = listOf(
            cgiConf.virtualHosts[0].copy(environment = mapOf("env1" to "value"))
        )
    )
    val cgiIndexConf = cgiConf.copy(
        virtualHosts = listOf(
            cgiConf.virtualHosts[0].copy(indexFile = "cgi")
        )
    )

    val confUserDir = conf.copy(
        virtualHosts = listOf(
            conf.virtualHosts[0]
                .copy(
                    userDirectories = true,
                    userDirectoryPath = resPathStr + "/{user}/public_gemini/"
                )
        )
    )

    val cacheConf = ServiceConf.initConf(
        conf.copy(
            virtualHosts =
                listOf(
                    conf.virtualHosts[0]
                        .copy(
                            directoryListing = true,
                            directories = listOf(
                                Directory(
                                    "dir/",
                                    directoryListing = true,
                                    allowCgi = null,
                                    cache = 1234
                                )
                            )
                        )
                )
        )
    )

    val mimeTypes = mapOf("config" to listOf(".gmi", ".gemini"))

    private fun getPath(value: String) = FileSystems.getDefault().getPath(resPathStr, value).toString()
}
