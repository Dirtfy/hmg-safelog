package com.hmg.safelog.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import com.hmg.safelog.core.DayReview
import com.hmg.safelog.core.EventRecord
import com.hmg.safelog.core.Gear
import com.hmg.safelog.core.PedalGuardConfig
import com.hmg.safelog.core.TelltaleAdvice
import com.hmg.safelog.core.TelltaleGuide
import com.hmg.safelog.core.Urgency
import com.hmg.safelog.core.TripSummary
import com.hmg.safelog.core.WarningTelltale
import kotlin.math.max

/** Canvas-drawn dark car-display UI (driving / record / trip review). Layout unit: 1920 x 1080. */
class DemoView(context: Context) : View(context) {
    enum class Mode { DRIVING, RECORD, REVIEW }

    @Volatile private var mode = Mode.DRIVING
    /** Time source; frame-capture mode swaps in a virtual clock. */
    var clock: () -> Long = { SystemClock.uptimeMillis() }
    @Volatile private var modeSince = SystemClock.uptimeMillis()
    @Volatile private var gear = Gear.PARK
    @Volatile private var speed = 0.0
    @Volatile private var brake = true
    @Volatile private var telltales: List<WarningTelltale> = emptyList()
    @Volatile private var utterance = ""
    @Volatile private var alertAt = 0L
    @Volatile private var bannerText = ""
    @Volatile private var bannerOff = false
    @Volatile private var record: EventRecord? = null
    @Volatile private var trip: TripSummary? = null
    /** Bottom-right source label. */
    var footer = "시뮬레이션 신호(CSV) 재생 · 실제 차량 아님"
    // live mode (Pleos): telltale advice card, navigation status, driver reply, day review, sent card
    @Volatile private var advice: TelltaleAdvice? = null
    @Volatile private var naviStatus = ""
    @Volatile private var driverSaid = ""
    @Volatile private var day: DayReview? = null
    @Volatile private var sentCard: String? = null
    /** Parked-screen buttons (live mode): id -> rect in 1920x1080 units. */
    var onButton: (String) -> Unit = {}
    @Volatile var parkedButtons = false

