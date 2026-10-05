package one.otpserverui

import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.io.path.toPath
import kotlinx.coroutines.test.runTest
import one.otpserverui.api.GeocodeCandidate
import one.otpserverui.api.GeocodeClient
import org.junit.jupiter.api.Test

private class StaticFilesFakeGeocodeClient : GeocodeClient {
    override suspend fun search(query: String): List<GeocodeCandidate> = emptyList()
}

private fun testEngine(): one.otpserverui.routing.RoutingEngine {
    val fixturePath = checkNotNull(object {}.javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
    val loaded = GraphLoader.load(fixturePath)
    return one.otpserverui.routing.RoutingEngine(loaded.graph, loaded.transitRepository, loaded.transferRepository)
}

class StaticFilesTest {
    @Test
    fun `GET slash serves the built frontend's index html when staticDir exists`() = runTest {
        val staticDir = Files.createTempDirectory("otp-server-ui-static-test")
        Files.writeString(staticDir.resolve("index.html"), "<!doctype html><title>stub</title>")
        testApplication {
            application { module(testEngine(), emptyList(), StaticFilesFakeGeocodeClient(), staticDir) }
            val response = client.get("/")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.bodyAsText()).contains("stub")
        }
    }

    @Test
    fun `a missing staticDir does not prevent the API routes from starting`() = runTest {
        val missingDir = Files.createTempDirectory("otp-server-ui-static-test").resolve("does-not-exist")
        testApplication {
            application { module(testEngine(), emptyList(), StaticFilesFakeGeocodeClient(), missingDir) }
            val response = client.get("/health")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        }
    }
}
