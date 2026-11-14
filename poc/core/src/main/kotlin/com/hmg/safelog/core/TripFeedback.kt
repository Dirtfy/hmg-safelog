package com.hmg.safelog.core

data class TripConfig(
    val hardAccelKphPerS: Double = 10.0,
    val hardBrakeKphPerS: Double = -12.0,
    val maxGapMs: Long = 2_000,
    val rateWindowMs: Long = 1_000,
    val nightStartHour: Int = 22,
    val nightEndHour: Int = 6,
)

data class TripSummary(
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    val distanceM: Double,
    val hardAccelCount: Int,
    val hardBrakeCount: Int,
    val pedalEvents: Int,
    val nightMs: Long,
) {
    /** Short Korean summary shown to the driver only. */
    fun toKorean(): String {
        val min = durationMs / 60_000
        val km = Math.round(distanceM / 100.0) / 10.0
        val night = nightMs / 60_000
        return "이번 주행: ${min}분, ${km}km, 급가속 ${hardAccelCount}회, 급제동 ${hardBrakeCount}회, " +
            "페달 오조작 의심 ${pedalEvents}회, 야간 주행 ${night}분"
    }
}

/** Trip = gear leaves PARK ... returns to PARK. Summary is for the driver only. */
class TripTracker(
    private val clock: Clock,
    private val config: TripConfig = TripConfig(),
    private val onSummary: (TripSummary) -> Unit = {},
) {
    private var active = false
    private var startMs = 0L
    private var prev: Sample? = null
    private var distanceM = 0.0
    private var hardAccel = 0
    private var hardBrake = 0
    private var pedal = 0
    private var nightMs = 0L
    private var inHardAccel = false
    private var inHardBrake = false
    /** Acceleration is measured over >= rateWindowMs: real signals arrive with jitter (two speed events can be
     *  20 ms apart on the Pleos emulator), and a per-sample rate turned that jitter into fake hard brakes. */
    private var rateRef: Sample? = null

    var lastSummary: TripSummary? = null
        private set

    fun onPedalEvent() {
        if (active) pedal++
    }

    fun onSample(s: Sample) {
        if (!active) {
            // UNKNOWN = no gear reading yet (app start); only a real gear out of PARK starts a trip
            if (s.gear != Gear.PARK && s.gear != Gear.UNKNOWN) begin(s)
            prev = s
            return
        }
        val p = prev
        if (p != null) accumulate(p, s)
        prev = s
        if (s.gear == Gear.PARK) end(s)
    }

    private fun begin(s: Sample) {
        active = true
        startMs = s.tMs
        distanceM = 0.0
        hardAccel = 0
        hardBrake = 0
        pedal = 0
        nightMs = 0
        inHardAccel = false
        inHardBrake = false
        rateRef = null
    }

    private fun accumulate(p: Sample, s: Sample) {
        val dt = s.tMs - p.tMs
        if (dt <= 0) return
        distanceM += (p.speedKph + s.speedKph) / 2.0 / 3.6 * (dt / 1000.0)
        if (dt <= config.maxGapMs) {
            val h = clock.localHour(p.tMs)
            val night = if (config.nightStartHour > config.nightEndHour) {
                h >= config.nightStartHour || h < config.nightEndHour
            } else {
                h >= config.nightStartHour && h < config.nightEndHour
            }
            if (night) nightMs += dt
            val ref = rateRef ?: p
            val span = s.tMs - ref.tMs
            if (span >= config.rateWindowMs) {
                val rate = (s.speedKph - ref.speedKph) / (span / 1000.0)
                val accel = rate >= config.hardAccelKphPerS && !s.brake
                val brake = rate <= config.hardBrakeKphPerS
                if (accel && !inHardAccel) hardAccel++
                if (brake && !inHardBrake) hardBrake++
                inHardAccel = accel
                inHardBrake = brake
                rateRef = s
            } else if (rateRef == null) {
                rateRef = p
            }
        } else {
            rateRef = s
        }
    }

    private fun end(s: Sample) {
        active = false
        val summary = TripSummary(
            startMs, s.tMs, s.tMs - startMs, distanceM, hardAccel, hardBrake, pedal, nightMs,
        )
        lastSummary = summary
        onSummary(summary)
    }
}
