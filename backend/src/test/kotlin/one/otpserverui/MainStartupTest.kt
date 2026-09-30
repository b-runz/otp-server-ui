package one.otpserverui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MainStartupTest {
    @Test
    fun `a missing graph file fails startup with a clear message`() {
        val exception = assertThrows<IllegalStateException> {
            loadGraphOrFail(java.nio.file.Path.of("does-not-exist.obj"))
        }
        assertThat(exception.message).contains("does-not-exist.obj")
    }
}
