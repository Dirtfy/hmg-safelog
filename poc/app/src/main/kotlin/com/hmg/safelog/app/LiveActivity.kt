package com.hmg.safelog.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.hmg.safelog.core.Clock
import com.hmg.safelog.core.Contact
import com.hmg.safelog.core.ContactNotifier
import com.hmg.safelog.core.DayReview
import com.hmg.safelog.core.DriverNotifier
import com.hmg.safelog.core.EventAlertPolicy
import com.hmg.safelog.core.EventRecord
import com.hmg.safelog.core.EventStore
import com.hmg.safelog.core.Gear
import com.hmg.safelog.core.ReplyIntent
import com.hmg.safelog.core.SafeLogEngine
import com.hmg.safelog.core.SharePolicy
import com.hmg.safelog.core.SignalListener
import com.hmg.safelog.core.Speaker
import com.hmg.safelog.core.TelltaleAdvice
import com.hmg.safelog.core.TelltaleExplainer
import com.hmg.safelog.core.TelltaleReading
import com.hmg.safelog.core.TripSummary
import com.hmg.safelog.core.VoiceReply
import com.hmg.safelog.core.WarningTelltale
import java.util.Calendar
import java.util.Locale

/**
 * Live mode for the Pleos Connect Emulator: vehicle signals come from the REAL Pleos Vehicle SDK
 * (gear / brake / telltales) and AAOS CarPropertyManager (speed), i.e. whatever the Virtual Console
 * or VHAL sets. Same core engine as the service; adds scene A (telltale advice + NaviHelper waypoint)
 * and scene C (day review + driver-initiated share).
 *
 * adb shell am start -n com.hmg.safelog.poc/com.hmg.safelog.app.LiveActivity [--ez route true]
 * Driver voice reply (no microphone on the emulator, so the recognized text is handed in):
 *   adb shell am broadcast -a com.hmg.safelog.poc.VOICE --es text "응, 안내해 줘" -p com.hmg.safelog.poc
 */
class LiveActivity : Activity() {
    private lateinit var view: DemoView
    private val main = Handler(Looper.getMainLooper())
    private var adapter: VehicleSignalAdapter? = null
    private var pleosTts: PleosTtsSpeaker? = null
    private var androidTts: TextToSpeech? = null
    @Volatile private var androidTtsReady = false
    private var navi: NaviHelper? = null
    @Volatile private var naviConnected = false

    @Volatile private var gear = Gear.UNKNOWN
    @Volatile private var kph = 0.0
    @Volatile private var brake = false
    private val telltalesOn = LinkedHashSet<WarningTelltale>()
    private val trips = ArrayList<TripSummary>()
    @Volatile private var lastRecord: EventRecord? = null
    @Volatile private var offer: TelltaleAdvice? = null
    @Volatile private var day: DayReview? = null

    private fun log(msg: String) = Log.i(TAG, msg)

    private val speaker = object : Speaker {
        override fun speak(text: String, languageTag: String) {
            log("SPEAK $text")
            view.onUtterance(text)
            try {
                val p = pleosTts
                if (p != null && p.isReady) p.speak(text, languageTag)
                else if (androidTtsReady) androidTts?.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
            } catch (e: Throwable) {
                Log.w(TAG, "tts failed: $e")
            }
        }
    }

    private lateinit var engine: SafeLogEngine
    private val explainer = TelltaleExplainer(speaker) { a ->
        log("ADVICE ${a.type} offerShop=${a.offerShop}")
        offer = if (a.offerShop) a else null
        view.setAdvice(a)
    }

