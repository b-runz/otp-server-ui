package one.otpserverui

import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class HealthCheckTest {
    @Test
    fun `GET health returns 200 ok`() = runTest {
        testApplication {
            application { module() }
            val response = client.get("/health")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.bodyAsText()).isEqualTo("ok")
        }
    }
}
