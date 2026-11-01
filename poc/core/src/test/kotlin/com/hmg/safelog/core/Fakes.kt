package com.hmg.safelog.core

import java.io.File

class RecordingSpeaker : Speaker {
    val spoken = ArrayList<Pair<String, String>>()
    override fun speak(text: String, languageTag: String) { spoken += text to languageTag }
}

class MemoryStore : EventStore {
    val records = ArrayList<EventRecord>()
    override fun save(record: EventRecord) { records += record }
}

/** UTC-based fake clock; epoch ms 0 == 00:00. */
class FakeClock(var now: Long = 0) : Clock {
    override fun nowMs() = now
    override fun localHour(epochMs: Long): Int = ((epochMs / 3_600_000L) % 24).toInt()
}

class RecordingContactNotifier : ContactNotifier {
    val sent = ArrayList<Pair<Contact, String>>()
    override fun notifyContact(contact: Contact, message: String) { sent += contact to message }
}

class RecordingDriverNotifier : DriverNotifier {
    val reminders = ArrayList<String>()
    override fun remind(message: String) { reminders += message }
}

data class Row(
    val tMs: Long,
    val speedKph: Double,
    val brake: Boolean,
    val gear: Gear,
    val airbag: Boolean,
    val telltales: List<WarningTelltale>,
)

object Scenarios {
    fun load(name: String): List<Row> {
        val dir = System.getProperty("scenarioDir") ?: error("scenarioDir not set")
        val lines = File(dir, "$name.csv").readLines().drop(1).filter { it.isNotBlank() }
        return lines.map { l ->
            val c = l.split(",", limit = 6)
            Row(
                c[0].toLong(), c[1].toDouble(), c[2] == "1", Gear.valueOf(c[3]), c[4] == "1",
                c[5].split("|").filter { it.isNotBlank() }.map { WarningTelltale.valueOf(it) },
            )
        }
    }

    /** Fake SignalSource that replays a trace into a listener. */
    class Replay(private val rows: List<Row>, private val offsetMs: Long = 0) : SignalSource {
        override fun start(listener: SignalListener) {
            val allTelltales = WarningTelltale.values()
            for (r in rows) {
                val t = r.tMs + offsetMs
                listener.onGear(t, r.gear)
                listener.onBrake(t, r.brake)
                if (r.airbag) listener.onAirbagDeployed(t)
                listener.onTelltales(t, allTelltales.map { TelltaleReading(it, it in r.telltales) })
                listener.onSpeed(t, r.speedKph)
            }
        }
        override fun stop() {}
    }
}
