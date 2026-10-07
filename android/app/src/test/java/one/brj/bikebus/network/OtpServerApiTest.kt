package one.brj.bikebus.network

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OtpServerApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: OtpServerApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = OtpServerApi(OkHttpClient(), server.url("/").toString().removeSuffix("/"), authToken = "test-token")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun request() = SearchRequestDto(
        mode = "bring_bike", timeMode = "depart_at",
        originLat = 0.0, originLon = 0.0, destinationLat = 0.0, destinationLon = 0.0,
        dateTimeIso = "2026-01-01T10:00:00Z",
    )

    @Test
    fun `search sends the auth token header`() = runTest {
        server.enqueue(MockResponse().setBody("""{"itineraries":[],"notice":null}""").setResponseCode(200))
        api.search(request())
        assertEquals("test-token", server.takeRequest().getHeader("X-Auth-Token"))
    }

    @Test
    fun `search maps a successful response`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"itineraries":[{"legs":[{"mode":"WALK","distanceMeters":100.0,"durationSeconds":60.0,""" +
                    """"fromLat":1.0,"fromLon":2.0,"toLat":3.0,"toLon":4.0,"fromName":"A","toName":"B",""" +
                    """"routeShortName":null,"departureEpochSecond":1735689600}],"exceedsBikeLimit":false,""" +
                    """"hasLongWalkEgress":false}],"notice":"test notice"}"""
            ).setResponseCode(200)
        )
        val result = api.search(request())
        check(result is SearchResult.Success)
        assertEquals(1, result.itineraries.size)
        assertEquals("test notice", result.notice)
    }

    @Test
    fun `search maps a known error code from a 422 response`() = runTest {
        server.enqueue(MockResponse().setBody("""{"error":"no_coverage"}""").setResponseCode(422))
        val result = api.search(request())
        check(result is SearchResult.Error)
        assertEquals("no_coverage", result.code)
    }

    @Test
    fun `search falls back to unknown_error on an unparseable error body`() = runTest {
        // Mirrors a bare, body-less 403 -- both an invalid token and rate-limiting return
        // exactly this shape by design (see the OCI deployment spec's security rationale).
        server.enqueue(MockResponse().setBody("").setResponseCode(403))
        val result = api.search(request())
        check(result is SearchResult.Error)
        assertEquals("unknown_error", result.code)
    }

    @Test
    fun `nearbyRoutes maps a successful response`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"routes":[{"routeGtfsId":"RUT:1","routeShortName":"1A","stopIds":["S1"],"distanceMeters":42.0}]}"""
            ).setResponseCode(200)
        )
        val routes = api.nearbyRoutes(lat = 55.0, lon = 12.0)
        assertEquals(1, routes.size)
        assertEquals("1A", routes.first().routeShortName)
        assertEquals("test-token", server.takeRequest().getHeader("X-Auth-Token"))
    }

    @Test
    fun `connect maps a known error code`() = runTest {
        server.enqueue(MockResponse().setBody("""{"error":"unreachable"}""").setResponseCode(422))
        val result = api.connect(
            ConnectRequestDto(
                originLat = 0.0, originLon = 0.0, destinationLat = 0.0, destinationLon = 0.0,
                routeGtfsId = "RUT:1", routeStopIds = listOf("S1"),
                timeMode = "depart_at", dateTimeIso = "2026-01-01T10:00:00Z",
            )
        )
        check(result is ConnectOutcome.Error)
        assertEquals("unreachable", result.code)
        assertEquals("test-token", server.takeRequest().getHeader("X-Auth-Token"))
    }
}
