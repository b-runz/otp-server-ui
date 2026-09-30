package one.otpserverui

import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.io.path.toPath
import kotlinx.coroutines.test.runTest
import one.otpserverui.api.GeocodeCandidate
import one.otpserverui.api.GeocodeClient
import org.junit.jupiter.api.Test

private fun testEngine(): one.otpserverui.routing.RoutingEngine {
    val fixturePath = checkNotNull(object {}.javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
    val loaded = GraphLoader.load(fixturePath)
    return one.otpserverui.routing.RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)
}

private class FakeGeocodeClient(private val results: List<GeocodeCandidate>) : GeocodeClient {
    override suspend fun search(query: String): List<GeocodeCandidate> = results
}

class HealthCheckTest {
    @Test
    fun `GET health returns 200 ok`() = runTest {
        testApplication {
            application { module(testEngine(), emptyList(), FakeGeocodeClient(emptyList())) }
            val response = client.get("/health")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.bodyAsText()).isEqualTo("ok")
        }
    }
}
