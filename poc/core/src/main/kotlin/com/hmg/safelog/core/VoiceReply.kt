package com.hmg.safelog.core

enum class ReplyIntent { YES, NO, SEND, REPEAT, UNKNOWN }

/** Short Korean voice replies -> intent. Keyword based; the recognizer (Pleos STT) supplies the text. */
object VoiceReply {
    private val send = listOf("보내", "공유")
    private val no = listOf("아니", "괜찮아", "나중에", "됐어", "싫어")
    private val repeat = listOf("다시", "뭐라고")
    private val yes = listOf("응", "네", "예", "그래", "좋아", "안내해", "가자", "부탁")

    fun classify(text: String): ReplyIntent {
        val t = text.replace(" ", "")
        return when {
            t.isEmpty() -> ReplyIntent.UNKNOWN
            no.any { t.contains(it) } -> ReplyIntent.NO
            send.any { t.contains(it) } -> ReplyIntent.SEND
            repeat.any { t.contains(it) } -> ReplyIntent.REPEAT
            yes.any { t.contains(it) } -> ReplyIntent.YES
            else -> ReplyIntent.UNKNOWN
        }
    }
}
