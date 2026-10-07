package one.brj.bikebus.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DtoMappingTest {
    @Test
    fun `LegDto toUiModel preserves the instant and uses the system default zone, not UTC`() {
        val epochSecond = 1_735_732_800L // arbitrary fixed instant
        val dto = LegDto(
            mode = "WALK", distanceMeters = 100.0, durationSeconds = 60.0,
            fromLat = 1.0, fromLon = 2.0, toLat = 3.0, toLon = 4.0,
            fromName = "A", toName = "B", routeShortName = null,
            departureEpochSecond = epochSecond,
        )
        val leg = dto.toUiModel()
        // The instant itself must round-trip exactly, regardless of which zone displays it.
        assertEquals(Instant.ofEpochSecond(epochSecond), leg.departureTime.toInstant())
        // The offset must be the system default zone's real offset at this instant, not a
        // hardcoded UTC -- computed dynamically so this test is correct on any host timezone.
        val expectedOffset = ZoneId.systemDefault().rules.getOffset(Instant.ofEpochSecond(epochSecond))
        assertEquals(expectedOffset, leg.departureTime.offset)
    }
}
