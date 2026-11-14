package com.hmg.safelog.core

/** How soon the driver should act on a warning telltale. */
enum class Urgency { STOP_NOW, TODAY, THIS_WEEK }

/** Plain-language explanation of one warning telltale (shown and spoken to the driver). */
data class TelltaleAdvice(
    val type: WarningTelltale,
    val title: String,
    val speech: String,
    val urgency: Urgency,
    /** Offer a repair shop as a navigation waypoint (only when it is safe to keep driving). */
    val offerShop: Boolean,
)

object TelltaleGuide {
    const val SHOP_QUESTION = "가까운 정비소로 같이 갈까요?"

    fun advice(type: WarningTelltale): TelltaleAdvice = when (type) {
        WarningTelltale.TPMS -> TelltaleAdvice(
            type, "타이어 공기압 낮음",
            "타이어 바람이 조금 빠졌어요. 지금 위험하지는 않아요. 오늘 안에 넣어 주세요.",
            Urgency.TODAY, true,
        )
        WarningTelltale.ENGINE_OIL_PRESSURE -> TelltaleAdvice(
            type, "엔진 오일 압력 낮음",
            "엔진 오일 압력이 낮아요. 안전한 곳에 차를 세우고 시동을 꺼 주세요.",
            Urgency.STOP_NOW, false,
        )
        WarningTelltale.AIRBAG -> TelltaleAdvice(
            type, "에어백 점검",
            "에어백 경고등이 켜졌어요. 사고 때 에어백이 펴지지 않을 수 있어요. 오늘 점검을 받아 주세요.",
            Urgency.TODAY, true,
        )
        WarningTelltale.ABS -> TelltaleAdvice(
            type, "ABS 점검",
            "ABS 경고등이 켜졌어요. 브레이크는 작동하지만 미끄러운 길에서 조심하세요. 이번 주 안에 점검받으세요.",
            Urgency.THIS_WEEK, true,
        )
        WarningTelltale.ESC -> TelltaleAdvice(
            type, "차체 자세 제어 점검",
            "차체 자세 제어 경고등이 켜졌어요. 급하게 돌지 말고 천천히 운전하세요. 이번 주 안에 점검받으세요.",
            Urgency.THIS_WEEK, true,
        )
        WarningTelltale.ENGINE -> TelltaleAdvice(
            type, "엔진 점검",
            "엔진 점검등이 켜졌어요. 지금 바로 위험하지는 않아요. 이번 주 안에 점검받으세요.",
            Urgency.THIS_WEEK, true,
        )
        WarningTelltale.MASTER_WARNING -> TelltaleAdvice(
            type, "차량 경고",
            "차량 경고가 있어요. 차를 세운 뒤 계기판 메시지를 확인해 주세요.",
            Urgency.TODAY, false,
        )
    }
}

/**
 * Speaks a plain explanation the moment a watched telltale turns on (once per off -> on change),
 * then asks whether to add a repair shop. The multi-day reminder stays in [EventAlertPolicy].
 */
class TelltaleExplainer(
    private val speaker: Speaker,
    private val onAdvice: (TelltaleAdvice) -> Unit = {},
) {
    private val on = HashSet<WarningTelltale>()

    fun onTelltales(tMs: Long, readings: List<TelltaleReading>) {
        for (r in readings) {
            if (!r.on) {
                on.remove(r.type)
                continue
            }
            if (!on.add(r.type)) continue
            val a = TelltaleGuide.advice(r.type)
            speaker.speak(a.speech)
            if (a.offerShop) speaker.speak(TelltaleGuide.SHOP_QUESTION)
            onAdvice(a)
        }
    }
}
