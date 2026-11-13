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
import com.hmg.safelog.core.EventRecord
import com.hmg.safelog.core.Gear
import com.hmg.safelog.core.PedalGuardConfig
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
    private var shareOn = false

    private val pedalMessage = PedalGuardConfig().message

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)

    fun setMode(m: Mode) { modeSince = clock(); mode = m; postInvalidate() }
    fun setRecord(r: EventRecord) { record = r }
    fun setTrip(t: TripSummary) { trip = t }

    fun onSignals(g: Gear, kph: Double, b: Boolean, tt: List<WarningTelltale>) {
        gear = g; speed = kph; brake = b; telltales = tt
        if (b && alertAt != 0L && clock() - alertAt > 300) bannerOff = true
        postInvalidate()
    }

    fun onUtterance(text: String) {
        utterance = text
        if (text == pedalMessage) {
            bannerText = text
            bannerOff = false
            alertAt = clock()
        }
        postInvalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN && mode == Mode.REVIEW) {
            val u = width / 1920f
            if (e.x in 1100 * u..1860 * u && e.y in 700 * u..900 * u) { shareOn = !shareOn; invalidate() }
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
        val u = width / 1920f
        c.drawColor(BG)
        c.save()
        c.scale(u, u)
        when (mode) {
            Mode.DRIVING -> drawDriving(c)
            Mode.RECORD -> drawRecord(c)
            Mode.REVIEW -> drawReview(c)
        }
        text(c, "시뮬레이션 신호(CSV) 재생 · 실제 차량 아님", 1860f, 1056f, 30f, 0xFF78909C.toInt(), Paint.Align.RIGHT)
        c.restore()
        // keep animating (banner flash, record reveal)
        postInvalidateOnAnimation()
    }

    // ---------------------------------------------------------------- driving
    private fun drawDriving(c: Canvas) {
        val now = clock()
        text(c, "SafeLog", 60f, 90f, 56f, Color.WHITE, bold = true)
        val tt = telltales
        if (tt.isNotEmpty()) {
            text(c, "경고등 켜짐: " + tt.joinToString(" ") { it.name }, 1860f, 90f, 40f, 0xFFFFB74D.toInt(), Paint.Align.RIGHT)
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
        text(c, "이 기록은 내가 열 때만 보여요", 960f, 1000f, 62f, Color.WHITE, Paint.Align.CENTER, bold = true, maxW = 1240f)
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

        // share toggle
        text(c, "가족에게 보내기", 100f, 825f, 76f, Color.WHITE, bold = true)
        val track = RectF(1280f, 725f, 1540f, 855f)
        box(c, track, 65f, if (shareOn) 0xFF43A047.toInt() else 0xFF546E7A.toInt())
        p.style = Paint.Style.FILL; p.color = Color.WHITE
        c.drawCircle(if (shareOn) 1475f else 1345f, 790f, 52f, p)
        text(c, if (shareOn) "켜짐" else "꺼짐", 1590f, 820f, 64f,
            if (shareOn) 0xFF81C784.toInt() else 0xFFB0BEC5.toInt(), bold = true)

        val note = RectF(60f, 910f, 1860f, 1030f)
        box(c, note, 50f, 0xFF1B2733.toInt(), 0xFF66BB6A.toInt(), 5f)
        text(c, "위치·경로는 보내지 않아요", 960f, 990f, 66f, Color.WHITE, Paint.Align.CENTER, bold = true, maxW = 1700f)
    }

    private companion object {
        const val BG = 0xFF0E1218.toInt()
    }
}
