package com.hmg.safelog.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
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
 * Process-wide live session behind [LiveActivity]. Pleos destroys and recreates a non-DO activity when driving
 * starts, so the engine, SDK connections and the screen view live here and survive activity instances.
 *
 * Live mode for the Pleos Connect Emulator: vehicle signals come from the REAL Pleos Vehicle SDK
 * (gear / brake / telltales) and AAOS CarPropertyManager (speed), i.e. whatever the Virtual Console
 * or VHAL sets. Same core engine as the service; adds scene A (telltale advice + NaviHelper waypoint)
 * and scene C (day review + driver-initiated share).
 *
 * adb shell am start -n com.hmg.safelog.poc/com.hmg.safelog.app.LiveActivity [--ez route true]
 * Driver voice reply (no microphone on the emulator, so the recognized text is handed in):
 *   adb shell am broadcast -a com.hmg.safelog.poc.VOICE --es text "응, 안내해 줘" -p com.hmg.safelog.poc
 */
object LiveSession {
    private lateinit var app: Context
    lateinit var view: DemoView
        private set
    @Volatile private var started = false
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
                val via = when {
                    p != null && p.isReady -> { p.speak(text, languageTag); "pleos" }
                    androidTtsReady -> { androidTts?.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString()); "android" }
                    else -> "none"
                }
                log("TTS_VIA $via")
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

    /** First call creates the engine and SDK connections; later activity instances only re-attach the view. */
    @Synchronized
    fun start(context: Context, route: Boolean, frames: Boolean) {
        if (started) return
        started = true
        app = context.applicationContext
        view = DemoView(app)
        view.footer = "Pleos Connect Emulator · 차량 신호: Virtual Console → Pleos Vehicle SDK"
        view.onButton = { onButton(it) }

        pleosTts = try { PleosTtsSpeaker(app) } catch (e: Throwable) { Log.w(TAG, "pleos tts: $e"); null }
        try {
            androidTts = TextToSpeech(app) { st ->
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
        val fileStore = FileEventStore(app.filesDir.resolve("events"))
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

        if (frames) startFrames()
        watchUxRestrictions()
        startNavi(route)
        adapter = VehicleSignalAdapter(app).also { it.start(listener) }
        log("LIVE_START")

        val f = IntentFilter().apply { addAction(ACTION_VOICE); addAction(ACTION_UI); addAction(ACTION_TELLTALE) }
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED)
        else app.registerReceiver(receiver, f)
    }

    // ------------------------------------------------------------------ signals
    private val listener = object : SignalListener {
        override fun onSpeed(tMs: Long, kph: Double) {
            if (Math.abs(kph - LiveSession.kph) >= 1.0) log("SIG kph=${"%.1f".format(kph)}")
            LiveSession.kph = kph
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
            if (gear != LiveSession.gear) log("SIG gear=$gear")
            LiveSession.gear = gear
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
        view.setParked(gear == Gear.PARK && kph < 1.0 && trips.isNotEmpty())
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
            val n = NaviHelper(app)
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

    // ------------------------------------------------------------ frame capture
    private var frameThread: android.os.HandlerThread? = null

    /**
     * Saves what SafeLog draws (960x540 JPEG, 4 fps, file name = epoch ms) even while Pleos hides the activity
     * during driving. The video shows these next to the real Pleos screen. Pull: run-as ... files/frames
     */
    private fun startFrames() {
        val dir = app.filesDir.resolve("frames").apply { deleteRecursively(); mkdirs() }
        val shadow = DemoView(app)
        shadow.footer = view.footer
        shadow.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        )
        shadow.layout(0, 0, 1920, 1080)
        view.mirror = shadow
        val t = android.os.HandlerThread("safelog-frames").also { it.start() }
        frameThread = t
        val h = Handler(t.looper)
        val bmp = android.graphics.Bitmap.createBitmap(960, 540, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp).apply { scale(0.5f, 0.5f) }
        h.post(object : Runnable {
            override fun run() {
                try {
                    shadow.draw(canvas)
                    java.io.FileOutputStream(dir.resolve("${System.currentTimeMillis()}.jpg")).use {
                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it)
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "frame failed: $e")
                }
                h.postDelayed(this, 250)
            }
        })
        log("FRAMES_START")
    }

    // ------------------------------------------------------- driving restriction
    private var uxCar: android.car.Car? = null

    /** Pleos driving restriction (CarUxRestrictions): log it so the demo shows what the platform allows. */
    private fun watchUxRestrictions() {
        try {
            val car = android.car.Car.createCar(app)
            uxCar = car
            val pm = car.getCarManager(android.car.Car.PACKAGE_SERVICE) as android.car.content.pm.CarPackageManager
            log("DO_DECLARED_HONORED ${pm.isActivityDistractionOptimized(app.packageName, LiveActivity::class.java.name)}")
            val ux = car.getCarManager(android.car.Car.CAR_UX_RESTRICTION_SERVICE) as android.car.drivingstate.CarUxRestrictionsManager
            val show = { r: android.car.drivingstate.CarUxRestrictions ->
                log("UXR requiresDO=${r.isRequiresDistractionOptimization} active=0x${Integer.toHexString(r.activeRestrictions)}")
            }
            ux.currentCarUxRestrictions?.let(show)
            ux.registerListener { r -> show(r) }
        } catch (e: Throwable) {
            Log.w(TAG, "ux restrictions unavailable: $e")
        }
    }

    private const val TAG = "SafeLog"
    const val ACTION_VOICE = "com.hmg.safelog.poc.VOICE"
    const val ACTION_UI = "com.hmg.safelog.poc.UI"
    const val ACTION_TELLTALE = "com.hmg.safelog.poc.TELLTALE"
    private const val HOSPITAL_LAT = 37.3520
    private const val HOSPITAL_LON = 127.1235
    private const val SHOP_LAT = 37.3596
    private const val SHOP_LON = 127.1149
}
