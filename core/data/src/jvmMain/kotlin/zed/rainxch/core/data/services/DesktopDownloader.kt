package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import zed.rainxch.core.data.network.ProxyManager
import zed.rainxch.core.domain.model.DownloadProgress
import zed.rainxch.core.domain.model.ProxyConfig
import zed.rainxch.core.domain.network.Downloader
import java.io.File
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DesktopDownloader(
    private val files: FileLocationsProvider,
    private val proxyManager: ProxyManager = ProxyManager,
) : Downloader {
    private val activeDownloads = ConcurrentHashMap<String, Call>()
    private val nameToId = ConcurrentHashMap<String, String>()

    private fun buildClient(): OkHttpClient {
        Authenticator.setDefault(null)

        return OkHttpClient
            .Builder()
            .apply {
                when (val config = proxyManager.currentProxyConfig.value) {
                    is ProxyConfig.None -> {
                        proxy(Proxy.NO_PROXY)
                    }

                    is ProxyConfig.System -> {}

                    is ProxyConfig.Http -> {
                        proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(config.host, config.port)))
                        if (config.username != null && config.password != null) {
                            proxyAuthenticator { _, response ->
                                response.request
                                    .newBuilder()
                                    .header(
                                        "Proxy-Authorization",
                                        Credentials.basic(config.username!!, config.password!!),
                                    ).build()
                            }
                        }
                    }

                    is ProxyConfig.Socks -> {
                        proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(config.host, config.port)))
                        if (config.username != null && config.password != null) {
                            Authenticator.setDefault(
                                object : Authenticator() {
                                    override fun getPasswordAuthentication() =
                                        PasswordAuthentication(
                                            config.username,
                                            config.password!!.toCharArray(),
                                        )
                                },
                            )
                        }
                    }
                }
            }.build()
    }

    override fun download(
        url: String,
        suggestedFileName: String?,
    ): Flow<DownloadProgress> =
        flow {
            val client = buildClient()

            val dir = File(files.userDownloadsDir())
            if (!dir.exists()) dir.mkdirs()

            val rawName =
                suggestedFileName?.takeIf { it.isNotBlank() }
                    ?: url
                        .substringAfterLast('/')
                        .substringBefore('?')
                        .substringBefore('#')
                        .ifBlank { "asset-${UUID.randomUUID()}" }
            val safeName = rawName.substringAfterLast('/').substringAfterLast('\\')
            require(safeName.isNotBlank() && safeName != "." && safeName != "..") {
                "Invalid file name: $rawName"
            }

            val downloadId = UUID.randomUUID().toString()
            val previous = nameToId.putIfAbsent(safeName, downloadId)
            if (previous != null) {
                throw IllegalStateException("A download for '$safeName' is already in progress")
            }

            val destination = File(dir, safeName)
            if (destination.exists()) {
                Logger.d { "Deleting existing file before download: ${destination.absolutePath}" }
                destination.delete()
            }

            Logger.d { "Starting download: $url" }

            val request = Request.Builder().url(url).build()
            val call = client.newCall(request)
            activeDownloads[downloadId] = call

            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw kotlinx.io.IOException("Unexpected code ${response.code}")
                    }

                    val body = response.body
                    val contentLength = body.contentLength()
                    val total = if (contentLength > 0) contentLength else null

                    body.byteStream().use { input ->
                        destination.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var downloaded: Long = 0
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloaded += bytesRead
                                val percent =
                                    if (total != null) ((downloaded * 100L) / total).toInt() else null
                                emit(DownloadProgress(downloaded, total, percent))
                            }
                        }
                    }

                    if (destination.exists() && destination.length() > 0) {
                        Logger.d { "Download complete: ${destination.absolutePath}" }
                        val finalDownloaded = destination.length()
                        val finalPercent =
                            if (total != null) ((finalDownloaded * 100L) / total).toInt() else 100
                        emit(DownloadProgress(finalDownloaded, total, finalPercent))
                    } else {
                        throw IllegalStateException("File not ready after download: ${destination.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                destination.delete()
                Logger.e(e) { "Download failed" }
                throw e
            } finally {
                activeDownloads.remove(downloadId)
                nameToId.remove(safeName)
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun saveToFile(
        url: String,
        suggestedFileName: String?,
    ): String =
        withContext(Dispatchers.IO) {
            val rawName =
                suggestedFileName?.takeIf { it.isNotBlank() }
                    ?: url
                        .substringAfterLast('/')
                        .substringBefore('?')
                        .substringBefore('#')
                        .ifBlank { "asset-${UUID.randomUUID()}" }
            val safeName = rawName.substringAfterLast('/').substringAfterLast('\\')
            require(safeName.isNotBlank() && safeName != "." && safeName != "..") {
                "Invalid file name: $rawName"
            }

            val file = File(files.userDownloadsDir(), safeName)
            if (file.exists()) {
                Logger.d { "Deleting existing file before download: ${file.absolutePath}" }
                file.delete()
            }

            Logger.d { "saveToFile downloading file..." }
            download(url, suggestedFileName).collect { }

            file.absolutePath
        }

    override suspend fun getDownloadedFilePath(fileName: String): String? =
        withContext(Dispatchers.IO) {
            val file = File(files.userDownloadsDir(), fileName)
            if (file.exists() && file.length() > 0) file.absolutePath else null
        }

    override suspend fun cancelDownload(fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            var cancelled = false
            var deleted = false

            val downloadId = nameToId[fileName]
            if (downloadId != null) {
                activeDownloads[downloadId]?.let { call ->
                    if (!call.isCanceled()) {
                        call.cancel()
                        cancelled = true
                    }
                }
                activeDownloads.remove(downloadId)
                nameToId.remove(fileName)
            }

            val file = File(files.userDownloadsDir(), fileName)
            if (file.exists()) {
                deleted = file.delete()
            }

            cancelled || deleted
        }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}
