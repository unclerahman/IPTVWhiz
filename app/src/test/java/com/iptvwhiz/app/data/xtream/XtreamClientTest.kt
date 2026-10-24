package com.iptvwhiz.app.data.xtream

import com.iptvwhiz.app.data.ImportError
import com.iptvwhiz.app.data.ImportException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** A fake Xtream panel: there is no legitimate public one to test against. */
class XtreamClientTest {

    private val server = MockWebServer()
    private val client = XtreamClient(OkHttpClient(), Json { ignoreUnknownKeys = true; isLenient = true })
    private var authResponse = """{"user_info":{"auth":1,"status":"Active"},"server_info":{}}"""

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl?.queryParameter("password") != "pass") {
                    return MockResponse().setBody("""{"user_info":{"auth":0}}""")
                }
                return when (request.requestUrl?.queryParameter("action")) {
                    null -> MockResponse().setBody(authResponse)
                    "get_live_categories" -> MockResponse().setBody(
                        """[{"category_id":"1","category_name":"News"},{"category_id":2,"category_name":"Sports"}]""",
                    )
                    // Numbers as strings and ints, nulls and an unknown category, like real panels.
                    "get_live_streams" -> MockResponse().setBody(
                        """
                        [
                          {"num":2,"name":"Sport 1","stream_id":202,"stream_icon":"","epg_channel_id":null,"category_id":"2"},
                          {"num":"1","name":"News 1","stream_id":"101","stream_icon":"http://logo/n1.png","epg_channel_id":"news1.us","category_id":"1"},
                          {"name":"Mystery","stream_id":303,"category_id":"99"}
                        ]
                        """.trimIndent(),
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun credentials(password: String = "pass") =
        XtreamCredentials(server.url("/").toString(), "user", password)

    @Test
    fun `loads live channels sorted by number with categories`() = runTest {
        client.authenticate(credentials())
        val channels = client.liveChannels(credentials())
        assertEquals(listOf("News 1", "Sport 1", "Mystery"), channels.map { it.name })
        assertEquals("News", channels[0].category)
        assertEquals("Sports", channels[1].category)
        assertNull(channels[2].category)
        assertEquals("http://logo/n1.png", channels[0].logo)
        assertNull(channels[1].logo)
        assertEquals("news1.us", channels[0].epgChannelId)
    }

    @Test
    fun `wrong password is reported as wrong login`() = runTest {
        try {
            client.authenticate(credentials(password = "nope"))
            fail("expected an exception")
        } catch (e: ImportException) {
            assertEquals(ImportError.WrongLogin, e.error)
        }
    }

    @Test
    fun `expired account is reported with its status`() = runTest {
        authResponse = """{"user_info":{"auth":1,"status":"Expired"}}"""
        try {
            client.authenticate(credentials())
            fail("expected an exception")
        } catch (e: ImportException) {
            assertEquals(ImportError.AccountInactive("Expired"), e.error)
        }
    }

    @Test
    fun `stream and epg urls`() {
        val creds = XtreamCredentials("host.tv:8080", "u", "p w")
        assertEquals("http://host.tv:8080/live/u/p%20w/42.ts", client.streamUrl(creds, "42"))
        assertEquals("http://host.tv:8080/xmltv.php?username=u&password=p%20w", client.epgUrl(creds))
    }

    @Test
    fun `base url accepts pasted api links and sub paths`() {
        assertEquals("http://h/", XtreamClient.baseUrl("http://h:80/player_api.php?username=a").toString())
        assertEquals("https://h/panel", XtreamClient.baseUrl("https://h/panel/").toString())
        assertEquals("http://h/", XtreamClient.baseUrl("h").toString())
    }
}
