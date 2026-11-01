package com.hmg.safelog.core

/** Text-to-speech output. languageTag is a BCP-47 tag, e.g. "ko-KR". */
interface Speaker {
    fun speak(text: String, languageTag: String = "ko-KR")
}

/** Persists event records (JSON lines). */
interface EventStore {
    fun save(record: EventRecord)
}

interface Clock {
    fun nowMs(): Long

    /** Local hour of day (0..23) for the given epoch milliseconds. */
    fun localHour(epochMs: Long): Int
}

/** Push-style callbacks a SignalSource delivers to the engine. */
interface SignalListener {
    fun onSpeed(tMs: Long, kph: Double)
    fun onBrake(tMs: Long, pressed: Boolean)
    fun onGear(tMs: Long, gear: Gear)
    fun onAirbagDeployed(tMs: Long)
    fun onTelltales(tMs: Long, readings: List<TelltaleReading>)
}

/** Vehicle signal producer: real adapter in the app, fake in tests. */
interface SignalSource {
    fun start(listener: SignalListener)
    fun stop()
}

/** Anyone who can be notified (driver reminder / contact message). */
data class Contact(val id: String, val name: String, val consented: Set<AlertCategory> = emptySet())

enum class AlertCategory { AIRBAG_DEPLOYED, WARNING_TELLTALE }

interface ContactNotifier {
    fun notifyContact(contact: Contact, message: String)
}

interface DriverNotifier {
    fun remind(message: String)
}
