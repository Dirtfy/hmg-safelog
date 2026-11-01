package com.hmg.safelog.core

class ParkingStartCheck(
    private val speaker: Speaker,
    private val minIntervalMs: Long = 10 * 60_000L,
    private val message: String = "오른발이 브레이크 위에 있는지 확인하세요",
    private val languageTag: String = "ko-KR",
) {
    private var prev: Gear? = null
    private var lastSpokenMs: Long? = null

    fun onSample(s: Sample) {
        val p = prev
        prev = s.gear
        if (p == Gear.PARK && (s.gear == Gear.REVERSE || s.gear == Gear.DRIVE)) {
            val last = lastSpokenMs
            if (last == null || s.tMs - last >= minIntervalMs) {
                lastSpokenMs = s.tMs
                speaker.speak(message, languageTag)
            }
        }
    }
}
