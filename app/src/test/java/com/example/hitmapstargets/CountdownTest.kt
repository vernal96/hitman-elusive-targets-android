package com.example.hitmapstargets

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class CountdownTest {
    @Test fun computesHoursAcrossDays() {
        assertEquals("74:42:21", formatCountdown(
            Instant.parse("2026-11-03T10:00:00Z"), Instant.parse("2026-11-06T12:42:21Z")
        ))
    }
    @Test fun doesntGoNegative() {
        assertEquals("00:00:00", formatCountdown(
            Instant.parse("2026-11-07T00:00:00Z"), Instant.parse("2026-11-06T12:00:00Z")
        ))
    }
    @Test fun distinguishesBoundaries() {
        val t = ElusiveTarget(1L, "Example", "/games/hitman/paris/test", null,
            Instant.parse("2026-11-06T12:00:00Z"), Instant.parse("2026-11-15T12:00:00Z"))
        assertEquals(TargetStatus.UPCOMING, t.status(Instant.parse("2026-11-06T11:59:59Z")))
        assertEquals(TargetStatus.ACTIVE, t.status(t.startsAt))
        assertEquals(TargetStatus.EXPIRED, t.status(t.endsAt))
    }
}
