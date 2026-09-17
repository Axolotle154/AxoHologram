package org.axostudio.axohologram.feature.importer.web

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** HTTP client for AxoStudio share codes and direct export URLs. */
class WebImportClient(
    private val apiUrl: () -> String = { DEFAULT_API_URL }
) {
    fun fetchHologram(codeOrUrl: String): WebFetchResult {
        val url = resolveUrl(codeOrUrl)
            ?: return WebFetchResult.failure("The web code or URL is empty or invalid.")

        var connection: HttpURLConnection? = null
        return try {
            connection = URI(url).toURL().openConnection() as? HttpURLConnection
                ?: return WebFetchResult.failure("The import URL must use HTTP or HTTPS.", url)
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "AxoHologram/4.0.0")
            connection.setRequestProperty("Accept", "application/yaml, application/x-yaml, application/json, text/plain")

            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.readUtf8()?.take(180)?.replace(Regex("\\s+"), " ")
                val suffix = detail?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                WebFetchResult.failure("Web server returned HTTP $status$suffix", url)
            } else {
                val body = connection.inputStream.readUtf8()
                if (body.isBlank()) WebFetchResult.failure("The web server returned an empty hologram.", url)
                else WebFetchResult.success(url, body)
            }
        } catch (exception: Exception) {
            WebFetchResult.failure("Could not connect to the web server: ${exception.message ?: exception.javaClass.simpleName}", url)
        } finally {
            connection?.disconnect()
        }
    }

    fun resolveUrl(codeOrUrl: String): String? {
        val input = codeOrUrl.trim()
        if (input.isEmpty()) return null
        val supplied = runCatching { URI(input) }.getOrNull()
        if (supplied?.scheme.equals("http", ignoreCase = true) || supplied?.scheme.equals("https", ignoreCase = true)) {
            return supplied?.toASCIIString()
        }
        // AXO-2875 is a share code, not a URL. 3.x expanded it to the API endpoint.
        if (!SHARE_CODE.matches(input)) return null
        val base = apiUrl().trim().trimEnd('/')
        val endpoint = runCatching { URI(base) }.getOrNull()
        if (endpoint?.scheme?.lowercase() !in setOf("http", "https")) return null
        return "$base/${URLEncoder.encode(input, StandardCharsets.UTF_8)}"
    }

    private fun InputStream.readUtf8(): String = bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

    companion object {
        const val DEFAULT_API_URL = "https://axostudio.xyz/api/holograms"
        private val SHARE_CODE = Regex("[A-Za-z0-9_-]{1,128}")
    }
}

data class WebFetchResult(val url: String?, val body: String?, val error: String?) {
    companion object {
        fun success(url: String, body: String) = WebFetchResult(url, body, null)
        fun failure(error: String, url: String? = null) = WebFetchResult(url, null, error)
    }
}
