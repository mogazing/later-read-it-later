package com.example.pocketreborn.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ResolvedPageResult(
    val finalUrl: String,
    val htmlContent: String,
    val statusCode: Int
)

class UrlResolver(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    suspend fun resolveAndFetch(url: String): Result<ResolvedPageResult> = withContext(Dispatchers.IO) {
        val normalized = NetworkSecurityUtils.normalizeUrl(url)
        if (!NetworkSecurityUtils.isValidWebUrl(normalized)) {
            return@withContext Result.failure(IllegalArgumentException("URL destination is restricted or unsafe."))
        }

        try {
            var currentUrl = normalized
            repeat(MAX_REDIRECTS + 1) { redirectCount ->
                val destinationCheck = NetworkSecurityUtils.validateNetworkDestination(currentUrl)
                if (destinationCheck.isFailure) {
                    return@withContext Result.failure(
                        destinationCheck.exceptionOrNull()
                            ?: SecurityException("The URL or redirect points to a restricted network address.")
                    )
                }

                // Use a standard browser User-Agent to avoid HTTP 405 / 403 blocks from sites like Ars Technica / Vox
                val request = Request.Builder()
                    .url(currentUrl)
                    .get()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isRedirect) {
                        if (redirectCount == MAX_REDIRECTS) {
                            return@withContext Result.failure(IOException("Too many redirects."))
                        }
                        val location = response.header("Location")
                            ?: return@withContext Result.failure(IOException("Redirect response did not include a destination."))
                        val redirectUrl = response.request.url.resolve(location)
                            ?: return@withContext Result.failure(IllegalArgumentException("Invalid redirect destination."))
                        currentUrl = redirectUrl.toString()
                        return@use
                    }

                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IOException("HTTP Error ${response.code}: ${response.message}"))
                    }

                    val finalUrl = response.request.url.toString()
                    if (!NetworkSecurityUtils.isSafeUrl(finalUrl)) {
                        return@withContext Result.failure(SecurityException("Redirected to an unsafe host destination."))
                    }

                    val body = response.body
                        ?: return@withContext Result.failure(IOException("Empty response body."))

                    // Check content-type
                    val contentType = response.header("Content-Type")?.lowercase() ?: ""
                    if (contentType.isNotBlank() &&
                        !contentType.contains("text/html") &&
                        !contentType.contains("xhtml") &&
                        !contentType.contains("xml") &&
                        !contentType.contains("text/plain")
                    ) {
                        return@withContext Result.failure(IllegalArgumentException("Unsupported content type: $contentType"))
                    }

                    // Limit response reading to 10MB
                    val maxBytes = 10 * 1024 * 1024L
                    val html = body.source().apply { request(maxBytes) }.buffer.snapshot().utf8()

                    return@withContext Result.success(
                        ResolvedPageResult(
                            finalUrl = finalUrl,
                            htmlContent = html,
                            statusCode = response.code
                        )
                    )
                }
            }
            Result.failure(IOException("Could not retrieve the web page."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val MAX_REDIRECTS = 5
    }
}
