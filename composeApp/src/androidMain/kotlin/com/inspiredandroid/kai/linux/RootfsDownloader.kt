package com.inspiredandroid.kai.linux

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val BUFFER_SIZE = 64 * 1024

/**
 * Streams a rootfs tarball to disk, trying each candidate URL in turn.
 *
 * Alpine publishes the same minirootfs on several mirrors, so a mirror that is
 * down or rate-limiting costs a retry rather than the install. Debian resolves
 * to a single Linux Containers URL, which simply means a one-element list.
 */
class RootfsDownloader(private val httpClient: HttpClient) {

    /**
     * Cancellable through the calling coroutine. A failed URL deletes its partial
     * file and resets progress so the UI does not show a bar walking backwards.
     */
    suspend fun download(
        urls: List<String>,
        targetFile: File,
        onProgress: (Float) -> Unit,
    ) {
        require(urls.isNotEmpty()) { "No download URL for this rootfs" }
        targetFile.parentFile?.mkdirs()
        var lastError: Exception? = null
        for ((index, url) in urls.withIndex()) {
            try {
                downloadFrom(url, targetFile, onProgress)
                return
            } catch (e: CancellationException) {
                targetFile.delete()
                throw e
            } catch (e: Exception) {
                lastError = e
                targetFile.delete()
                if (index < urls.lastIndex) onProgress(0f)
            }
        }
        throw IOException("All rootfs download mirrors failed", lastError)
    }

    private suspend fun downloadFrom(
        url: String,
        targetFile: File,
        onProgress: (Float) -> Unit,
    ) {
        httpClient.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) {
                throw IOException("HTTP ${response.status.value} from $url")
            }
            val totalBytes = response.contentLength() ?: -1L
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(BUFFER_SIZE)
            var downloadedBytes = 0L

            FileOutputStream(targetFile).use { output ->
                while (!channel.isClosedForRead) {
                    currentCoroutineContext().ensureActive()
                    val bytesRead = channel.readAvailable(buffer)
                    if (bytesRead <= 0) break
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        onProgress((downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }
                }
            }
            onProgress(1f)
        }
        validateArchive(targetFile)
    }

    /**
     * Some mirrors answer 200 with an HTML portal page (observed: HuaweiCloud LXC mirror),
     * which previously got stored as `rootfs.tar.xz` and failed later with
     * "input is not in the xz format". Check the archive magic before accepting it.
     */
    private fun validateArchive(file: File) {
        val magic = ByteArray(6)
        val read = file.inputStream().use { input ->
            var offset = 0
            while (offset < magic.size) {
                val count = input.read(magic, offset, magic.size - offset)
                if (count <= 0) break
                offset += count
            }
            offset
        }
        val valid = when {
            file.name.endsWith(".tar.xz") -> read == 6 && magic.contentEquals(XZ_MAGIC)
            file.name.endsWith(".tar.gz") ->
                read >= 2 && magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte()
            else -> true
        }
        if (!valid) {
            throw IOException("Downloaded archive is not a valid ${file.name} (bad magic bytes)")
        }
    }

    private companion object {
        val XZ_MAGIC = byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00)
    }
}