    private val pedalMessage = PedalGuardConfig().message

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)

    /** Offscreen copy that receives every state change (LiveActivity saves it as frames). */
    var mirror: DemoView? = null

    fun setMode(m: Mode) { modeSince = clock(); mode = m; postInvalidate(); mirror?.setMode(m) }
    fun setRecord(r: EventRecord) { record = r; mirror?.setRecord(r) }
    fun setTrip(t: TripSummary) { trip = t; mirror?.setTrip(t) }
    fun setAdvice(a: TelltaleAdvice?) { advice = a; postInvalidate(); mirror?.setAdvice(a) }
    fun setNaviStatus(s: String) { naviStatus = s; postInvalidate(); mirror?.setNaviStatus(s) }
    fun setDriverSaid(s: String) { driverSaid = s; postInvalidate(); mirror?.setDriverSaid(s) }
    fun setDay(d: DayReview) { day = d; sentCard = null; postInvalidate(); mirror?.setDay(d) }
    fun setSent(card: String) { sentCard = card; postInvalidate(); mirror?.setSent(card) }
    fun setParked(on: Boolean) { parkedButtons = on; mirror?.setParked(on) }

    fun onSignals(g: Gear, kph: Double, b: Boolean, tt: List<WarningTelltale>) {
        mirror?.onSignals(g, kph, b, tt)
        gear = g; speed = kph; brake = b; telltales = tt
        if (b && alertAt != 0L && clock() - alertAt > 300) bannerOff = true
        postInvalidate()
    }

    fun onUtterance(text: String) {
        mirror?.onUtterance(text)
        utterance = text
        if (text == pedalMessage) {
            bannerText = text
            bannerOff = false
            alertAt = clock()
        }
        postInvalidate()
    }

    /** 1920x1080 layout fitted into the window (Pleos runs apps in a non-16:9 window). */
    private fun unit() = minOf(width / 1920f, height / 1080f)
    private fun offX() = (width - 1920f * unit()) / 2f
    private fun offY() = (height - 1080f * unit()) / 2f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) {
            val u = unit()
            val x = (e.x - offX()) / u; val y = (e.y - offY()) / u
            when {
                mode == Mode.DRIVING && parkedButtons && y in 640f..740f && x in 60f..900f -> onButton("record")
                mode == Mode.DRIVING && parkedButtons && y in 640f..740f && x in 960f..1860f -> onButton("review")
                mode != Mode.DRIVING && x > 1500f && y < 160f -> onButton("close")
            }
        }
        return true
    }

    private fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        align: Paint.Align = Paint.Align.LEFT, bold: Boolean = false, maxW: Float = 0f,
    ) {
        p.style = Paint.Style.FILL
        p.color = color
        p.textAlign = align
        p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        p.textSize = size
        if (maxW > 0f) {
            val w = p.measureText(s)
            if (w > maxW) p.textSize = size * maxW / w
        }
        c.drawText(s, x, y, p)
    }

    private fun box(c: Canvas, r: RectF, rad: Float, fill: Int, stroke: Int = 0, sw: Float = 0f) {
        p.style = Paint.Style.FILL; p.color = fill
        c.drawRoundRect(r, rad, rad, p)
        if (stroke != 0) {
            p.style = Paint.Style.STROKE; p.strokeWidth = sw; p.color = stroke
            c.drawRoundRect(r, rad, rad, p)
            p.style = Paint.Style.FILL
        }
    }

    override fun onDraw(c: Canvas) {
        val u = unit()
        c.drawColor(BG)
        c.save()
        c.translate(offX(), offY())
        c.scale(u, u)
        when (mode) {
            Mode.DRIVING -> drawDriving(c)
            Mode.RECORD -> drawRecord(c)
            Mode.REVIEW -> if (day != null) drawDay(c) else drawReview(c)
        }
        text(c, footer, 1860f, 1056f, 30f, 0xFF78909C.toInt(), Paint.Align.RIGHT)
        c.restore()
        // keep animating (banner flash, record reveal) at 10 fps: every-vsync redraws starve the UI thread
        // under software rendering (Pleos emulator without GPU)
        postInvalidateDelayed(100)
    }

    // ---------------------------------------------------------------- driving
    private fun drawDriving(c: Canvas) {
        val now = clock()
        text(c, "SafeLog", 60f, 90f, 56f, Color.WHITE, bold = true)
        val tt = telltales
        if (tt.isNotEmpty()) {
            text(c, "경고등 켜짐: " + tt.joinToString(", ") { TelltaleGuide.advice(it).title }, 1860f, 90f, 40f, 0xFFFFB74D.toInt(), Paint.Align.RIGHT)
        }

        // flashing pedal banner (full width)
        val bannerOn = alertAt != 0L && !bannerOff && now - alertAt < 8_000
        if (bannerOn) {
            val flash = ((now - alertAt) / 200) % 2 == 0L
            p.style = Paint.Style.FILL
            p.color = if (flash) 0xFFE53935.toInt() else 0xFF8E0000.toInt()
            c.drawRect(0f, 0f, 1920f, 230f, p)
            text(c, bannerText, 960f, 150f, 100f, Color.WHITE, Paint.Align.CENTER, bold = true, maxW = 1800f)
        }

        // gear
        val letters = listOf(Gear.PARK to "P", Gear.REVERSE to "R", Gear.NEUTRAL to "N", Gear.DRIVE to "D")
        text(c, "기어", 60f, 330f, 40f, 0xFF90A4AE.toInt())
        for ((i, pair) in letters.withIndex()) {
            val x = 60f + i * 150f
            val active = gear == pair.first
            val r = RectF(x, 370f, x + 130f, 600f)
            if (active) {
                val col = when (pair.first) {
                    Gear.REVERSE -> 0xFFFFA726.toInt()
                    Gear.DRIVE -> 0xFF66BB6A.toInt()
                    Gear.PARK -> 0xFF42A5F5.toInt()
                    else -> 0xFFB0BEC5.toInt()
                }
                box(c, r, 24f, col)
                text(c, pair.second, x + 65f, 540f, 150f, 0xFF10161D.toInt(), Paint.Align.CENTER, bold = true)
            } else {
                box(c, r, 24f, 0xFF18222D.toInt())
                text(c, pair.second, x + 65f, 540f, 150f, 0xFF4A5A68.toInt(), Paint.Align.CENTER, bold = true)
            }
        }

        // speed
        text(c, "속도", 960f, 330f, 40f, 0xFF90A4AE.toInt(), Paint.Align.CENTER)
        text(c, Math.round(speed).toString(), 960f, 600f, 300f, Color.WHITE, Paint.Align.CENTER, bold = true)
        text(c, "km/h", 960f, 680f, 60f, 0xFF90A4AE.toInt(), Paint.Align.CENTER)

        // brake
        text(c, "브레이크", 1360f, 330f, 40f, 0xFF90A4AE.toInt())
        val br = RectF(1360f, 370f, 1860f, 700f)
        if (brake) {
            box(c, br, 36f, 0xFF2E7D32.toInt())
            text(c, "밟음", 1610f, 580f, 150f, Color.WHITE, Paint.Align.CENTER, bold = true)
        } else {
            val warn = speed > 3 && (gear == Gear.REVERSE || gear == Gear.DRIVE)
            box(c, br, 36f, 0xFF1C2833.toInt(), if (warn) 0xFFFFC107.toInt() else 0xFF455A64.toInt(), 8f)
            text(c, "뗌", 1610f, 580f, 150f, if (warn) 0xFFFFC107.toInt() else 0xFFB0BEC5.toInt(), Paint.Align.CENTER, bold = true)
        }

        // voice bubble
        val bubble = RectF(60f, 770f, 1860f, 1010f)
        box(c, bubble, 48f, 0xFF1B2733.toInt(), 0xFF4FC3F7.toInt(), 5f)
        text(c, "음성 안내", 110f, 835f, 38f, 0xFF4FC3F7.toInt(), bold = true)
        val u = utterance
        if (u.isEmpty()) {
            text(c, "대기 중", 110f, 940f, 64f, 0xFF607D8B.toInt())
        } else {
            text(c, u, 110f, 950f, 84f, Color.WHITE, bold = true, maxW = 1700f)
        }
        if (driverSaid.isNotEmpty()) {
            text(c, "운전자: $driverSaid", 1810f, 835f, 40f, 0xFFFFD54F.toInt(), Paint.Align.RIGHT, bold = true)
        }
        if (naviStatus.isNotEmpty() && !bannerOn) {
            text(c, "내비게이션: $naviStatus", 60f, 160f, 40f, 0xFF81C784.toInt(), maxW = 1300f)
        }
        val a = advice
        if (a != null && !bannerOn) drawAdvice(c, a)
        if (parkedButtons && a == null && !bannerOn) {
            box(c, RectF(60f, 640f, 900f, 740f), 50f, 0xFF263544.toInt(), 0xFF4FC3F7.toInt(), 4f)
            text(c, "기록 보기", 480f, 712f, 56f, Color.WHITE, Paint.Align.CENTER, bold = true)
            box(c, RectF(960f, 640f, 1860f, 740f), 50f, 0xFF263544.toInt(), 0xFF66BB6A.toInt(), 4f)
            text(c, "오늘 운전 돌아보기", 1410f, 712f, 56f, Color.WHITE, Paint.Align.CENTER, bold = true)
        }
    }

    /** Scene A: plain-language telltale card over the gear/speed area (voice first; no touch needed). */
    private fun drawAdvice(c: Canvas, a: TelltaleAdvice) {
        val r = RectF(60f, 220f, 1860f, 740f)
        val col = when (a.urgency) {
            Urgency.STOP_NOW -> 0xFFE53935.toInt()
            Urgency.TODAY -> 0xFFFFA726.toInt()
            Urgency.THIS_WEEK -> 0xFFFFD54F.toInt()
        }
        box(c, r, 40f, 0xFF1B2733.toInt(), col, 8f)
        text(c, "경고등 · ${a.title}", 110f, 320f, 72f, col, bold = true, maxW = 1700f)
        val urg = when (a.urgency) {
            Urgency.STOP_NOW -> "지금 차를 세우세요"
            Urgency.TODAY -> "오늘 안에 조치"
            Urgency.THIS_WEEK -> "이번 주 안에 점검"
        }
        text(c, urg, 1810f, 320f, 56f, Color.WHITE, Paint.Align.RIGHT, bold = true)
        tp.color = Color.WHITE; tp.textSize = 58f; tp.typeface = Typeface.DEFAULT
        val layout = StaticLayout.Builder.obtain(a.speech, 0, a.speech.length, tp, 1700)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.2f).build()
        c.save(); c.translate(110f, 370f); layout.draw(c); c.restore()
        if (a.offerShop) {
            text(c, "\"응\" 하고 말하면 가까운 정비소를 경유지로 넣어요", 110f, 700f, 48f, 0xFF4FC3F7.toInt(), maxW = 1700f)
        }
    }

    // ----------------------------------------------------------------- record
    private fun drawRecord(c: Canvas) {
        text(c, "기록 보기", 60f, 110f, 80f, Color.WHITE, bold = true)
        text(c, "페달 오조작 의심 · 경고 전후 기록", 60f, 175f, 44f, 0xFF90A4AE.toInt())
        val rec = record
        val s = rec?.samples.orEmpty()
        if (rec == null || s.size < 2) return
        val progress = ((clock() - modeSince) / 3000f).coerceIn(0f, 1f)

        val cl = 230f; val cr = 1840f; val ct = 250f; val cb = 740f
        val t0 = s.first().tMs; val t1 = s.last().tMs
        val vMax = (max(20.0, Math.ceil(s.maxOf { it.speedKph } / 10.0) * 10.0)).toFloat()
        fun x(t: Long) = cl + (cr - cl) * (t - t0).toFloat() / (t1 - t0).toFloat()
        fun y(v: Double) = cb - (cb - ct) * v.toFloat() / vMax

        // grid
        var g = 0
        while (g <= vMax.toInt()) {
            val yy = y(g.toDouble())
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0xFF263340.toInt(); p.pathEffect = null
            c.drawLine(cl, yy, cr, yy, p)
            text(c, g.toString(), cl - 20f, yy + 14f, 38f, 0xFF90A4AE.toInt(), Paint.Align.RIGHT)
            g += 10
        }
        text(c, "km/h", cl - 20f, ct - 30f, 34f, 0xFF90A4AE.toInt(), Paint.Align.RIGHT)
        // x ticks relative to warning
        var sec = -((rec.triggerMs - t0) / 5000) * 5
        while (rec.triggerMs + sec * 1000L <= t1) {
            val tt = rec.triggerMs + sec * 1000L
            if (tt >= t0) {
                val label = if (sec == 0L) "경고" else if (sec > 0L) "+${sec}초" else "${sec}초"
                text(c, label, x(tt), cb + 50f, 36f, 0xFF90A4AE.toInt(), Paint.Align.CENTER)
            }
            sec += 5
        }

        // speed line (revealed left to right)
        val clipR = cl + (cr - cl) * progress
        c.save()
        c.clipRect(0f, 0f, clipR, 1080f)
        val path = Path()
        for ((i, sm) in s.withIndex()) {
            if (i == 0) path.moveTo(x(sm.tMs), y(sm.speedKph)) else path.lineTo(x(sm.tMs), y(sm.speedKph))
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = 8f; p.color = 0xFF4FC3F7.toInt(); p.strokeJoin = Paint.Join.ROUND
        c.drawPath(path, p)

        // brake strip
        text(c, "브레이크", cl - 20f, 830f, 38f, 0xFF90A4AE.toInt(), Paint.Align.RIGHT)
        p.style = Paint.Style.FILL
        for (i in 0 until s.size - 1) {
            p.color = if (s[i].brake) 0xFF43A047.toInt() else 0xFF37474F.toInt()
            c.drawRect(x(s[i].tMs), 790f, x(s[i + 1].tMs) + 1f, 850f, p)
        }
        c.restore()
        text(c, "초록 = 밟음, 회색 = 뗌", cl, 905f, 34f, 0xFF90A4AE.toInt())

        // warning marker
        if (clipR >= x(rec.triggerMs)) {
            val mx = x(rec.triggerMs)
            p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.color = 0xFFFF5252.toInt()
            p.pathEffect = DashPathEffect(floatArrayOf(24f, 14f), 0f)
            c.drawLine(mx, ct - 10f, mx, 860f, p)
            p.pathEffect = null
            val tag = RectF(mx - 70f, ct - 62f, mx + 70f, ct - 6f)
            box(c, tag, 14f, 0xFFFF5252.toInt())
            text(c, "경고", mx, ct - 18f, 40f, Color.WHITE, Paint.Align.CENTER, bold = true)
        }

        // caption
        val cap = RectF(300f, 930f, 1620f, 1030f)
        box(c, cap, 50f, 0xFF1B2733.toInt(), 0xFF66BB6A.toInt(), 5f)
        text(c, "이 그래프는 가족에게 보내지 않아요 (횟수만 보내요)", 960f, 1000f, 56f, Color.WHITE, Paint.Align.CENTER, bold = true, maxW = 1240f)
    }

    // ----------------------------------------------------------------- review
    private fun drawReview(c: Canvas) {
        text(c, "이번 주행 돌아보기", 60f, 110f, 80f, Color.WHITE, bold = true)
        val card = RectF(60f, 160f, 1860f, 640f)
        box(c, card, 36f, 0xFF1B2733.toInt(), 0xFF4FC3F7.toInt(), 4f)
        val summary = trip?.toKorean() ?: "주행 기록이 없어요"
        tp.color = Color.WHITE; tp.textSize = 70f; tp.typeface = Typeface.DEFAULT_BOLD
        val layout = StaticLayout.Builder.obtain(summary, 0, summary.length, tp, 1680)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.25f).build()
        c.save(); c.translate(120f, 210f); layout.draw(c); c.restore()

        // automatic share (set up once with the family's consent)
        text(c, "가족에게 자동으로 보내요", 100f, 800f, 76f, Color.WHITE, bold = true)
        text(c, "운전이 끝나면 요약 카드(횟수만)를 보내요", 100f, 870f, 44f, 0xFFB0BEC5.toInt())
        text(c, "켜짐", 1700f, 820f, 64f, 0xFF81C784.toInt(), Paint.Align.CENTER, bold = true)

        val note = RectF(60f, 910f, 1860f, 1030f)
        box(c, note, 50f, 0xFF1B2733.toInt(), 0xFF66BB6A.toInt(), 5f)
        text(c, "위치·경로는 보내지 않아요", 960f, 990f, 66f, Color.WHITE, Paint.Align.CENTER, bold = true, maxW = 1700f)
    }

    // ------------------------------------------------------------ day review
    private fun drawDay(c: Canvas) {
        val d = day ?: return
        text(c, "오늘 운전 돌아보기", 60f, 110f, 80f, Color.WHITE, bold = true)
        text(c, "가족 자동 보내기 켜짐", 1860f, 105f, 48f, 0xFF66BB6A.toInt(), Paint.Align.RIGHT, bold = true)
        val card = RectF(60f, 160f, 1860f, 660f)
        box(c, card, 36f, 0xFF1B2733.toInt(), 0xFF4FC3F7.toInt(), 4f)
        for ((i, ln) in d.lines().withIndex()) {
            text(c, ln, 120f, 270f + i * 110f, 70f, Color.WHITE, bold = i == 0, maxW = 1680f)
        }
        val sent = sentCard
        if (sent == null) {
            text(c, "가족에게 자동으로 보내요", 100f, 760f, 66f, Color.WHITE, bold = true)
            text(c, "운전이 끝나면 요약 카드(횟수만)를 보내요 · 처음 설정 때 한 번 동의", 100f, 840f, 44f, 0xFFB0BEC5.toInt(), maxW = 1700f)
        } else {
            box(c, RectF(60f, 690f, 1860f, 900f), 40f, 0xFF1E3B2A.toInt(), 0xFF66BB6A.toInt(), 5f)
            text(c, "가족에게 자동으로 보냈어요 · 요약 카드 (횟수만)", 110f, 770f, 60f, 0xFF81C784.toInt(), bold = true, maxW = 1700f)
            text(c, sent.lines().drop(1).take(3).joinToString("  ·  "), 110f, 850f, 42f, Color.WHITE, maxW = 1700f)
        }
        val note = RectF(60f, 920f, 1860f, 1020f)
        box(c, note, 50f, 0xFF1B2733.toInt(), 0xFF66BB6A.toInt(), 5f)
        text(c, "위치·경로는 보내지 않아요", 960f, 992f, 62f, Color.WHITE, Paint.Align.CENTER, bold = true, maxW = 1700f)
    }

    private companion object {
        const val BG = 0xFF0E1218.toInt()
    }
}
