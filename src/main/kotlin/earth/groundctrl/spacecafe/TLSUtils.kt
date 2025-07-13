package earth.groundctrl.spacecafe

import io.github.oshai.kotlinlogging.KotlinLogging
import tlschannel.SniSslContextFactory
import java.io.FileInputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/* Different to SpaceBeans, this mostly relies on https://github.com/marianobarrios/tls-channel
   to set up a TLS channel. A custom X509ExtendedKeyManager class is not necessary.
   I kept a "sniKeyManager" factory for a similar API. "genSSLContext" works on VirtualHost
   directly instead of loading certificates first. */

private val logger = KotlinLogging.logger {}

fun genSSLContext(vhost: VirtualHost): SSLContext {
    val ks = KeyStore.getInstance("JKS")
    FileInputStream(vhost.keyStore.path).use {
        ks.load(it, vhost.keyStore.password.toCharArray())
    }

    val cert = ks.getCertificate(vhost.keyStore.alias) as X509Certificate
    logger.info { "Certificate for ${vhost.host} - serial-no: ${cert.serialNumber}, final-date: ${cert.notAfter}" }

    val kmFac = KeyManagerFactory.getInstance("SunX509")
    kmFac.init(ks, vhost.keyStore.password.toCharArray())

    val tmFac = TrustManagerFactory.getInstance("SunX509")
    tmFac.init(ks)

    val context = SSLContext.getInstance("TLS").apply {
        init(kmFac.keyManagers, tmFac.trustManagers, SecureRandom())
    }

    return context
}

fun sniKeyManager(sslContexts: Map<String, SSLContext>): SniSslContextFactory {
    return SniSslContextFactory { sniServerName ->
        if (!sniServerName.isPresent) {
            return@SniSslContextFactory Optional.empty()
        }

        val name = sniServerName.get()
        if (name !is SNIHostName) {
            return@SniSslContextFactory Optional.empty()
        }

        return@SniSslContextFactory sslContexts[name.asciiName]?.let {
            Optional.of(it)
        } ?: Optional.empty()
    }
}
