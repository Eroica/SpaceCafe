package earth.groundctrl.spacecafe.handlers

import earth.groundctrl.spacecafe.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.nio.file.FileSystems
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable

private val logger = KotlinLogging.logger {}

class GeminiHandler(conf: ServiceConf) : ProtocolHandler(conf) {
    override fun handle(req: String, uri: URI, remoteAddr: String): Response {
        val host = uri.host
        val path = uri.path.decode()
        val vhost = uri.toVirtualHost(vHosts)

        return when {
            vhost == null -> {
                logger.debug { "vhost $host not found in $vHosts" }
                ProxyRequestRefused(req)
            }
            uri.userInfo != null -> {
                logger.debug { "user info present" }
                BadRequest(req, "Userinfo component is not allowed")
            }
            !path.isValidPath() -> {
                logger.debug { "invalid path, out of root" }
                BadRequest(req)
            }
            uri.normalize() != uri -> {
                logger.debug { "redirect to normalize uri" }
                PermanentRedirect(req, uri.normalize().toString())
            }
            else -> {
                val (root, rawPath) = vhost.getRoot(path)
                val resource = FileSystems.getDefault().getPath(root, rawPath).normalize()
                val cgi = vhost.getCgi(resource) ?: vhost.getCgi(resource.resolve(vhost.indexFile))

                logger.debug { "requesting: '$resource', cgi is '$cgi'" }

                when {
                    cgi?.let { !it.isDirectory() && it.isExecutable() } == true -> {
                        logger.debug { "is cgi, will execute" }

                        val cgiFile = cgi.toFile()
                        val queryString = uri.query ?: ""
                        val pathInfo = if (cgiFile >= resource.toFile()) {
                            ""
                        } else {
                            "/" + resource.subpath(cgiFile.toPath().nameCount, resource.nameCount).toString()
                        }

                        Cgi(
                            req,
                            filename = cgiFile.toString(),
                            queryString = queryString,
                            pathInfo = pathInfo,
                            scriptName = cgiFile.name,
                            host = vhost.host,
                            port = conf.port.toString(),
                            remoteAddr = remoteAddr,
                            vhEnv = vhost.environment ?: emptyMap()
                        )
                    }
                    !resource.toFile().exists() -> {
                        logger.debug { "no resource" }
                        NotFound(req)
                    }
                    resource.toFile().exists() && !resource.toFile().canRead() -> {
                        logger.debug { "no read permissions" }
                        NotFound(req)
                    }
                    resource.toFile().name.startsWith(".") -> {
                        logger.debug { "dot file, ignored request" }
                        NotFound(req)
                    }
                    resource.toFile().isFile -> {
                        Success(
                            req,
                            meta = guessMimeType(resource, vhost.geminiParams, vhost.size, vhost.getCache(resource)),
                            bodySize = resource.toFile().length(),
                            bodyPath = resource
                        )
                    }
                    resource.toFile().isDirectory && !path.isEmpty() && !path.endsWith("/") -> {
                        logger.debug { "redirect directory" }
                        PermanentRedirect(req, "$uri/")
                    }
                    resource.toFile().isDirectory -> {
                        val dirFilePath = resource.resolve(vhost.indexFile)
                        val dirFile = dirFilePath.toFile()

                        if (dirFile.isFile && dirFile.canRead()) {
                            logger.debug { "serving index file: $dirFilePath" }
                            Success(
                                req,
                                meta = guessMimeType(dirFilePath, vhost.geminiParams, false, vhost.getCache(resource)),
                                bodySize = dirFile.length(),
                                bodyPath = dirFilePath
                            )
                        } else if (vhost.getDirectoryListing(resource)) {
                            logger.debug { "directory listing" }
                            DirListing(
                                req,
                                meta = GEMINI_MIME_TYPE,
                                bodyPath = resource,
                                uriPath = path,
                                cache = vhost.getCache(resource)
                            )
                        } else {
                            NotFound(req)
                        }
                    }
                    else -> {
                        logger.debug {"default: other resource type" }
                        NotFound(req)
                    }
                }
            }
        }
    }
}
