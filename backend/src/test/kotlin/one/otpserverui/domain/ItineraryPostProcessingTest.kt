package one.otpserverui.domain

import com.google.common.truth.Truth.assertThat
import java.time.OffsetDateTime
import one.otpserverui.model.Leg
import org.junit.jupiter.api.Test

private fun testLeg(mode: String, distanceMeters: Double): Leg {
    val time = OffsetDateTime.parse("2026-09-18T07:40:00Z")
    return Leg(
        mode = mode,
        distanceMeters = distanceMeters,
        durationSeconds = 60.0,
        fromLat = 0.0,
        fromLon = 0.0,
        toLat = 0.0,
        toLon = 0.0,
        fromName = null,
        toName = null,
        routeShortName = null,
        routeGtfsId = null,
        legGeometryPoints = null,
        departureTime = time,
        arrivalTime = time
    )
}

class ItineraryPostProcessingTest {
    @Test
    fun `drops a leg under 150 meters at the start`() {
        val legs = listOf(
            testLeg("WALK", 100.0),
            testLeg("BICYCLE", 500.0),
            testLeg("WALK", 500.0)
        )

        val result = dropTinyLegs(legs)

        assertThat(result).containsExactly(legs[1], legs[2]).inOrder()
    }

    @Test
    fun `drops a leg under 150 meters in the middle`() {
        val legs = listOf(
            testLeg("WALK", 500.0),
            testLeg("BICYCLE", 100.0),
            testLeg("WALK", 500.0)
        )

        val result = dropTinyLegs(legs)

        assertThat(result).containsExactly(legs[0], legs[2]).inOrder()
    }

    @Test
    fun `drops a leg under 150 meters at the end`() {
        val legs = listOf(
            testLeg("WALK", 500.0),
            testLeg("BICYCLE", 500.0),
            testLeg("WALK", 100.0)
        )

        val result = dropTinyLegs(legs)

        assertThat(result).containsExactly(legs[0], legs[1]).inOrder()
    }

    @Test
    fun `drops multiple legs under 150 meters regardless of position or mode`() {
        val legs = listOf(
            testLeg("WALK", 10.0),
            testLeg("BICYCLE", 500.0),
            testLeg("BICYCLE", 20.0),
            testLeg("BUS", 5000.0),
            testLeg("WALK", 30.0)
        )

        val result = dropTinyLegs(legs)

        assertThat(result).containsExactly(legs[1], legs[3]).inOrder()
    }

    @Test
    fun `keeps a leg at exactly 150 meters`() {
        val legs = listOf(
            testLeg("WALK", 150.0),
            testLeg("BICYCLE", 500.0),
            testLeg("WALK", 500.0)
        )

        val result = dropTinyLegs(legs)

        assertThat(result).containsExactly(legs[0], legs[1], legs[2]).inOrder()
    }

    @Test
    fun `falls back to the original list when trimming would empty it`() {
        val legs = listOf(testLeg("WALK", 20.0))

        val result = dropTinyLegs(legs)

        assertThat(result).isEqualTo(legs)
    }

    @Test
    fun `falls back to the original list when every leg is under the threshold, not only when there's one leg`() {
        // The fallback (legs.filterNot { ... }.ifEmpty { legs }) triggers whenever filtering
        // would remove ALL legs, regardless of how many there are -- a 2-leg (or longer) list
        // where every leg is under the threshold reverts just like a single-leg one does.
        val legs = listOf(testLeg("WALK", 30.0), testLeg("BICYCLE", 100.0))

        val result = dropTinyLegs(legs)

        assertThat(result).isEqualTo(legs)
    }
}
