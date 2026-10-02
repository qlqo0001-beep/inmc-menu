package com.inmc.menu.scheduler

import com.inmc.menu.Hub
import kr.inmc.core.scheduler.TickerBase

/** 1초마다 한 번 깨어난다. 30초마다 — 메뉴 저장. 1분마다 — 출석(접속 분·자동 출석·날 바뀜). 5분마다 — 랜덤 TP 실패 구역(바뀐 것만). */
class Ticker(private val hub: Hub) : TickerBase(hub.plugin) {

    override val periodTicks: Long = 20L

    private var count = 0

    override fun ready(): Boolean = hub.ready

    override fun tick(now: Long) {
        count++
        if (count % SAVE_EVERY == 0) step("메뉴 저장") { hub.menus.flush() }
        if (count % ATTENDANCE_EVERY == 0) step("출석") { hub.attendance.tick() }
        if (count % MARKS_EVERY == 0) step("랜덤 TP 실패 구역 저장") { hub.rtp.saveMarks() }
    }

    private companion object {
        const val SAVE_EVERY = 30
        const val ATTENDANCE_EVERY = 60
        const val MARKS_EVERY = 300
    }
}
