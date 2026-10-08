package com.example.data.remote.model

import java.time.Instant

/**
 * Wire int64 values are canonical decimal strings; they are only converted to
 * [Long] here, with an explicit signed-int64 range check (`toLongOrNull`).
 */
object DecimalInt64 {
    fun parse(value: String): Long {
        return value.toLongOrNull()
            ?: throw IllegalArgumentException("Not a signed int64 decimal string: $value")
    }

    fun format(value: Long): String = value.toString()
}

/** RFC3339 timestamps with up to nine fractional digits (e.g. `...T12:00:00.123456789Z`). */
object Rfc3339Instant {
    fun parse(value: String): Instant = Instant.parse(value)

    fun format(value: Instant): String = value.toString()
}
