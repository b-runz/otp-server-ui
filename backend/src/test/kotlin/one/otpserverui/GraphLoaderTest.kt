package one.otpserverui

import com.google.common.truth.Truth.assertThat
import kotlin.io.path.toPath
import org.junit.jupiter.api.Test

class GraphLoaderTest {
    @Test
    fun `loads a real graph with a non-empty transit repository`() {
        val fixturePath = checkNotNull(javaClass.classLoader.getResource("tiny-fixture-graph.obj")).toURI().toPath()
        val loaded = GraphLoader.load(fixturePath)

        assertThat(loaded.graph).isNotNull()
        assertThat(loaded.transitRepository).isNotNull()
        assertThat(loaded.transferRepository).isNotNull()
    }
}
