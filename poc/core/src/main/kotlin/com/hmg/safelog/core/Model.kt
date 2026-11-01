package com.hmg.safelog.core

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/** One time-aligned vehicle sample (speed is the clock: one sample per speed update). */
data class Sample(
    val tMs: Long,
    val speedKph: Double,
    val brake: Boolean,
    val gear: Gear,
)

/** Warning telltales that SafeLog watches (subset of Pleos TelltaleType). */
enum class WarningTelltale { ABS, ESC, AIRBAG, ENGINE, ENGINE_OIL_PRESSURE, TPMS, MASTER_WARNING }

data class TelltaleReading(val type: WarningTelltale, val on: Boolean)

data class EventRecord(
    val type: String,
    val triggerMs: Long,
    val samples: List<Sample>,
) {
    /** JSON lines: first line is a header, then one line per sample. */
    fun toJsonLines(): List<String> {
        val lines = ArrayList<String>()
        lines += """{"type":"$type","triggerMs":$triggerMs,"samples":${samples.size}}"""
        for (s in samples) {
            lines += """{"t":${s.tMs},"kph":${s.speedKph},"brake":${s.brake},"gear":"${s.gear.name}"}"""
        }
        return lines
    }
}
