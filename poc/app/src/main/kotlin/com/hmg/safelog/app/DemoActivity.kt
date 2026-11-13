package com.hmg.safelog.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import com.hmg.safelog.core.Clock
import com.hmg.safelog.core.Contact
import com.hmg.safelog.core.ContactNotifier
import com.hmg.safelog.core.DriverNotifier
import com.hmg.safelog.core.EventAlertPolicy
import com.hmg.safelog.core.EventRecord
import com.hmg.safelog.core.EventStore
import com.hmg.safelog.core.Gear
import com.hmg.safelog.core.SafeLogEngine
import com.hmg.safelog.core.Speaker
import com.hmg.safelog.core.TelltaleReading
import com.hmg.safelog.core.TripSummary
import com.hmg.safelog.core.WarningTelltale
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale

/**
 * Demo mode: replays a simulation CSV (assets) in real time through the REAL core engine
 * (PedalGuard / ParkingStartCheck / TripTracker). Never touches the Pleos SDK, so it runs on a
 * plain Android emulator.
 *
 * adb shell am start -n com.hmg.safelog.poc/com.hmg.safelog.app.DemoActivity --es scenario demo_mart_reverse
 * Optional: --ei record_hold_ms 8000 --ei review_hold_ms 10000
 * Frame capture: --ez frames true renders the same screens offscreen on a virtual clock and saves
 * one 1920x1080 PNG per 100 ms to files/frames (pull with run-as), so a slow emulator still gives smooth footage.
 */
class DemoActivity : Activity() {
    private lateinit var view: DemoView
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    @Volatile private var stopped = false
    private var worker: Thread? = null
    private val startUptime = SystemClock.uptimeMillis()
    private var framesDir: File? = null
    @Volatile private var vt = startUptime
    private var frameNo = 0

    private class DemoRow(
        val tMs: Long, val kph: Double, val brake: Boolean, val gear: Gear,
        val airbag: Boolean, val telltales: List<WarningTelltale>,
    )

