package com.hmg.safelog.core

/** All trips of one day: the driver's review screen and the card sent to family after each trip. */
data class DayReview(
    val trips: Int,
    val totalMs: Long,
    val distanceM: Double,
    val hardAccel: Int,
    val hardBrake: Int,
    val pedalEvents: Int,
    val telltalesOn: List<WarningTelltale>,
) {
    val minutes: Long get() = totalMs / 60_000
    val km: Double get() = Math.round(distanceM / 100.0) / 10.0

    fun lines(): List<String> = listOf(
        "오늘 운전 ${trips}회 · 총 ${minutes}분 · ${km}km",
        "급가속 ${hardAccel}회 · 급제동 ${hardBrake}회",
        if (pedalEvents == 0) "페달 확인: 이상 없음" else "페달 오조작 의심 ${pedalEvents}회 (기록 있음)",
        if (telltalesOn.isEmpty()) "경고등: 없음"
        else "경고등: " + telltalesOn.joinToString(", ") { TelltaleGuide.advice(it).title },
    )

    /**
     * What is sent to the registered family automatically when a trip ends. Counts only:
     * no location, no route, no timestamps of individual trips, no raw pedal record.
     */
    fun shareCard(): String = (listOf("[세이프로그] 오늘 운전 요약") + lines()).joinToString("\n")

    companion object {
        fun of(trips: List<TripSummary>, telltalesOn: List<WarningTelltale>) = DayReview(
            trips = trips.size,
            totalMs = trips.sumOf { it.durationMs },
            distanceM = trips.sumOf { it.distanceM },
            hardAccel = trips.sumOf { it.hardAccelCount },
            hardBrake = trips.sumOf { it.hardBrakeCount },
            pedalEvents = trips.sumOf { it.pedalEvents },
            telltalesOn = telltalesOn,
        )
    }
}
