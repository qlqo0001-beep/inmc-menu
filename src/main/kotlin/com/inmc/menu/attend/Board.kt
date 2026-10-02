package com.inmc.menu.attend

import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.time.LocalDate
import java.time.YearMonth

/** 출석판의 방식. */
enum class Mode(val label: String, val help: String) {
    CALENDAR("달력형", "이번 달 1~31일 칸 — 다음 달 새로"),
    STREAK("연속형", "연속 1~N일째 — 하루 빠지면 1일째로"),
    TOTAL("누적형", "총 1~N번째"),
}

/** 받는 법. */
enum class Claim(val label: String, val help: String) {
    AUTO("자동", "접속하면(정한 분만큼 접속한 뒤) 저절로"),
    MANUAL("수동", "출석 화면에서 오늘 칸을 눌러야"),
}

/** 연속형·누적형에서 N 을 넘으면. */
enum class AfterEnd(val label: String) { REPEAT("처음부터 반복"), KEEP_LAST("마지막 칸 유지") }

/** 칸 하나의 표시 상태. */
enum class CellState { DONE, TODAY, TODAY_DONE, MISSED, LATER, NONE }

data class RewardItem(val item: StoredItem, val amount: Int)

/** 칸 하나의 보상 — 아이템 + 명령어(콘솔, `{player}`) + 돈(기본 화폐). */
data class Reward(val items: List<RewardItem> = emptyList(), val commands: List<String> = emptyList(), val money: Long = 0) {

    fun isEmpty(): Boolean = items.isEmpty() && commands.isEmpty() && money <= 0

    fun save(section: ConfigurationSection) {
        for ((i, entry) in items.withIndex()) {
            val sub = section.createSection("items.$i")
            entry.item.save(sub)
            sub.set("amount", entry.amount)
        }
        if (commands.isNotEmpty()) section.set("commands", commands)
        if (money > 0) section.set("money", money)
    }

    companion object {
        fun load(section: ConfigurationSection?): Reward {
            if (section == null) return Reward()
            val items = section.getConfigurationSection("items")?.let { list ->
                list.getKeys(false).sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }.mapNotNull { key ->
                    val sub = list.getConfigurationSection(key) ?: return@mapNotNull null
                    StoredItem.load(sub)?.let { RewardItem(it, sub.getInt("amount", 1).coerceIn(1, MAX_AMOUNT)) }
                }
            }.orEmpty()
            return Reward(items, section.getStringList("commands"), section.getLong("money", 0).coerceAtLeast(0))
        }

        const val MAX_AMOUNT = 3456
    }
}

/** 한 사람의 출석판 하나 기록. */
data class Record(
    /** 마지막으로 출석한 "출석 날"(하루 경계 기준). */
    val last: LocalDate? = null,
    /** 연속형 — 마지막 출석까지 이어진 날 수. */
    val streak: Int = 0,
    /** 출석한 날 수(모든 방식). */
    val total: Int = 0,
    /** 달력형 — [days]·[bonuses] 가 가리키는 달. */
    val month: YearMonth? = null,
    val days: Set<Int> = emptySet(),
    val bonuses: Set<Int> = emptySet(),
    /** 이 기록이 속한 판의 초기화 회차([Board.round]) — 다르면 없는 기록으로 본다. */
    val round: Int = 0,
) {
    fun daysIn(month: YearMonth): Set<Int> = if (this.month == month) days else emptySet()

    fun bonusesIn(month: YearMonth): Set<Int> = if (this.month == month) bonuses else emptySet()

    /** 연속형 — 오늘 기준으로 아직 이어지는 연속(어제나 오늘 출석했으면). */
    fun liveStreak(today: LocalDate): Int = if (last == today || last == today.minusDays(1)) streak else 0

    fun save(section: ConfigurationSection) {
        last?.let { section.set("last", it.toString()) }
        if (streak > 0) section.set("streak", streak)
        if (total > 0) section.set("total", total)
        month?.let { section.set("month", it.toString()) }
        if (days.isNotEmpty()) section.set("days", days.sorted())
        if (bonuses.isNotEmpty()) section.set("bonuses", bonuses.sorted())
        if (round > 0) section.set("round", round)
    }

    companion object {
        fun load(section: ConfigurationSection?): Record {
            if (section == null) return Record()
            return Record(
                last = section.getString("last")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                streak = section.getInt("streak"),
                total = section.getInt("total"),
                month = section.getString("month")?.let { runCatching { YearMonth.parse(it) }.getOrNull() },
                days = section.getIntegerList("days").toSet(),
                bonuses = section.getIntegerList("bonuses").toSet(),
                round = section.getInt("round"),
            )
        }
    }
}

