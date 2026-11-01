package com.hmg.safelog.core

/** Glue: assembles Samples from signal callbacks and feeds all features. */
class SafeLogEngine(
    speaker: Speaker,
    store: EventStore,
    clock: Clock,
    private val alerts: EventAlertPolicy,
    pedalConfig: PedalGuardConfig = PedalGuardConfig(),
    onTripSummary: (TripSummary) -> Unit = {},
) : SignalListener {
    private val trips = TripTracker(clock, onSummary = onTripSummary)
    private val pedal = PedalGuard(pedalConfig, speaker, store, onTrigger = { trips.onPedalEvent() })
    private val parking = ParkingStartCheck(speaker)

    private var brake = false
    private var gear = Gear.UNKNOWN

    val lastTrip: TripSummary? get() = trips.lastSummary

    override fun onBrake(tMs: Long, pressed: Boolean) { brake = pressed }
    override fun onGear(tMs: Long, gear: Gear) { this.gear = gear }

    override fun onSpeed(tMs: Long, kph: Double) {
        val s = Sample(tMs, kph, brake, gear)
        parking.onSample(s)
        pedal.onSample(s)
        trips.onSample(s)
    }

    override fun onAirbagDeployed(tMs: Long) = alerts.onAirbagDeployed(tMs)
    override fun onTelltales(tMs: Long, readings: List<TelltaleReading>) = alerts.onTelltales(tMs, readings)

    fun flush() = pedal.flush()
}
