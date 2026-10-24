package com.iptvwhiz.app.data.xtream

import com.iptvwhiz.app.data.ImportError
import com.iptvwhiz.app.data.ImportException
import com.iptvwhiz.app.data.httpError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class XtreamCredentials(val server: String, val username: String, val password: String)

data class XtreamChannel(
    val streamId: String,
    val number: Int?,
    val name: String,
    val logo: String?,
    val epgChannelId: String?,
    val category: String?,
)

/**
 * Minimal Xtream Codes "player_api" client for live TV.
 * Panels are inconsistent (numbers as strings, nulls, HTML error pages), so everything is parsed leniently.
 */
class XtreamClient(private val http: OkHttpClient, private val json: Json) {

    /** Checks the login. Throws [ImportException] with [ImportError.WrongLogin] or [ImportError.AccountInactive]. */
    suspend fun authenticate(credentials: XtreamCredentials) {
        val root = getJson(apiUrl(credentials)) as? JsonObject
            ?: throw ImportException(ImportError.WrongLogin)
        val userInfo = root["user_info"] as? JsonObject
            ?: throw ImportException(ImportError.WrongLogin)
        if (userInfo.string("auth") != "1") throw ImportException(ImportError.WrongLogin)
        val status = userInfo.string("status")
        if (status != null && !status.equals("Active", ignoreCase = true)) {
            throw ImportException(ImportError.AccountInactive(status))
        }
    }

    suspend fun liveChannels(credentials: XtreamCredentials): List<XtreamChannel> {
        val categories = (getJson(apiUrl(credentials, "get_live_categories")) as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .associate { it.string("category_id").orEmpty() to it.string("category_name").orEmpty() }

        val streams = getJson(apiUrl(credentials, "get_live_streams")) as? JsonArray
            ?: throw ImportException(ImportError.NotAPlaylist)

        return streams.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("stream_id") ?: return@mapNotNull null
            XtreamChannel(
                streamId = id,
                number = obj.string("num")?.toIntOrNull(),
                name = obj.string("name")?.trim().orEmpty().ifEmpty { "Channel $id" },
                logo = obj.string("stream_icon")?.takeIf { it.isNotBlank() },
                epgChannelId = obj.string("epg_channel_id")?.takeIf { it.isNotBlank() },
                category = categories[obj.string("category_id")]?.takeIf { it.isNotBlank() },
            )
        }.sortedWith(compareBy(nullsLast<Int>()) { it.number })
    }

    fun streamUrl(credentials: XtreamCredentials, streamId: String, extension: String = "ts"): String =
        baseUrl(credentials.server).newBuilder()
            .addPathSegment("live")
            .addPathSegment(credentials.username)
            .addPathSegment(credentials.password)
            .addPathSegment("$streamId.$extension")
            .build()
            .toString()

    fun epgUrl(credentials: XtreamCredentials): String =
        baseUrl(credentials.server).newBuilder()
            .addPathSegment("xmltv.php")
            .addQueryParameter("username", credentials.username)
            .addQueryParameter("password", credentials.password)
            .build()
            .toString()

    private fun apiUrl(credentials: XtreamCredentials, action: String? = null): HttpUrl =
        baseUrl(credentials.server).newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", credentials.username)
            .addQueryParameter("password", credentials.password)
            .apply { if (action != null) addQueryParameter("action", action) }
            .build()

    private suspend fun getJson(url: HttpUrl): JsonElement? = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw ImportException(httpError(response.code))
            val body = response.body?.string().orEmpty()
            runCatching { json.parseToJsonElement(body) }.getOrNull()
        }
    }

    companion object {
        /** Accepts "host:port", "http://host:port/", or a pasted ".../player_api.php?..." link. */
        fun baseUrl(server: String): HttpUrl {
            var text = server.trim()
            if (!text.contains("://")) text = "http://$text"
            val url = text.toHttpUrlOrNull() ?: throw ImportException(ImportError.InvalidAddress)
            val segments = url.pathSegments.filter { it.isNotEmpty() }
                .let { if (it.lastOrNull()?.endsWith(".php") == true) it.dropLast(1) else it }
            return url.newBuilder()
                .query(null)
                .fragment(null)
                .encodedPath("/")
                .apply { segments.forEach { addPathSegment(it) } }
                .build()
        }
    }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