/** 출석 한 번의 결과 — 새 기록, 보상 칸, 표시할 수(이달 n일·연속 n일째·총 n번째), 새로 받는 이달 보너스. */
data class Attended(val record: Record, val cell: Int, val count: Int, val bonuses: List<Int>)

/**
 * 출석판(`attendance/<id>.yml`). 칸마다 보상, 달력형은 "이달 n일 출석" 보너스도.
 * 계산은 전부 순수 함수(`AttendanceTest`) — 날짜는 부르는 쪽이 하루 경계·시간대로 정해 넘긴다.
 */
data class Board(
    val id: String,
    val name: String = id,
    val icon: Material = Material.CLOCK,
    val enabled: Boolean = true,
    val mode: Mode = Mode.CALENDAR,
    val claim: Claim = Claim.AUTO,
    /** 연속형·누적형의 칸 수(1~45). 달력형은 31. */
    val length: Int = 7,
    val afterEnd: AfterEnd = AfterEnd.REPEAT,
    /** 자동 — 오늘 이만큼(분) 접속한 뒤. 0 = 들어오자마자. */
    val autoMinutes: Int = 0,
    val rewards: Map<Int, Reward> = emptyMap(),
    /** 달력형 — 이달 n일 출석하면. */
    val bonuses: Map<Int, Reward> = emptyMap(),
    /** 사용할 달 — 정하면 그 달에만 열린다(월별 판, 사용자 요청 2026-10-02). null = 늘. */
    val month: YearMonth? = null,
    /**
     * 초기화 회차 — 올리면 모든 사람의 이 판 기록이 처음부터다([Record.round] 가 다르면 없는 기록).
     * 사람마다의 파일을 훑지 않으니 접속 안 한 사람에게도 바로 맞고, 파일을 쓰다 꺼질 틈도 없다.
     */
    val round: Int = 0,
) {

    /** 지금 열려 있나 — 켜져 있고, 달을 정했으면 그 달. */
    fun openOn(today: LocalDate): Boolean = enabled && (month == null || month == YearMonth.from(today))

    /** 복사본의 기본값 — 보상·설정 그대로, 기록은 새로(회차 0). 달을 정한 판이면 다음 달로 옮기고 이름·id 의 달도 바꾼다. */
    fun copyDraft(): Board {
        val next = month?.plusMonths(1) ?: return copy(id = id + "_복사", round = 0)
        val shifted = shiftMonth(id, month, next)
        return copy(id = if (shifted != id) shifted else id + "_복사", name = shiftMonth(name, month, next), month = next, round = 0)
    }

    val cells: Int get() = if (mode == Mode.CALENDAR) 31 else length.coerceIn(1, MAX_LENGTH)

    /** 연속형·누적형 — n 번째 출석이 몇 번째 칸인가. */
    fun position(n: Int): Int = when {
        n <= 0 -> 0
        n <= cells -> n
        afterEnd == AfterEnd.REPEAT -> (n - 1) % cells + 1
        else -> cells
    }

    /** 오늘 출석하면. 이미 했으면 null. */
    fun attend(record: Record, today: LocalDate): Attended? {
        if (record.last == today) return null
        return when (mode) {
            Mode.CALENDAR -> {
                val month = YearMonth.from(today)
                val days = record.daysIn(month) + today.dayOfMonth
                val claimed = record.bonusesIn(month)
                val fresh = bonuses.keys.filter { it <= days.size && it !in claimed }.sorted()
                Attended(record.copy(last = today, total = record.total + 1, month = month, days = days, bonuses = claimed + fresh), today.dayOfMonth, days.size, fresh)
            }
            Mode.STREAK -> {
                val streak = if (record.last == today.minusDays(1)) record.streak + 1 else 1
                Attended(record.copy(last = today, streak = streak, total = record.total + 1), position(streak), streak, emptyList())
            }
            Mode.TOTAL -> {
                val total = record.total + 1
                Attended(record.copy(last = today, total = total), position(total), total, emptyList())
            }
        }
    }

    /** 칸 1..[cells] 의 상태(0번 = 1칸). */
    fun states(record: Record, today: LocalDate): List<CellState> {
        val attendedToday = record.last == today
        if (mode == Mode.CALENDAR) {
            val month = YearMonth.from(today)
            val days = record.daysIn(month)
            val length = month.lengthOfMonth()
            return (1..cells).map { d ->
                when {
                    d > length -> CellState.NONE
                    d == today.dayOfMonth -> if (d in days) CellState.TODAY_DONE else CellState.TODAY
                    d in days -> CellState.DONE
                    d < today.dayOfMonth -> CellState.MISSED
                    else -> CellState.LATER
                }
            }
        }
        val count = if (mode == Mode.STREAK) record.liveStreak(today) else record.total
        if (attendedToday) {
            val pos = position(count)
            // 반복으로 새 바퀴의 첫 칸이면 앞 칸들은 아직이다.
            return (1..cells).map { i -> when {
                i == pos -> CellState.TODAY_DONE
                i < pos -> CellState.DONE
                else -> CellState.LATER
            } }
        }
        val next = position(count + 1)
        val done = if (next == 1 && afterEnd == AfterEnd.REPEAT) 0 else position(count)
        return (1..cells).map { i -> when {
            i == next -> CellState.TODAY
            i <= done -> CellState.DONE
            else -> CellState.LATER
        } }
    }

    /** 오늘 출석하면 받을 칸(아직이면) — 화면이 "오늘" 칸을 고를 때. */
    fun todayCell(record: Record, today: LocalDate): Int = when (mode) {
        Mode.CALENDAR -> today.dayOfMonth
        Mode.STREAK -> position(if (record.last == today) record.streak else record.liveStreak(today) + 1)
        Mode.TOTAL -> position(if (record.last == today) record.total else record.total + 1)
    }

    fun save(): YamlConfiguration = YamlConfiguration().also { y ->
        y.set("name", name)
        y.set("icon", icon.name)
        y.set("enabled", enabled)
        y.set("mode", mode.name.lowercase())
        y.set("claim", claim.name.lowercase())
        y.set("length", length)
        y.set("after-end", afterEnd.name.lowercase().replace('_', '-'))
        y.set("auto-minutes", autoMinutes)
        month?.let { y.set("month", it.toString()) }
        if (round > 0) y.set("round", round)
        for ((cell, reward) in rewards.toSortedMap()) if (!reward.isEmpty()) reward.save(y.createSection("rewards.$cell"))
        for ((count, reward) in bonuses.toSortedMap()) reward.save(y.createSection("bonuses.$count"))
    }

    companion object {

        const val MAX_LENGTH = 45

        val DEFAULT_BONUSES = listOf(7, 14, 21, 28)

        fun load(id: String, y: YamlConfiguration): Board {
            fun <E : Enum<E>> enumOf(values: Array<E>, raw: String?, fallback: E) =
                values.firstOrNull { it.name.equals(raw?.replace('-', '_'), ignoreCase = true) } ?: fallback
            fun cells(path: String, range: IntRange): Map<Int, Reward> = y.getConfigurationSection(path)?.let { s ->
                s.getKeys(false).mapNotNull { key -> key.toIntOrNull()?.takeIf { it in range }?.let { it to Reward.load(s.getConfigurationSection(key)) } }.toMap()
            }.orEmpty()
            return Board(
                id = id,
                name = y.getString("name") ?: id,
                icon = y.getString("icon")?.let { Material.matchMaterial(it) } ?: Material.CLOCK,
                enabled = y.getBoolean("enabled", true),
                mode = enumOf(Mode.entries.toTypedArray(), y.getString("mode"), Mode.CALENDAR),
                claim = enumOf(Claim.entries.toTypedArray(), y.getString("claim"), Claim.AUTO),
                length = y.getInt("length", 7).coerceIn(1, MAX_LENGTH),
                afterEnd = enumOf(AfterEnd.entries.toTypedArray(), y.getString("after-end"), AfterEnd.REPEAT),
                autoMinutes = y.getInt("auto-minutes", 0).coerceIn(0, 1440),
                rewards = cells("rewards", 1..MAX_LENGTH),
                bonuses = cells("bonuses", 1..31),
                month = y.getString("month")?.let { runCatching { YearMonth.parse(it) }.getOrNull() },
                round = y.getInt("round", 0).coerceAtLeast(0),
            )
        }

        /** 글자 속 달을 옮긴다 — `2026-10` → `2026-11`, `10월` → `11월`(해가 넘어가면 `2026년` → `2027년`도). */
        fun shiftMonth(text: String, from: YearMonth, to: YearMonth): String {
            var out = text.replace(from.toString(), to.toString())
            if (from.year != to.year) out = out.replace(Regex("(?<!\\d)${from.year}년"), "${to.year}년")
            return out.replace(Regex("(?<!\\d)${from.monthValue}월"), "${to.monthValue}월")
        }

        /** 처음 깔 때 하나 — 매일 출석(달력형·자동), 보상은 비워 두고 이달 보너스 기준만. */
        fun starter(): Board = Board("daily", "<gold>매일 출석</gold>", Material.CLOCK, mode = Mode.CALENDAR, claim = Claim.AUTO,
            bonuses = DEFAULT_BONUSES.associateWith { Reward() })
    }
}
