package com.nuvio.tv.ui.screens.anidrama

import android.util.Log
import com.nuvio.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Small HTTP helpers for the Ani-drama tab (AniList, MAL-Dubs, arm.haglund.dev, TMDB). */
internal object AniDramaHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

internal suspend fun httpGetText(url: String): String = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
    AniDramaHttp.client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("HTTP ${response.code} for $url")
        response.body?.string().orEmpty()
    }
}

internal suspend fun httpPostJson(url: String, body: String): String = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
        .build()
    AniDramaHttp.client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("HTTP ${response.code} for $url")
        response.body?.string().orEmpty()
    }
}

/** Logcat with a tag, used like the phone app's logger: log.w { "message" }. */
internal class AniDramaLog(private val tag: String) {
    fun w(message: () -> String) {
        Log.w(tag, message())
    }
}

/** TMDB v3 URL with the app's key and URL-encoded query values. */
internal fun buildTmdbUrl(endpoint: String, apiKey: String, query: Map<String, String>): String = buildString {
    append("https://api.themoviedb.org/3/")
    append(endpoint.trimStart('/'))
    append("?api_key=")
    append(URLEncoder.encode(apiKey, "UTF-8"))
    query.forEach { (key, value) ->
        append('&')
        append(key)
        append('=')
        append(URLEncoder.encode(value, "UTF-8").replace("+", "%20"))
    }
}

internal object AniDramaTmdb {
    fun apiKey(): String = BuildConfig.TMDB_API_KEY

    /** TMDB language, e.g. "en-US", from the TV's language. */
    fun language(): String {
        if (com.nuvio.tv.fenrir.FenrirSpanishMode.enabled.value) return "es-MX"
        val locale = Locale.getDefault()
        val lang = locale.language.ifBlank { "en" }
        return if (locale.country.isNotBlank()) "$lang-${locale.country}" else lang
    }
}