    private val share = SharePolicy(object : ContactNotifier {
        override fun notifyContact(contact: Contact, message: String) {
            // PoC: the card is built and handed to the notifier; no SMS/messenger channel yet.
            log("SHARE_SENT to=${contact.name} lines=${message.lines().size}\n$message")
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = DemoView(this)
        view.footer = intent.getStringExtra("footer") ?: "Pleos Connect Emulator · 차량 신호: Virtual Console / VHAL"
        view.onButton = { onButton(it) }
        setContentView(view)
        if (intent.getBooleanExtra("fullscreen", false)) hideSystemBars()
        // logged so a test script can tap the on-screen buttons (layout unit 1920x1080, fitted + centered)
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            log("VIEW_BOUNDS ${loc[0]} ${loc[1]} ${v.width} ${v.height}")
        }

        pleosTts = try { PleosTtsSpeaker(this) } catch (e: Throwable) { Log.w(TAG, "pleos tts: $e"); null }
        try {
            androidTts = TextToSpeech(this) { st ->
                androidTtsReady = st == TextToSpeech.SUCCESS &&
                    (androidTts?.setLanguage(Locale.KOREA) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= 0
                log("android tts ready=$androidTtsReady")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "android tts: $e")
        }

        val clock = object : Clock {
            override fun nowMs() = System.currentTimeMillis()
            override fun localHour(epochMs: Long): Int =
                Calendar.getInstance().apply { timeInMillis = epochMs }.get(Calendar.HOUR_OF_DAY)
        }
        val fileStore = FileEventStore(filesDir.resolve("events"))
        engine = SafeLogEngine(
            speaker = speaker,
            store = object : EventStore {
                override fun save(record: EventRecord) {
                    fileStore.save(record)
                    lastRecord = record
                    log("EVENT_SAVED type=${record.type} samples=${record.samples.size}")
                }
            },
            clock = clock,
            alerts = EventAlertPolicy(
                { emptyList() },
                object : ContactNotifier { override fun notifyContact(contact: Contact, message: String) {} },
                object : DriverNotifier { override fun remind(message: String) = speaker.speak(message) },
            ),
            onTripSummary = { t ->
                trips += t
                log("TRIP_SUMMARY ${t.toKorean()}")
            },
        )

        startNavi(intent.getBooleanExtra("route", false))
        adapter = VehicleSignalAdapter(this).also { it.start(listener) }
        log("LIVE_START")

        val f = IntentFilter().apply { addAction(ACTION_VOICE); addAction(ACTION_UI); addAction(ACTION_TELLTALE) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(receiver, f)
    }

    // ------------------------------------------------------------------ signals
    private val listener = object : SignalListener {
        override fun onSpeed(tMs: Long, kph: Double) {
            if (Math.abs(kph - this@LiveActivity.kph) >= 1.0) log("SIG kph=${"%.1f".format(kph)}")
            this@LiveActivity.kph = kph
            engine.onSpeed(tMs, kph)
            refresh()
        }

        override fun onBrake(tMs: Long, pressed: Boolean) {
            if (pressed != brake) log("SIG brake=$pressed")
            brake = pressed
            engine.onBrake(tMs, pressed)
            refresh()
        }

        override fun onGear(tMs: Long, gear: Gear) {
            if (gear != this@LiveActivity.gear) log("SIG gear=$gear")
            this@LiveActivity.gear = gear
            if (gear == Gear.PARK) { offer = null; view.setAdvice(null) }
            engine.onGear(tMs, gear)
            // gear change is also a sample point, so parking prompts don't wait for the next speed tick
            engine.onSpeed(tMs, kph)
            refresh()
        }

        override fun onAirbagDeployed(tMs: Long) = engine.onAirbagDeployed(tMs)

        override fun onTelltales(tMs: Long, readings: List<TelltaleReading>) {
            val on = readings.filter { it.on }.map { it.type }.toSet()
            if (on != telltalesOn) log("SIG telltales=$on")
            synchronized(telltalesOn) { telltalesOn.clear(); telltalesOn.addAll(on) }
            engine.onTelltales(tMs, readings)
            explainer.onTelltales(tMs, readings)
            if (on.isEmpty()) { view.setAdvice(null); offer = null }
            refresh()
        }
    }

    private fun refresh() {
        view.parkedButtons = gear == Gear.PARK && kph < 1.0 && trips.isNotEmpty()
        view.onSignals(gear, kph, brake, synchronized(telltalesOn) { telltalesOn.toList() })
    }

    // -------------------------------------------------------------- driver input
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                ACTION_VOICE -> onVoice(i.getStringExtra("text") ?: "")
                ACTION_UI -> onButton(i.getStringExtra("button") ?: "")
                ACTION_TELLTALE -> injectTelltale(i.getStringExtra("type") ?: "", i.getBooleanExtra("on", true))
            }
        }
    }

    /**
     * Telltales need pleos.car.permission.PRIVATE_CAR_SAFETY, which a third-party app does not get on the
     * Pleos Connect Emulator 2.1.0 (registerTelltales and getTelltalesInfo both throw). For the demo the reading
     * is handed in here and goes through the same path as an SDK reading.
     */
    private fun injectTelltale(type: String, on: Boolean) {
        val t = WarningTelltale.values().firstOrNull { it.name == type } ?: return
        log("INJECT telltale $t on=$on")
        val now = System.currentTimeMillis()
        val readings = WarningTelltale.values().map { TelltaleReading(it, if (it == t) on else it in telltalesOn) }
        listener.onTelltales(now, readings)
    }

    private fun onVoice(text: String) {
        val intent = VoiceReply.classify(text)
        log("VOICE \"$text\" -> $intent")
        view.setDriverSaid(text)
        main.postDelayed({ view.setDriverSaid("") }, 6_000)
        val o = offer
        when {
            o != null && intent == ReplyIntent.YES -> { offer = null; addShopWaypoint() }
            o != null && intent == ReplyIntent.NO -> { offer = null; speaker.speak("알겠어요. 오늘 안에 꼭 점검해 주세요.") }
            o != null && intent == ReplyIntent.REPEAT -> speaker.speak(o.speech)
            day != null && intent == ReplyIntent.SEND -> send()
            else -> speaker.speak("다시 한 번 말씀해 주세요.")
        }
    }

