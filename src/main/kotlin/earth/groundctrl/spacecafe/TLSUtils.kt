package earth.groundctrl.spacecafe

import java.io.FileInputStream
import java.net.Socket
import java.security.KeyStore
import java.security.KeyStore.PrivateKeyEntry
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*
import javax.net.ssl.StandardConstants.SNI_HOST_NAME

class SniKeyManager private constructor(
    private val keyManager: X509ExtendedKeyManager,
    private val defaultAlias: String
) : X509ExtendedKeyManager() {
    companion object {
        operator fun invoke(keyManagerFactory: KeyManagerFactory, defaultAlias: String): SniKeyManager {
            val keyManager = keyManagerFactory.keyManagers
                .filterIsInstance<X509ExtendedKeyManager>()
                .firstOrNull()
                ?: throw RuntimeException("Failed to init SNI")

            return SniKeyManager(keyManager, defaultAlias)
        }
    }

    override fun getClientAliases(
        keyType: String?,
        issuers: Array<out Principal?>?
    ): Array<out String?>? {
        throw UnsupportedOperationException()
    }

    override fun chooseClientAlias(
        keyType: Array<out String?>?,
        issuers: Array<out Principal?>?,
        socket: Socket?
    ): String? {
        throw UnsupportedOperationException()
    }

    override fun chooseEngineClientAlias(
        keyType: Array<out String?>?,
        issuers: Array<out Principal?>?,
        engine: SSLEngine?
    ): String? {
        throw UnsupportedOperationException()
    }

    override fun getServerAliases(
        keyType: String?,
        issuers: Array<out Principal?>?
    ): Array<out String?>? {
        return keyManager.getServerAliases(keyType, issuers)
    }

    override fun chooseServerAlias(
        keyType: String?,
        issuers: Array<out Principal?>?,
        socket: Socket?
    ): String? {
        return keyManager.chooseServerAlias(keyType, issuers, socket)
    }

    override fun chooseEngineServerAlias(
        keyType: String?,
        issuers: Array<out Principal?>?,
        engine: SSLEngine
    ): String? {
        val sniHost = (engine.handshakeSession as ExtendedSSLSession).requestedServerNames
            .filterIsInstance<SNIHostName>()
            .firstOrNull { it.type == SNI_HOST_NAME }
            ?.asciiName

        return sniHost?.takeIf {
            getCertificateChain(it) != null && getPrivateKey(it) != null
        } ?: defaultAlias
    }

    override fun getCertificateChain(alias: String?): Array<out X509Certificate?>? {
        return keyManager.getCertificateChain(alias)
    }

    override fun getPrivateKey(alias: String?): PrivateKey? {
        return keyManager.getPrivateKey(alias)
    }
}

fun loadCert(
    path: String,
    alias: String,
    password: String
): Pair<X509Certificate, PrivateKey> {
    FileInputStream(path).use {
        val ks = KeyStore.getInstance("JKS")
        ks.load(it, password.toCharArray())

        val cert = ks.getCertificate(alias) as X509Certificate
        val privateKey = (ks.getEntry(
            alias, KeyStore.PasswordProtection(password.toCharArray())
        ) as PrivateKeyEntry).privateKey

        return Pair(cert, privateKey)
    }
}

fun genSSLContext(
    certs: Map<String, Pair<X509Certificate, PrivateKey>>
): SSLContext {
    val ks = KeyStore.getInstance("JKS")
    ks.load(null, "secret".toCharArray())
    certs.forEach { (hostname, pair) ->
        val (cert, pk) = pair
        ks.setKeyEntry(
            hostname,
            pk,
            "secret".toCharArray(),
            arrayOf(cert)
        )
    }

    val tmFac = TrustManagerFactory.getInstance("SunX509")
    tmFac.init(ks)

    val kmFac = KeyManagerFactory.getInstance("SunX509")
    kmFac.init(ks, "secret".toCharArray())

    val sniKeyManager = SniKeyManager(kmFac, "localhost")

    val ctx = SSLContext.getInstance("TLSv1.2")
    ctx.init(
        arrayOf(sniKeyManager),
        tmFac.trustManagers,
        SecureRandom()
    )

    return ctx
}
