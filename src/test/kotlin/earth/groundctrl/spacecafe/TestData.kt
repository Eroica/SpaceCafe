package earth.groundctrl.spacecafe

import java.nio.file.Paths

internal object TestData {
    private val host = "localhost"
    private val port = 1965
    private val portStr = port.toString()
    private val resPath = Paths.get(javaClass.getResource("/index.gmi").toURI()).toString()

    val conf = ServiceConf(
        address = "127.0.0.1",
        port = port,
        defaultMimeType = "text/plain",
        idleTimeout = 10000,
        virtualHosts = listOf(
            VirtualHost(
                host = host,
                root = resPath,
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
}
