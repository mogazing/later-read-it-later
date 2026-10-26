package com.example.pocketreborn.data.network

import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.net.URL

object NetworkSecurityUtils {

    fun isValidWebUrl(urlString: String): Boolean {
        return try {
            val uri = URI(urlString.trim())
            val scheme = uri.scheme?.lowercase()
            scheme in setOf("http", "https") &&
                !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null &&
                !uri.host.equals("localhost", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    fun isSafeUrl(urlString: String): Boolean {
        return validateNetworkDestination(urlString).isSuccess
    }

    fun validateNetworkDestination(urlString: String): Result<Unit> {
        if (!isValidWebUrl(urlString)) {
            return Result.failure(IllegalArgumentException("Only valid HTTP or HTTPS URLs are supported."))
        }
        return try {
            val url = URL(urlString)
            val host = url.host
            val addresses = try {
                InetAddress.getAllByName(host)
            } catch (e: UnknownHostException) {
                if (isValidWebUrl(urlString) && !host.equals("localhost", ignoreCase = true)) {
                    return Result.success(Unit)
                }
                return Result.failure(e)
            }
            if (addresses.any(::isPrivateAddress)) {
                Result.failure(SecurityException("The URL points to a restricted network address."))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isPrivateAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isAnyLocalAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) {
            return true
        }
        val bytes = address.address
        if (bytes.size == 4) {
            val ip1 = bytes[0].toInt() and 0xFF
            val ip2 = bytes[1].toInt() and 0xFF
            if (ip1 == 0 || ip1 == 10 || ip1 == 127 || ip1 >= 224) return true
            if (ip1 == 100 && ip2 in 64..127) return true
            if (ip1 == 169 && ip2 == 254) return true
            if (ip1 == 172 && ip2 in 16..31) return true
            if (ip1 == 192 && (ip2 == 0 || ip2 == 168)) return true
            if (ip1 == 198 && ip2 in setOf(18, 19, 51)) return true
            if (ip1 == 203 && ip2 == 0 && (bytes[2].toInt() and 0xFF) == 113) return true
        }
        return false
    }

    fun extractUrlFromSharedText(text: String): String? {
        return extractUrlsFromSharedText(text).singleOrNull()
    }

    fun extractUrlsFromSharedText(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val urlRegex = Regex("""https?://[^\s<>"'{}|\\^`]+""")
        return urlRegex.findAll(text)
            .map { trimExternalPunctuation(it.value) }
            .filter(::isValidWebUrl)
            .map(::normalizeUrl)
            .distinct()
            .toList()
    }

    private fun trimExternalPunctuation(candidate: String): String {
        var url = candidate.trim().trimEnd('.', ',', '!', ';', ':')
        while (url.endsWith(')') && url.count { it == ')' } > url.count { it == '(' }) {
            url = url.dropLast(1)
        }
        while (url.endsWith(']') && url.count { it == ']' } > url.count { it == '[' }) {
            url = url.dropLast(1)
        }
        return url
    }

    fun normalizeUrl(url: String): String {
        return try {
            val uri = URI(url.trim()).normalize()
            val asciiUrl = uri.toASCIIString().substringBefore('#')
            val schemeEnd = asciiUrl.indexOf(':')
            if (schemeEnd <= 0) asciiUrl else uri.scheme.lowercase() + asciiUrl.substring(schemeEnd)
        } catch (_: Exception) {
            url.trim()
        }
    }
}
