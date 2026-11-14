package com.hmg.safelog.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SceneACTest {
    private class Rec : Speaker {
        val said = ArrayList<String>()
        override fun speak(text: String, languageTag: String) { said += text }
    }

    @Test
    fun tpmsOnSpeaksPlainAdviceOnceAndOffersShop() {
        val sp = Rec()
        val got = ArrayList<TelltaleAdvice>()
        val ex = TelltaleExplainer(sp) { got += it }
        val on = listOf(TelltaleReading(WarningTelltale.TPMS, true))
        ex.onTelltales(0, on)
        ex.onTelltales(1_000, on) // still on: no repeat
        assertEquals(1, got.size)
        assertEquals(Urgency.TODAY, got[0].urgency)
        assertEquals(listOf(TelltaleGuide.advice(WarningTelltale.TPMS).speech, TelltaleGuide.SHOP_QUESTION), sp.said)
        ex.onTelltales(2_000, listOf(TelltaleReading(WarningTelltale.TPMS, false)))
        ex.onTelltales(3_000, on) // off -> on again: explained again
        assertEquals(2, got.size)
    }

    @Test
    fun oilPressureSaysStopAndDoesNotOfferShop() {
        val sp = Rec()
        TelltaleExplainer(sp).onTelltales(0, listOf(TelltaleReading(WarningTelltale.ENGINE_OIL_PRESSURE, true)))
        assertEquals(1, sp.said.size)
        assertTrue(sp.said[0].contains("세우고"))
    }

    @Test
    fun everyTelltaleHasAdvice() {
        for (t in WarningTelltale.values()) assertTrue(TelltaleGuide.advice(t).speech.isNotBlank())
    }

    @Test
    fun voiceReplies() {
        assertEquals(ReplyIntent.YES, VoiceReply.classify("응, 안내해 줘"))
        assertEquals(ReplyIntent.YES, VoiceReply.classify("네"))
        assertEquals(ReplyIntent.NO, VoiceReply.classify("아니, 나중에"))
        assertEquals(ReplyIntent.SEND, VoiceReply.classify("보내 줘"))
        assertEquals(ReplyIntent.REPEAT, VoiceReply.classify("다시 말해 줘"))
        assertEquals(ReplyIntent.UNKNOWN, VoiceReply.classify("오늘 날씨"))
    }

    @Test
    fun dayReviewShareCardHasNoLocation() {
        val trips = listOf(
            TripSummary(0, 1_200_000, 1_200_000, 8_400.0, 0, 0, 0, 0),
            TripSummary(2_000_000, 2_600_000, 600_000, 3_100.0, 1, 0, 1, 0),
        )
        val d = DayReview.of(trips, listOf(WarningTelltale.TPMS))
        assertEquals(2, d.trips)
        assertEquals(30, d.minutes)
        assertEquals(11.5, d.km)
        val card = d.shareCard()
        assertTrue(card.contains("오늘 운전 2회"))
        assertTrue(card.contains("페달 오조작 의심 1회"))
        assertTrue(card.contains("타이어 공기압 낮음"))
        for (w in listOf("위치", "경로", "주소", "위도", "경도")) assertFalse(card.contains(w), w)
    }
}
