package com.inmc.menu.rtp

/**
 * **서버 전체 동시 찾기 수 상한** + 짧은 대기열. 한꺼번에 몰려도 청크 생성이 겹겹이 쌓이지 않게 — 넘치면 줄을 서고 앞 사람이 끝나면 들어간다.
 * 메인 스레드에서만 만진다. 순수(`RtpTest`).
 */
class Slots<T>(var limit: Int, var queueLimit: Int) {

    private val running = LinkedHashSet<T>()
    private val waiting = ArrayDeque<T>()

    sealed interface Offer {
        data object Run : Offer
        data class Queued(val position: Int) : Offer
        data object Full : Offer
    }

    fun offer(item: T): Offer = when {
        item in running -> Offer.Run
        item in waiting -> Offer.Queued(waiting.indexOf(item) + 1)
        running.size < limit.coerceAtLeast(1) -> {
            running += item
            Offer.Run
        }
        waiting.size < queueLimit -> {
            waiting.addLast(item)
            Offer.Queued(waiting.size)
        }
        else -> Offer.Full
    }

    /** 끝났거나 취소됐다 — 자리를 비우고, 빈자리에 들어갈 대기열 맨 앞(들)을 돌려준다. */
    fun release(item: T): List<T> {
        if (!waiting.remove(item) && !running.remove(item)) return emptyList()
        val promoted = ArrayList<T>()
        while (running.size < limit.coerceAtLeast(1) && waiting.isNotEmpty()) {
            val next = waiting.removeFirst()
            running += next
            promoted += next
        }
        return promoted
    }

    /** 대기열에서 몇 번째(1부터). 찾는 중이면 0, 없으면 -1. */
    fun position(item: T): Int = when (item) {
        in running -> 0
        in waiting -> waiting.indexOf(item) + 1
        else -> -1
    }

    val runningCount: Int get() = running.size
    val waitingCount: Int get() = waiting.size
}
