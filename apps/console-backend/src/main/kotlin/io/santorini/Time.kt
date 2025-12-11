package io.santorini

import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.UtcOffset

fun defaultFixedOffsetTimeZone(): FixedOffsetTimeZone {
    return FixedOffsetTimeZone(UtcOffset(8))
}