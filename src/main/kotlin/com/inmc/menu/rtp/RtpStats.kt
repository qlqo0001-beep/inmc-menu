package com.inmc.menu.rtp

/** `/야생 상태` 의 수 — 월드마다. 켜진 동안만(메모리). 메인 스레드에서만 만진다. */
class RtpStats {

    class Line {
        var requests = 0
        var success = 0
        var failed = 0
        var cancelled = 0

        /** 자리를 찾은 찾기 — 찾기를 시작한 때(대기열 뒤)부터 찾은 때까지. */
        var found = 0
        var searchMillis = 0L
        var maxSearchMillis = 0L

        /** 읽은 청크 수(찾기·실패·취소 전부). */
        var chunkLoads = 0L
    }

    private val lines = LinkedHashMap<String, Line>()

    fun of(world: String): Line = lines.getOrPut(world) { Line() }

    fun all(): Map<String, Line> = lines

    fun found(world: String, millis: Long) {
        val line = of(world)
        line.found++
        line.searchMillis += millis
        line.maxSearchMillis = maxOf(line.maxSearchMillis, millis)
    }
}