    private fun now() = if (framesDir != null) vt else SystemClock.uptimeMillis()
    private fun el() = now() - startUptime
    private fun log(msg: String) = Log.i(TAG, "T+${el()} $msg")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = DemoView(this)
        if (intent.getBooleanExtra("frames", false)) {
            framesDir = filesDir.resolve("frames").apply { deleteRecursively(); mkdirs() }
            view.clock = { vt }
            view.measure(
                View.MeasureSpec.makeMeasureSpec(FRAME_W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(FRAME_H, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, FRAME_W, FRAME_H)
            setContentView(TextView(this).apply { text = "프레임 캡처 중"; textSize = 32f })
        } else {
            setContentView(view)
        }
        hideSystemBars()

        try {
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        val r = tts?.setLanguage(Locale.KOREA)
                        ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                    } catch (e: Throwable) {
                        Log.w(TAG, "tts setLanguage failed: $e")
                    }
                }
                Log.i(TAG, "tts init status=$status ready=$ttsReady")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "tts unavailable: $e")
        }

        val scenario = intent.getStringExtra("scenario") ?: "demo_mart_reverse"
        val recordHold = intent.getIntExtra("record_hold_ms", 9_000).toLong()
        val reviewHold = intent.getIntExtra("review_hold_ms", 10_000).toLong()
        log("DEMO_START scenario=$scenario")
        worker = Thread({ runDemo(scenario, recordHold, reviewHold) }, "demo-replay").also { it.start() }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    override fun onDestroy() {
        stopped = true
        worker?.interrupt()
        try { tts?.shutdown() } catch (_: Throwable) {}
        super.onDestroy()
    }

    /** Shows the spoken text in the bubble and also asks Android TTS (ko-KR), failures ignored. */
    private inner class DemoSpeaker : Speaker {
        override fun speak(text: String, languageTag: String) {
            log("SPEAK $text")
            view.onUtterance(text)
            try {
                if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "demo")
            } catch (e: Throwable) {
                Log.w(TAG, "tts speak failed: $e")
            }
        }
    }

    private fun loadRows(name: String): List<DemoRow> =
        assets.open("$name.csv").bufferedReader(Charsets.UTF_8).useLines { seq ->
            seq.drop(1).filter { it.isNotBlank() }.map { l ->
                val c = l.split(",", limit = 6)
                DemoRow(
                    c[0].toLong(), c[1].toDouble(), c[2] == "1", Gear.valueOf(c[3]), c[4] == "1",
                    c.getOrElse(5) { "" }.split("|").filter { it.isNotBlank() }.map { WarningTelltale.valueOf(it) },
                )
            }.toList()
        }

    private fun sleepUntil(uptime: Long) {
        val d = uptime - SystemClock.uptimeMillis()
        if (d > 0) Thread.sleep(d)
    }

    /** Real time: sleep. Frame capture: step the virtual clock, saving one frame per FRAME_MS. */
    private fun advanceTo(target: Long) {
        val dir = framesDir ?: return sleepUntil(target)
        while (vt < target) {
            renderFrame(dir)
            vt += FRAME_MS
        }
    }

    private fun renderFrame(dir: File) {
        val bmp = Bitmap.createBitmap(FRAME_W, FRAME_H, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        FileOutputStream(File(dir, "f%05d.png".format(frameNo++))).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    private fun runDemo(scenario: String, recordHold: Long, reviewHold: Long) {
        try {
            val rows = loadRows(scenario)
            var savedRecord: EventRecord? = null
            var trip: TripSummary? = null
            val fileStore = FileEventStore(filesDir.resolve("demo-events"))
            val store = object : EventStore {
                override fun save(record: EventRecord) {
                    fileStore.save(record)
                    savedRecord = record
                    log("EVENT_SAVED type=${record.type} triggerMs=${record.triggerMs} samples=${record.samples.size}")
                }
            }
            val clock = object : Clock {
                override fun nowMs() = System.currentTimeMillis()
                override fun localHour(epochMs: Long): Int =
                    Calendar.getInstance().apply { timeInMillis = epochMs }.get(Calendar.HOUR_OF_DAY)
            }
            val speaker = DemoSpeaker()
            val engine = SafeLogEngine(
                speaker = speaker,
                store = store,
                clock = clock,
                alerts = EventAlertPolicy(
                    { emptyList<Contact>() },
                    object : ContactNotifier {
                        override fun notifyContact(contact: Contact, message: String) {}
                    },
                    object : DriverNotifier {
                        override fun remind(message: String) = speaker.speak(message)
                    },
                ),
                onTripSummary = { trip = it; log("TRIP_SUMMARY ${it.toKorean()}") },
            )

            view.setMode(DemoView.Mode.DRIVING)
            log("SCREEN driving")
            val allTelltales = WarningTelltale.values()
            val t0 = now()
            val first = rows.first().tMs
            var prevT = first
            var skipped = 0L // long gaps (e.g. 12 h telltale samples) are compressed to 1.5 s
            for (r in rows) {
                if (stopped) return
                val gap = r.tMs - prevT
                prevT = r.tMs
                if (gap > 5_000) skipped += gap - 1_500
                advanceTo(t0 + (r.tMs - first - skipped))
                view.onSignals(r.gear, r.kph, r.brake, r.telltales)
                engine.onGear(r.tMs, r.gear)
                engine.onBrake(r.tMs, r.brake)
                if (r.airbag) engine.onAirbagDeployed(r.tMs)
                engine.onTelltales(r.tMs, allTelltales.map { TelltaleReading(it, it in r.telltales) })
                engine.onSpeed(r.tMs, r.kph)
            }
            engine.flush()
            log("SCENARIO_END")
            advanceTo(now() + 2_000)

            val rec = savedRecord
            if (rec != null) {
                view.setRecord(rec)
                view.setMode(DemoView.Mode.RECORD)
                log("SCREEN record")
                advanceTo(now() + recordHold)
            }
            val t = trip ?: engine.lastTrip
            if (t != null) {
                view.setTrip(t)
                view.setMode(DemoView.Mode.REVIEW)
                log("SCREEN review")
                advanceTo(now() + reviewHold)
            }
            log("DEMO_DONE")
            if (framesDir != null) log("FRAMES_DONE n=$frameNo")
        } catch (e: InterruptedException) {
            // activity closed
        } catch (e: Throwable) {
            Log.e(TAG, "demo failed", e)
        }
    }

    private companion object {
        const val TAG = "SafeLog"
        const val FRAME_MS = 100L
        const val FRAME_W = 1920
        const val FRAME_H = 1080
    }
}
