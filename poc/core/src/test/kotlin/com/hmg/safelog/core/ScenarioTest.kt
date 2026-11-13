package com.hmg.safelog.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScenarioTest {
    private class Rig(contacts: List<Contact> = emptyList(), val offsetMs: Long = 0) {
        val speaker = RecordingSpeaker()
        val store = MemoryStore()
        val clock = FakeClock()
        val contactNotifier = RecordingContactNotifier()
        val driverNotifier = RecordingDriverNotifier()
        val trips = ArrayList<TripSummary>()
        val alerts = EventAlertPolicy({ contacts }, contactNotifier, driverNotifier)
        val engine = SafeLogEngine(speaker, store, clock, alerts, onTripSummary = { trips += it })

        fun run(name: String) {
            Scenarios.Replay(Scenarios.load(name), offsetMs).start(engine)
            engine.flush()
        }
    }

    private val pedalMsg = "다른 페달, 브레이크를 밟으세요"

    // ---- PedalGuard ----
    @Test fun reverseMisapplicationTriggersOnceWithEvidence() {
        val rig = Rig(); rig.run("reverse_misapplication")
        assertEquals(1, rig.speaker.spoken.count { it.first == pedalMsg })
        assertEquals("ko-KR", rig.speaker.spoken.first { it.first == pedalMsg }.second)
        assertEquals(1, rig.store.records.size)
        val rec = rig.store.records[0]
        assertEquals("SUSPECTED_PEDAL_MISAPPLICATION", rec.type)
        assertTrue(rec.samples.first().tMs >= rec.triggerMs - 10_000)
        assertTrue(rec.samples.last().tMs > rec.triggerMs, "post samples present")
        assertTrue(rec.samples.last().tMs <= rec.triggerMs + 5_000)
        val lines = rec.toJsonLines()
        assertEquals(rec.samples.size + 1, lines.size)
        assertTrue(lines[0].startsWith("{\"type\":\"SUSPECTED_PEDAL_MISAPPLICATION\""))
        assertTrue(lines[1].contains("\"gear\""))
    }

    @Test fun forwardLowSpeedMisapplicationTriggers() {
        val rig = Rig(); rig.run("forward_lowspeed_misapplication")
        assertEquals(1, rig.speaker.spoken.count { it.first == pedalMsg })
        assertEquals(1, rig.store.records.size)
    }

    @Test fun normalParkingDoesNotTrigger() {
        val rig = Rig(); rig.run("normal_parking")
        assertEquals(0, rig.speaker.spoken.count { it.first == pedalMsg })
        assertTrue(rig.store.records.isEmpty())
    }

    @Test fun highwayAccelerationDoesNotTrigger() {
        val rig = Rig(); rig.run("highway_accel")
        assertTrue(rig.speaker.spoken.none { it.first == pedalMsg })
        assertTrue(rig.store.records.isEmpty())
    }

    @Test fun accelerationWithBrakePressedDoesNotTrigger() {
        val rig = Rig(); rig.run("brake_held_accel")
        assertTrue(rig.speaker.spoken.none { it.first == pedalMsg })
        assertTrue(rig.store.records.isEmpty())
    }

    @Test fun parkAndNeutralNeverTrigger() {
        val speaker = RecordingSpeaker(); val store = MemoryStore()
        val g = PedalGuard(PedalGuardConfig(), speaker, store)
        for (gear in listOf(Gear.PARK, Gear.NEUTRAL)) {
            for (i in 0..10) g.onSample(Sample(i * 100L, i * 5.0, false, gear))
        }
        g.flush()
        assertTrue(speaker.spoken.isEmpty()); assertTrue(store.records.isEmpty())
    }

    @Test fun cooldownSuppressesSecondTriggerWithin10s() {
        val speaker = RecordingSpeaker(); val store = MemoryStore()
        val g = PedalGuard(PedalGuardConfig(), speaker, store)
        // two surges 4 s apart
        var t = 0L
        fun surge() {
            for (i in 0..5) { g.onSample(Sample(t, 0.0, false, Gear.DRIVE)); t += 100 }
            for (i in 1..5) { g.onSample(Sample(t, i * 4.0, false, Gear.DRIVE)); t += 100 }
            for (i in 0..30) { g.onSample(Sample(t, 0.0, false, Gear.DRIVE)); t += 100 }
        }
        surge(); surge(); g.flush()
        assertEquals(1, speaker.spoken.size)
        assertEquals(1, store.records.size)
    }

    @Test fun slowRiseDoesNotTrigger() {
        val speaker = RecordingSpeaker(); val store = MemoryStore()
        val g = PedalGuard(PedalGuardConfig(), speaker, store)
        // 1 kph per 100 ms == 10 kph/s but only 8+ after 800ms... use 0.5 kph/100ms (5 kph/s)
        for (i in 0..100) g.onSample(Sample(i * 100L, i * 0.5, false, Gear.DRIVE))
        g.flush()
        assertTrue(speaker.spoken.isEmpty())
    }

    // ---- ParkingStartCheck ----
    @Test fun parkToReversePromptAtMostOncePer10Minutes() {
        val rig = Rig(); rig.run("park_to_reverse")
        val prompts = rig.speaker.spoken.filter { it.first == "오른발이 브레이크 위에 있는지 확인하세요" }
        assertEquals(2, prompts.size)
    }

    @Test fun parkToDrivePrompts() {
        val speaker = RecordingSpeaker()
        val p = ParkingStartCheck(speaker)
        p.onSample(Sample(0, 0.0, true, Gear.PARK))
        p.onSample(Sample(1000, 0.0, true, Gear.DRIVE))
        assertEquals(1, speaker.spoken.size)
    }

    @Test fun neutralToDriveDoesNotPrompt() {
        val speaker = RecordingSpeaker()
        val p = ParkingStartCheck(speaker)
        p.onSample(Sample(0, 0.0, true, Gear.NEUTRAL))
        p.onSample(Sample(1000, 0.0, true, Gear.DRIVE))
        assertTrue(speaker.spoken.isEmpty())
    }

    // ---- TripFeedback ----
    @Test fun tripSummaryCountsAndKorean() {
        val rig = Rig(offsetMs = 12 * 3_600_000L); rig.run("trip_commute")
        assertEquals(1, rig.trips.size)
        val t = rig.trips[0]
        assertEquals(14_000L, t.durationMs)
        assertEquals(72.22, t.distanceM, 0.5)
        assertEquals(1, t.hardAccelCount)
        assertEquals(1, t.hardBrakeCount)
        assertEquals(0, t.pedalEvents)
        assertEquals(0L, t.nightMs)
        val ko = t.toKorean()
        assertTrue(ko.contains("급가속 1회")); assertTrue(ko.contains("급제동 1회"))
    }

    @Test fun tripNightMinutes() {
        val rig = Rig(offsetMs = 23 * 3_600_000L); rig.run("trip_commute")
        assertEquals(14_000L, rig.trips[0].nightMs)
    }

    @Test fun tripCountsPedalEvent() {
        val rig = Rig(); rig.run("reverse_misapplication")
        // trip never returns to PARK in that trace, so no summary; check via a trace ending in PARK
        val rig2 = Rig()
        val rows = Scenarios.load("reverse_misapplication") + Row(15_000, 0.0, true, Gear.PARK, false, emptyList())
        Scenarios.Replay(rows).start(rig2.engine); rig2.engine.flush()
        assertEquals(1, rig2.trips.size)
        assertEquals(1, rig2.trips[0].pedalEvents)
    }

    // ---- SharePolicy / EventAlertPolicy ----
    private val none = Contact("c1", "엄마")
    private val airbagOnly = Contact("c2", "아빠", setOf(AlertCategory.AIRBAG_DEPLOYED))
    private val telltaleOnly = Contact("c3", "형", setOf(AlertCategory.WARNING_TELLTALE))

    @Test fun defaultConsentNothingEverSentToContacts() {
        val rig = Rig(contacts = listOf(none))
        rig.run("airbag"); rig.run("telltale_3days"); rig.run("reverse_misapplication"); rig.run("trip_commute")
        assertTrue(rig.contactNotifier.sent.isEmpty())
    }

    @Test fun airbagNotifiesOnlyConsentedContacts() {
        val rig = Rig(contacts = listOf(none, airbagOnly, telltaleOnly))
        rig.run("airbag")
        assertEquals(1, rig.contactNotifier.sent.size)
        assertEquals("c2", rig.contactNotifier.sent[0].first.id)
    }

    @Test fun telltaleOnForThreeDaysRemindsDailyAndNotifiesConsented() {
        val rig = Rig(contacts = listOf(none, airbagOnly, telltaleOnly))
        rig.run("telltale_3days")   // ON for 0..96h, samples every 12h
        // reminders at 72h and 96h
        assertEquals(2, rig.driverNotifier.reminders.size)
        assertTrue(rig.driverNotifier.reminders[0].contains("ABS"))
        assertEquals(2, rig.contactNotifier.sent.size)
        assertTrue(rig.contactNotifier.sent.all { it.first.id == "c3" })
    }

    @Test fun telltaleLessThanThreeDaysNoReminder() {
        val cn = RecordingContactNotifier(); val dn = RecordingDriverNotifier()
        val a = EventAlertPolicy({ listOf(telltaleOnly) }, cn, dn)
        a.onTelltales(0, listOf(TelltaleReading(WarningTelltale.ESC, true)))
        a.onTelltales(2 * 24 * 3_600_000L, listOf(TelltaleReading(WarningTelltale.ESC, true)))
        assertTrue(dn.reminders.isEmpty()); assertTrue(cn.sent.isEmpty())
    }

    @Test fun telltaleOffResetsTimer() {
        val cn = RecordingContactNotifier(); val dn = RecordingDriverNotifier()
        val a = EventAlertPolicy({ listOf(telltaleOnly) }, cn, dn)
        val day = 24 * 3_600_000L
        a.onTelltales(0, listOf(TelltaleReading(WarningTelltale.TPMS, true)))
        a.onTelltales(2 * day, listOf(TelltaleReading(WarningTelltale.TPMS, false)))
        a.onTelltales(3 * day, listOf(TelltaleReading(WarningTelltale.TPMS, true)))
        a.onTelltales(5 * day, listOf(TelltaleReading(WarningTelltale.TPMS, true)))
        assertTrue(dn.reminders.isEmpty())
        a.onTelltales(6 * day, listOf(TelltaleReading(WarningTelltale.TPMS, true)))
        assertEquals(1, dn.reminders.size)
    }

    @Test fun driverShareIsExplicitOnly() {
        val cn = RecordingContactNotifier()
        val share = SharePolicy(cn)
        assertTrue(cn.sent.isEmpty())
        share.driverShare(none, "오늘 주행 요약")
        assertEquals(1, cn.sent.size)
    }
}