    private fun onButton(id: String) {
        log("BUTTON $id")
        when (id) {
            "record" -> lastRecord?.let { view.setRecord(it); view.setMode(DemoView.Mode.RECORD); log("SCREEN record") }
                ?: speaker.speak("저장된 페달 기록이 없어요.")
            "review" -> {
                val d = DayReview.of(trips.toList(), synchronized(telltalesOn) { telltalesOn.toList() })
                day = d
                view.setDay(d)
                view.setMode(DemoView.Mode.REVIEW)
                log("SCREEN review ${d.lines()}")
                speaker.speak("오늘도 안전하게 운전하셨어요. 이 내용은 저만 알고 있어요.")
                main.postDelayed({ if (day != null) speaker.speak("가족에게 보내고 싶으시면 말씀해 주세요.") }, 4_500)
            }
            "send" -> send()
            "close" -> { day = null; view.setMode(DemoView.Mode.DRIVING); log("SCREEN driving") }
        }
    }

    private fun send() {
        val d = day ?: return
        val card = d.shareCard()
        share.driverShare(Contact("family", "가족"), card)
        view.setSent(card)
        speaker.speak("요약 카드를 보냈어요. 위치와 경로는 들어 있지 않아요.")
    }

    // --------------------------------------------------------------- navigation
    private val naviListener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) { naviLog("경로 안내 시작") }
        override fun onRoutingCompleted() { naviLog("경로 탐색 완료") }
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            naviLog("경유지 변경됨")
            navi?.getWaypointInfo()
        }
        override fun onWayPointInfo(waypointInfo: List<WaypointInfo>) {
            log("NAVI waypoints=${waypointInfo.map { it.name }}")
            if (waypointInfo.isNotEmpty()) naviLog("경유지 " + waypointInfo.joinToString(", ") { it.name })
        }
        override fun onError(errorCode: NaviErrorCode) { naviLog("오류 $errorCode") }
    }

    private fun naviLog(s: String) {
        log("NAVI $s")
        view.setNaviStatus(s)
    }

    private fun startNavi(route: Boolean) {
        try {
            val n = NaviHelper(applicationContext)
            navi = n
            n.initialize({
                naviConnected = true
                naviLog("연결됨")
                n.addListener(naviListener)
                if (route) {
                    n.requestRoute(
                        RouteInfo(
                            latitude = HOSPITAL_LAT, longitude = HOSPITAL_LON,
                            displayLatitude = null, displayLongitude = null,
                            poiName = "분당서울대학교병원", poiId = "", address = "경기도 성남시 분당구 구미로173번길 82",
                            poiSubId = "0", routeOption = RouteOption.RECOMMENDED,
                        ),
                    )
                    naviLog("목적지: 분당서울대학교병원")
                }
            }, { e ->
                naviLog("연결 실패 (${e.javaClass.simpleName})")
            })
        } catch (e: Throwable) {
            Log.w(TAG, "navi init failed", e)
            naviLog("사용 불가")
        }
    }

    private fun addShopWaypoint() {
        val n = navi
        if (n == null || !naviConnected) {
            speaker.speak("지금은 내비게이션에 연결할 수 없어요.")
            return
        }
        n.addWaypoint(
            RequestWaypointInfo(
                latitude = SHOP_LAT, longitude = SHOP_LON, poiName = "가까운 정비소",
                waypointIndex = WaypointIndex.FIRST, poiId = "", address = "경기도 성남시 분당구", poiSubId = "0",
            ),
        )
        naviLog("정비소 경유지 추가 요청")
        speaker.speak("정비소를 경유지로 추가했어요.")
    }

    // -------------------------------------------------------------------- misc
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
        try { unregisterReceiver(receiver) } catch (_: Throwable) {}
        adapter?.stop()
        try { navi?.removeListener(naviListener); navi?.release() } catch (_: Throwable) {}
        pleosTts?.release()
        try { androidTts?.shutdown() } catch (_: Throwable) {}
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SafeLog"
        const val ACTION_VOICE = "com.hmg.safelog.poc.VOICE"
        const val ACTION_UI = "com.hmg.safelog.poc.UI"
        const val ACTION_TELLTALE = "com.hmg.safelog.poc.TELLTALE"
        const val HOSPITAL_LAT = 37.3520
        const val HOSPITAL_LON = 127.1235
        const val SHOP_LAT = 37.3596
        const val SHOP_LON = 127.1149
    }
}
