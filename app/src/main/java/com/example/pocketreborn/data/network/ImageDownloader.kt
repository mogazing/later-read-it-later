package com.example.pocketreborn.data.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class ImageDownloader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
) {

    suspend fun downloadImage(articleId: String, imageUrl: String): Result<String> = withContext(Dispatchers.IO) {
        if (!NetworkSecurityUtils.isValidWebUrl(imageUrl)) {
            return@withContext Result.failure(IllegalArgumentException("Unsupported image URL."))
        }

        val imagesDir = File(context.filesDir, "article_images/$articleId")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }

        val tempFile = File(imagesDir, "temp_${UUID.randomUUID()}.tmp")
        val extension = getExtensionFromUrl(imageUrl)
        val finalFile = File(imagesDir, "img_${UUID.randomUUID().toString().take(8)}.$extension")

        try {
            var currentUrl = imageUrl
            repeat(MAX_REDIRECTS + 1) { redirectCount ->
                val destinationCheck = NetworkSecurityUtils.validateNetworkDestination(currentUrl)
                if (destinationCheck.isFailure) {
                    return@withContext Result.failure(
                        destinationCheck.exceptionOrNull() ?: SecurityException("Unsafe image URL destination.")
                    )
                }

                val request = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", "LaterApp/1.0 ImageDownloader")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isRedirect) {
                        if (redirectCount == MAX_REDIRECTS) {
                            return@withContext Result.failure(IOException("Too many image redirects."))
                        }
                        val location = response.header("Location")
                            ?: return@withContext Result.failure(IOException("Image redirect has no destination."))
                        currentUrl = response.request.url.resolve(location)?.toString()
                            ?: return@withContext Result.failure(IllegalArgumentException("Invalid image redirect destination."))
                        return@use
                    }

                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IOException("HTTP ${response.code} downloading image."))
                    }
                    val body = response.body
                        ?: return@withContext Result.failure(IOException("Empty image body."))
                    if (body.contentLength() > MAX_IMAGE_BYTES) {
                        return@withContext Result.failure(IOException("Image exceeds the 15 MB download limit."))
                    }
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var totalBytes = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                totalBytes += count
                                if (totalBytes > MAX_IMAGE_BYTES) {
                                    throw IOException("Image exceeds the 15 MB download limit.")
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }

                    if (tempFile.renameTo(finalFile)) {
                        return@withContext Result.success(finalFile.absolutePath)
                    }
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                    return@withContext Result.success(finalFile.absolutePath)
                }
            }
            Result.failure(IOException("Could not retrieve the image."))
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    private fun getExtensionFromUrl(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.endsWith(".png") -> "png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "jpg"
            lower.endsWith(".webp") -> "webp"
            lower.endsWith(".gif") -> "gif"
            lower.endsWith(".svg") -> "svg"
            else -> "jpg"
        }
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private const val MAX_IMAGE_BYTES = 15 * 1024 * 1024L
    }
}
