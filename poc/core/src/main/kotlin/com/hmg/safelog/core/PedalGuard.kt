package com.hmg.safelog.core

data class PedalGuardConfig(
    val bufferMs: Long = 15_000,
    val maxStartSpeedKph: Double = 15.0,
    val riseKph: Double = 8.0,
    val riseWithinMs: Long = 1_000,
    val preMs: Long = 10_000,
    val postMs: Long = 5_000,
    val cooldownMs: Long = 10_000,
    val message: String = "브레이크를 밟으세요",
    val languageTag: String = "ko-KR",
)

/**
 * Detects suspected pedal misapplication: gear R/D, speed at window start <= maxStart,
 * brake NOT pressed during the rise window, and speed rising >= riseKph within riseWithinMs.
 * "Window" for the brake check = the rise interval itself (from the low-speed sample to now).
 */
class PedalGuard(
    private val config: PedalGuardConfig,
    private val speaker: Speaker,
    private val store: EventStore,
    private val onTrigger: (Long) -> Unit = {},
) {
    private val buffer = ArrayDeque<Sample>()
    private var lastTriggerMs: Long? = null
    private val pending = ArrayList<Pair<Long, List<Sample>>>() // triggerMs, pre-samples
    private val pendingPost = HashMap<Long, MutableList<Sample>>()

    fun onSample(s: Sample) {
        buffer.addLast(s)
        val keepFrom = s.tMs - maxOf(config.bufferMs, config.preMs)
        while (buffer.isNotEmpty() && buffer.first().tMs < keepFrom) buffer.removeFirst()

        // collect post samples, finalize when complete
        val done = ArrayList<Long>()
        for ((trigger, _) in pending) {
            pendingPost.getValue(trigger).add(s)
            if (s.tMs - trigger >= config.postMs) done += trigger
        }
        for (trigger in done) finalize(trigger)

        if (detect(s)) trigger(s)
    }

    /** Flush pending events at end of stream (saves whatever post samples exist). */
    fun flush() {
        for (trigger in pending.map { it.first }) finalize(trigger)
    }

    private fun detect(now: Sample): Boolean {
        if (now.gear != Gear.REVERSE && now.gear != Gear.DRIVE) return false
        // walk back through the rise window
        val window = buffer.filter { it.tMs >= now.tMs - config.riseWithinMs }
        for (i in window.indices) {
            val start = window[i]
            if (start.speedKph > config.maxStartSpeedKph) continue
            val span = window.subList(i, window.size)
            if (span.any { it.brake }) continue
            if (span.any { it.gear != Gear.REVERSE && it.gear != Gear.DRIVE }) continue
            if (now.speedKph - start.speedKph >= config.riseKph) return true
        }
        return false
    }

    private fun trigger(s: Sample) {
        val last = lastTriggerMs
        if (last != null && s.tMs - last < config.cooldownMs) return
        lastTriggerMs = s.tMs
        speaker.speak(config.message, config.languageTag)
        onTrigger(s.tMs)
        val pre = buffer.filter { it.tMs >= s.tMs - config.preMs }
        pending += s.tMs to pre
        pendingPost[s.tMs] = ArrayList()
    }

    private fun finalize(trigger: Long) {
        val idx = pending.indexOfFirst { it.first == trigger }
        if (idx < 0) return
        val (_, pre) = pending.removeAt(idx)
        val post = pendingPost.remove(trigger).orEmpty().filter { it.tMs > trigger }
        val record = EventRecord("SUSPECTED_PEDAL_MISAPPLICATION", trigger, pre + post)
        store.save(record)
    }
}
