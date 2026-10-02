package com.inmc.menu

import com.inmc.menu.attend.AfterEnd
import com.inmc.menu.attend.Board
import com.inmc.menu.attend.CellState
import com.inmc.menu.attend.Claim
import com.inmc.menu.attend.Days
import com.inmc.menu.attend.Mode
import com.inmc.menu.attend.PlayerData
import com.inmc.menu.attend.Record
import com.inmc.menu.attend.Reward
import com.inmc.menu.attend.RewardItem
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 출석 계산 — 하루 경계, 세 방식, 반복/유지, 칸 상태, 파일 왕복. */
class AttendanceTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private fun day(text: String) = LocalDate.parse(text)

    @Test
    fun `하루 경계 — 시간대와 바뀌는 시각`() {
        // 2026-10-01 15:30 UTC = 서울 10-02 00:30
        val now = Instant.parse("2026-10-01T15:30:00Z")
        assertEquals(day("2026-10-02"), Days.today(now, seoul, 0))
        assertEquals(day("2026-10-01"), Days.today(now, seoul, 6), "새벽 6시에 바뀌면 아직 전날")
        assertEquals(day("2026-10-01"), Days.today(now, ZoneId.of("UTC"), 0))
    }

    @Test
    fun `달력형 — 하루 한 번, 이달 보너스는 한 번씩, 다음 달 새로`() {
        val board = Board("d", mode = Mode.CALENDAR, bonuses = mapOf(2 to Reward(money = 10), 3 to Reward(money = 20)))
        var record = Record()
        val first = board.attend(record, day("2026-10-30"))!!
        assertEquals(30, first.cell)
        assertEquals(1, first.count)
        assertEquals(emptyList(), first.bonuses)
        record = first.record
        assertNull(board.attend(record, day("2026-10-30")), "같은 날 두 번은 없다")

        val second = board.attend(record, day("2026-10-31"))!!
        assertEquals(listOf(2), second.bonuses)
        record = second.record
        assertEquals(setOf(30, 31), record.days)

        // 11월 — 칸과 보너스가 새로.
        val nov = board.attend(record, day("2026-11-01"))!!
        assertEquals(YearMonth.of(2026, 11), nov.record.month)
        assertEquals(setOf(1), nov.record.days)
        assertEquals(emptySet(), nov.record.bonuses)
        assertEquals(3, nov.record.total)
        val nov2 = board.attend(nov.record, day("2026-11-05"))!!
        assertEquals(listOf(2), nov2.bonuses, "이달 두 번째 — 날이 붙어 있지 않아도")
    }

    @Test
    fun `연속형 — 하루 빠지면 1일째, 반복과 유지`() {
        val repeat = Board("s", mode = Mode.STREAK, length = 3, afterEnd = AfterEnd.REPEAT)
        var record = Record()
        val cells = ArrayList<Int>()
        var date = day("2026-10-01")
        repeat(5) {
            val r = repeat.attend(record, date)!!
            cells += r.cell
            record = r.record
            date = date.plusDays(1)
        }
        assertEquals(listOf(1, 2, 3, 1, 2), cells)
        assertEquals(5, record.streak)

        // 하루 건너뛰면 1일째부터.
        val broken = repeat.attend(record, date.plusDays(1))!!
        assertEquals(1, broken.cell)
        assertEquals(1, broken.record.streak)
        assertEquals(6, broken.record.total)

        val keep = Board("k", mode = Mode.STREAK, length = 3, afterEnd = AfterEnd.KEEP_LAST)
        assertEquals(listOf(1, 2, 3, 3, 3), (1..5).map(keep::position))
    }

    @Test
    fun `누적형 — 빠져도 이어진다`() {
        val board = Board("t", mode = Mode.TOTAL, length = 2)
        val a = board.attend(Record(), day("2026-10-01"))!!
        val b = board.attend(a.record, day("2026-10-09"))!!
        val c = board.attend(b.record, day("2026-12-25"))!!
        assertEquals(listOf(1, 2, 1), listOf(a.cell, b.cell, c.cell))
        assertEquals(3, c.count)
    }

    @Test
    fun `칸 상태 — 달력형`() {
        val board = Board("d", mode = Mode.CALENDAR)
        val today = day("2026-02-10")
        val record = Record(last = day("2026-02-09"), month = YearMonth.of(2026, 2), days = setOf(1, 9), total = 2)
        val states = board.states(record, today)
        assertEquals(31, states.size)
        assertEquals(CellState.DONE, states[0])
        assertEquals(CellState.MISSED, states[1])
        assertEquals(CellState.DONE, states[8])
        assertEquals(CellState.TODAY, states[9])
        assertEquals(CellState.LATER, states[10])
        assertEquals(CellState.NONE, states[28], "2월 29일은 없다(2026)")
        val after = board.attend(record, today)!!.record
        assertEquals(CellState.TODAY_DONE, board.states(after, today)[9])
        // 지난달 기록은 이번 달에 안 보인다.
        assertEquals(CellState.MISSED, board.states(record, day("2026-03-02"))[0])
    }

    @Test
    fun `칸 상태 — 연속형·누적형의 바퀴`() {
        val board = Board("t", mode = Mode.TOTAL, length = 3, afterEnd = AfterEnd.REPEAT)
        val today = day("2026-10-10")
        // 셋 다 채운 다음 날 — 새 바퀴의 첫 칸이 오늘, 나머지는 아직.
        assertEquals(listOf(CellState.TODAY, CellState.LATER, CellState.LATER), board.states(Record(last = day("2026-10-09"), total = 3), today))
        // 하나 했고 오늘 아직.
        assertEquals(listOf(CellState.DONE, CellState.TODAY, CellState.LATER), board.states(Record(last = day("2026-10-01"), total = 1), today))
        // 오늘 넷째(새 바퀴 첫 칸)를 했다.
        assertEquals(listOf(CellState.TODAY_DONE, CellState.LATER, CellState.LATER), board.states(Record(last = today, total = 4), today))
        // 유지 — 끝 칸이 계속 오늘.
        val keep = board.copy(afterEnd = AfterEnd.KEEP_LAST)
        assertEquals(listOf(CellState.DONE, CellState.DONE, CellState.TODAY), keep.states(Record(last = day("2026-10-09"), total = 5), today))
        // 연속형 — 이틀 전이 마지막이면 끊겼다.
        val streak = Board("s", mode = Mode.STREAK, length = 3)
        assertEquals(listOf(CellState.TODAY, CellState.LATER, CellState.LATER), streak.states(Record(last = day("2026-10-08"), streak = 2), today))
        assertEquals(3, streak.todayCell(Record(last = day("2026-10-09"), streak = 2), today))
    }

    @Test
    fun `출석판 파일 왕복`() {
        val diamond = StoredItem(ItemRef.Vanilla(Material.DIAMOND), Material.DIAMOND)
        val board = Board("week", "<gold>주간</gold>", Material.DIAMOND, enabled = false, mode = Mode.STREAK, claim = Claim.MANUAL, length = 7,
            afterEnd = AfterEnd.KEEP_LAST, autoMinutes = 15,
            rewards = mapOf(1 to Reward(listOf(RewardItem(diamond, 3)), listOf("say {player}"), 500), 7 to Reward(money = 1000)),
            bonuses = mapOf(7 to Reward(), 14 to Reward(commands = listOf("give {player} apple 1"))), month = YearMonth.of(2026, 11), round = 2)
        val back = Board.load("week", YamlConfiguration().apply { loadFromString(board.save().saveToString()) })
        assertEquals(board, back)

        val record = Record(day("2026-10-02"), 3, 10, YearMonth.of(2026, 10), setOf(1, 2), setOf(7), round = 2)
        val section = YamlConfiguration()
        record.save(section)
        assertEquals(record, Record.load(YamlConfiguration().apply { loadFromString(section.saveToString()) }))
        assertEquals(Board.load("x", YamlConfiguration()), Board("x"), "빈 파일은 기본값")
    }

    @Test
    fun `판 초기화 — 회차가 다른 기록은 없는 것, 다시 출석하면 새 회차로`() {
        val board = Board("d", mode = Mode.CALENDAR)
        val pd = PlayerData(UUID.randomUUID())
        val today = day("2026-10-02")
        pd.records["d"] = board.attend(pd.record(board), today)!!.record
        assertNull(board.attend(pd.record(board), today), "초기화 전 — 오늘은 이미")

        val reset = board.copy(round = 1)
        assertEquals(Record(round = 1), pd.record(reset), "옛 회차의 기록은 안 보인다")
        val again = reset.attend(pd.record(reset), today)!!
        assertEquals(1, again.record.round)
        assertEquals(setOf(2), again.record.days)
        assertEquals(1, again.record.total, "누적도 처음부터")
    }

    @Test
    fun `사용할 달 — 그 달에만 열린다`() {
        val nov = Board("nov", month = YearMonth.of(2026, 11))
        assertFalse(nov.openOn(day("2026-10-31")))
        assertTrue(nov.openOn(day("2026-11-01")))
        assertFalse(nov.openOn(day("2026-12-01")))
        assertFalse(nov.copy(enabled = false).openOn(day("2026-11-15")), "꺼진 판은 그 달이어도 닫힘")
        assertTrue(Board("always").openOn(day("2030-01-01")), "달을 안 정하면 늘")
    }

    @Test
    fun `복사 — 보상 그대로, 기록 회차는 새로, 달을 정했으면 다음 달`() {
        val rewards = mapOf(1 to Reward(money = 100))
        val oct = Board("2026-10", "<gold>10월 출석</gold>", rewards = rewards, month = YearMonth.of(2026, 10), round = 3)
        val draft = oct.copyDraft()
        assertEquals("2026-11", draft.id)
        assertEquals("<gold>11월 출석</gold>", draft.name)
        assertEquals(YearMonth.of(2026, 11), draft.month)
        assertEquals(rewards, draft.rewards)
        assertEquals(0, draft.round)

        assertEquals("10월출석_복사", Board("10월출석").copyDraft().id, "달이 없으면 이름 뒤에 _복사")
        assertEquals("출석_11월", Board("출석_10월", month = YearMonth.of(2026, 10)).copyDraft().id)
        // 해 넘김 · 110월 같은 숫자 속은 건드리지 않는다.
        val dec = YearMonth.of(2026, 12)
        assertEquals("2027년 1월 출석 2027-01", Board.shiftMonth("2026년 12월 출석 2026-12", dec, dec.plusMonths(1)))
        assertEquals("110월", Board.shiftMonth("110월", YearMonth.of(2026, 10), YearMonth.of(2026, 11)))
    }

    @Test
    fun `한 사람 초기화 — 파일에서 그 판만 지우고 못 받은 보상은 그대로`() {
        val dir = kotlin.io.path.createTempDirectory("attend").toFile()
        val file = java.io.File(dir, "p.yml")
        file.writeText("boards:\n  a:\n    total: 3\n  b:\n    total: 1\npending:\n- AAAA\n", Charsets.UTF_8)
        assertTrue(PlayerData.forget(file, "a"))
        val y = YamlConfiguration.loadConfiguration(file)
        assertFalse(y.contains("boards.a"))
        assertEquals(1, y.getInt("boards.b.total"))
        assertEquals(listOf("AAAA"), y.getStringList("pending"))
        assertFalse(PlayerData.forget(file, "a"), "두 번째는 지울 것이 없다")
        assertFalse(PlayerData.forget(java.io.File(dir, "none.yml"), "a"), "파일이 없으면 없다")
        dir.deleteRecursively()
    }
}
