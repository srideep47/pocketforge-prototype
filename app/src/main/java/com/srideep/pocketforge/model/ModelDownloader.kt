package com.srideep.pocketforge.model

import android.util.Log
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Progress for one model download, as the UI renders it. */
data class DownloadProgress(
    val modelId: String,
    val currentFile: String,
    val bytesDone: Long,
    val bytesTotal: Long,
    val finished: Boolean = false,
    val error: String? = null,
) {
    val fraction: Float
        get() = if (bytesTotal <= 0L) 0f else (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
}

/**
 * Fetches a catalog model into app storage.
 *
 * Downloads are resumable per file: a file already on disk at its full length is skipped,
 * and a partial one continues with a Range request. That matters on a phone — these are
 * multi-gigabyte transfers over conference wifi, and starting over because the screen
 * locked is not acceptable.
 */
class ModelDownloader(private val modelsDir: File) {

    fun download(model: CatalogModel): Flow<DownloadProgress> = flow {
        val target = model.directoryIn(modelsDir).apply { mkdirs() }
        // Cleared up front so an interrupted re-download cannot leave a stale marker
        // claiming the model is whole.
        model.completionMarkerIn(modelsDir).delete()

        // Ask for every size up front so the progress bar means something.
        val sizes = LinkedHashMap<String, Long>()
        for (file in model.requiredFiles) {
            currentCoroutineContext().ensureActive()
            sizes[file] = contentLengthOf(model.downloadUrl(file))
        }
        val total = sizes.values.sum().takeIf { it > 0L } ?: model.approxBytes

        var done = 0L
        for ((file, size) in sizes) {
            currentCoroutineContext().ensureActive()
            val destination = File(target, file)

            if (destination.length() == size && size > 0L) {
                done += size
                emit(DownloadProgress(model.id, file, done, total))
                continue
            }

            val already = destination.length().takeIf { it in 1 until size } ?: 0L
            if (already == 0L) destination.delete()

            var fileDone = already
            fetch(model.downloadUrl(file), destination, from = already) { chunk ->
                fileDone += chunk
                emit(DownloadProgress(model.id, file, done + fileDone, total))
            }
            done += maxOf(fileDone, size)
        }

        model.completionMarkerIn(modelsDir).writeText(total.toString())
        emit(DownloadProgress(model.id, "", total, total, finished = true))
    }.flowOn(Dispatchers.IO)

    /** Removes a downloaded model and its scratch files. */
    fun delete(model: CatalogModel): Boolean = model.directoryIn(modelsDir).deleteRecursively()

    private fun contentLengthOf(url: String): Long {
        val connection = open(url).apply { requestMethod = "HEAD" }
        return try {
            connection.connect()
            connection.contentLengthLong.coerceAtLeast(0L)
        } catch (e: IOException) {
            Log.w(TAG, "could not size $url", e)
            0L
        } finally {
            connection.disconnect()
        }
    }

    private suspend inline fun fetch(
        url: String,
        destination: File,
        from: Long,
        onChunk: suspend (Int) -> Unit,
    ) {
        val connection = open(url).apply {
            if (from > 0L) setRequestProperty("Range", "bytes=$from-")
        }
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code for $url")

            // A server that ignored the Range header sends 200 and the whole body; append
            // only when it actually honoured the resume.
            val appending = from > 0L && code == HttpURLConnection.HTTP_PARTIAL
            if (from > 0L && !appending) destination.delete()

            connection.inputStream.use { input ->
                java.io.FileOutputStream(destination, appending).use { output ->
                    val buffer = ByteArray(1 shl 16)
                    var lastEmit = System.currentTimeMillis()
                    var sinceEmit = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        sinceEmit += read
                        // Emitting per 64 KB chunk would flood the UI; a few times a
                        // second is all a progress bar can show.
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > PROGRESS_INTERVAL_MS) {
                            onChunk(sinceEmit)
                            sinceEmit = 0
                            lastEmit = now
                        }
                    }
                    if (sinceEmit > 0) onChunk(sinceEmit)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PocketForge")
        }

    private companion object {
        const val TAG = "ModelDownloader"
        const val PROGRESS_INTERVAL_MS = 250L
    }
}
