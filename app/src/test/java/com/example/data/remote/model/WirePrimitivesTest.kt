package com.example.data.remote.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class WirePrimitivesTest {

    @Test
    fun `int64 beyond double precision round-trips losslessly`() {
        // 2^53 + 1: the smallest positive integer not representable as a Double.
        val value = "9007199254740993"
        val parsed = DecimalInt64.parse(value)
        assertEquals(value, DecimalInt64.format(parsed))
    }

    @Test
    fun `int64 max and min round-trip`() {
        assertEquals(Long.MAX_VALUE.toString(), DecimalInt64.format(DecimalInt64.parse(Long.MAX_VALUE.toString())))
        assertEquals(Long.MIN_VALUE.toString(), DecimalInt64.format(DecimalInt64.parse(Long.MIN_VALUE.toString())))
    }

    @Test
    fun `non-numeric and out-of-range ids are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DecimalInt64.parse("12.5") }
        assertThrows(IllegalArgumentException::class.java) { DecimalInt64.parse("9223372036854775808") }
        assertThrows(IllegalArgumentException::class.java) { DecimalInt64.parse("") }
    }

    @Test
    fun `rfc3339 with nine fractional digits parses`() {
        val instant = Rfc3339Instant.parse("2025-03-01T12:00:00.123456789Z")
        assertEquals(Instant.parse("2025-03-01T12:00:00Z").plusNanos(123456789), instant)
    }

    @Test
    fun `rfc3339 without fractional part parses`() {
        assertEquals(Instant.parse("2025-03-01T12:00:00Z"), Rfc3339Instant.parse("2025-03-01T12:00:00Z"))
    }
}
