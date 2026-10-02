package com.inmc.menu.attend

import com.inmc.menu.Hub
import com.inmc.menu.menu.ButtonDef
import com.inmc.menu.util.Ph
import kr.inmc.core.util.Text
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * 출석 — 출석판(`attendance/<id>.yml`)과 사람마다의 기록(`attendance-data/<uuid>.yml`, [PlayerData]).
 *
 * - **자동** 판: 접속하면(정한 분만큼 접속한 뒤) — 들어올 때 기록을 읽은 직후, 그리고 1분마다(날이 바뀐 것도 여기서 잡는다).
 * - **수동** 판: 출석 화면에서 오늘 칸을 눌러야. 들어올 때·날이 바뀔 때 "오늘 아직" 안내.
 * - 출석 순서: **기록을 먼저 쓰고**(그 자리에서) 보상을 준다 — 보상 뒤에 꺼지면 또 받는 것보다, 기록 뒤에 꺼져 못 받는 쪽이 낫다.
 *   가방이 차서 남은 아이템은 못 받은 보상에 넣고 한 번 더 쓴다.
 * - 하루 경계: `config.yml` 의 시간대·바뀌는 시각([Days]).
 */
class AttendanceService(private val hub: Hub) {

    @Volatile
    var boards: List<Board> = emptyList()
        private set

    private val folder get() = hub.io.file("attendance")
    private val dataFolder get() = hub.io.file("attendance-data")
    private val data = HashMap<UUID, PlayerData>()
    private var lastDay: LocalDate? = null

    // --- 출석판 -------------------------------------------------------------------------------

    /** 켤 때 그 자리에서. 폴더가 없으면(처음) 매일 출석판 하나. */
    fun loadBoards() {
        if (!folder.isDirectory) {
            folder.mkdirs()
            val starter = Board.starter()
            starter.save().save(java.io.File(folder, starter.id + ".yml"))
        }
        boards = folder.listFiles { f -> f.isFile && f.name.endsWith(".yml") }.orEmpty()
            .mapNotNull { file ->
                val id = file.name.removeSuffix(".yml")
                if (!ButtonDef.ID.matches(id)) return@mapNotNull null.also { hub.logger.warning("출석판 이름이 맞지 않아 건너뜁니다: ${file.name}") }
                runCatching { Board.load(id, YamlConfiguration.loadConfiguration(file)) }
                    .onFailure { hub.logger.warning("출석판을 읽지 못했습니다(${file.name}): ${it.message}") }.getOrNull()
            }.sortedBy { it.id }
    }

    fun board(id: String): Board? = boards.firstOrNull { it.id == id }

    /** 검증기 — 파일에 쓰지 않고 판 목록을 잠깐 바꿨다가 되돌린다. */
    fun swap(value: List<Board>) {
        boards = value
    }

    /** 지금 열린 판 — 켜져 있고, 달을 정했으면 이 달([Board.openOn]). */
    fun enabled(): List<Board> {
        val today = today()
        return boards.filter { it.openOn(today) }
    }

    fun put(board: Board) {
        boards = (boards.filter { it.id != board.id } + board).sortedBy { it.id }
        val yaml = board.save()
        val file = java.io.File(folder, board.id + ".yml")
        hub.io.asyncRun { hub.io.save(file, yaml) }
    }

    /** 판 전체 초기화 — 회차를 올린다. 모든 사람의 이 판 기록이 처음부터(못 받은 보상은 그대로). */
    fun resetBoard(board: Board): Board = board.copy(round = board.round + 1).also(::put)

    /**
     * 한 사람의 판 하나 기록을 지운다. 들어와 있으면 메모리에서 지우고 그 자리에서 쓴다. 아니면 워커가 파일에서 지우고, 그 사이 들어와
     * 옛 기록을 읽었으면 메모리에서도 지운다(워커는 한 줄이라 들어올 때 읽기와 순서가 섞이지 않는다). 지운 것이 있었으면 true.
     */
    fun resetPlayer(boardId: String, target: UUID, then: (Boolean) -> Unit) {
        data[target]?.let { return then(forgetLoaded(it, boardId)) }
        val file = file(target)
        hub.io.async({ PlayerData.forget(file, boardId) }) { removed ->
            val joined = data[target]?.let { forgetLoaded(it, boardId) } ?: false
            then(removed || joined)
        }
    }

    private fun forgetLoaded(pd: PlayerData, boardId: String): Boolean {
        if (pd.records.remove(boardId) == null) return false
        write(pd)
        return true
    }

    fun delete(id: String) {
        boards = boards.filter { it.id != id }
        val file = java.io.File(folder, "$id.yml")
        hub.io.asyncRun { file.delete() }
    }

    // --- 날 ---------------------------------------------------------------------------------

    fun today(): LocalDate = Days.today(Instant.now(), zone(), hub.config.attendance.resetHour)

    private fun zone(): ZoneId = runCatching { ZoneId.of(hub.config.attendance.timezone) }.getOrDefault(ZoneId.systemDefault())

    // --- 사람 ---------------------------------------------------------------------------------

    fun data(player: UUID): PlayerData? = data[player]

    private fun file(id: UUID) = java.io.File(dataFolder, "$id.yml")

    fun onJoin(player: Player) {
        val id = player.uniqueId
        hub.io.async({ PlayerData.read(id, file(id)) }) { loaded ->
            val online = Bukkit.getPlayer(id) ?: return@async
            data[id] = loaded
            autoCheck(online)
            remind(online)
        }
    }

    fun onQuit(player: Player) {
        data.remove(player.uniqueId)?.let { write(it) }
    }

    /** `/reload` 뒤 — 이미 들어와 있는 사람. */
    fun loadOnline() {
        for (player in Bukkit.getOnlinePlayers()) if (player.uniqueId !in data) onJoin(player)
    }

    /** 1분마다 — 접속 분을 세고, 자동 판을 보고, 날이 바뀌었으면 수동 판을 안내한다. */
    fun tick() {
        val today = today()
        val rolled = lastDay != null && lastDay != today
        lastDay = today
        for (player in Bukkit.getOnlinePlayers()) {
            val pd = data[player.uniqueId] ?: continue
            pd.addMinute(today)
            autoCheck(player)
            if (rolled) remind(player)
        }
    }

    private fun autoCheck(player: Player) {
        if (!player.hasPermission(Hub.ATTENDANCE)) return
        val pd = data[player.uniqueId] ?: return
        val today = today()
        for (board in enabled()) {
            if (board.claim != Claim.AUTO || pd.record(board).last == today) continue
            if (pd.minutes(today) >= board.autoMinutes) attend(player, board)
        }
    }

    private fun remind(player: Player) {
        if (!hub.config.attendance.remind || !player.hasPermission(Hub.ATTENDANCE)) return
        val pd = data[player.uniqueId] ?: return
        val today = today()
        for (board in enabled()) {
            if (board.claim == Claim.MANUAL && pd.record(board).last != today) hub.messages.send(player, "attend-remind", Ph.of().value(board.name))
        }
    }

    enum class Outcome { DONE, ALREADY, LOADING, WAIT, LOCKED, CLOSED }

    /**
     * 오늘 출석. 자동 판을 손으로 누르면 정한 분을 채웠을 때만. 결과를 알리는 것은 여기서(화면은 다시 그리기만).
     */
    fun attend(player: Player, board: Board): Outcome {
        if (!player.hasPermission(Hub.ATTENDANCE)) return Outcome.LOCKED.also { hub.messages.send(player, "no-permission") }
        val pd = data[player.uniqueId] ?: return Outcome.LOADING.also { hub.messages.send(player, "attend-loading") }
        val today = today()
        // 화면을 연 채 달이 바뀌었거나 꺼진 판(미리 보기) — 출석하지 않는다.
        if (!board.openOn(today)) return Outcome.CLOSED.also { hub.messages.send(player, "attend-closed", Ph.of().value(board.name)) }
        if (board.claim == Claim.AUTO && pd.minutes(today) < board.autoMinutes) {
            hub.messages.send(player, "attend-wait", Ph.of().count(board.autoMinutes - pd.minutes(today)))
            return Outcome.WAIT
        }
        val result = board.attend(pd.record(board), today) ?: return Outcome.ALREADY.also { hub.messages.send(player, "attend-already") }
        // 1. 기록 먼저 — 그 자리에서.
        pd.records[board.id] = result.record
        write(pd)
        // 2. 보상.
        val before = pd.pending.size
        give(player, pd, board.rewards[result.cell])
        for (count in result.bonuses) {
            val bonus = board.bonuses[count] ?: continue
            if (bonus.isEmpty()) continue
            give(player, pd, bonus)
            hub.messages.send(player, "attend-bonus", Ph.of().value(board.name).count(count))
        }
        val key = when (board.mode) {
            Mode.CALENDAR -> "attend-done-calendar"
            Mode.STREAK -> "attend-done-streak"
            Mode.TOTAL -> "attend-done-total"
        }
        hub.messages.send(player, key, Ph.of().value(board.name).count(result.count))
        // 3. 못 받은 것이 생겼으면 한 번 더.
        if (pd.pending.size > before) {
            write(pd)
            hub.messages.send(player, "attend-pending", Ph.of().count(pd.pending.size - before))
        }
        return Outcome.DONE
    }

    /** 못 받은 보상 받기 — 들어가는 만큼. */
    fun claimPending(player: Player) {
        val pd = data[player.uniqueId] ?: return hub.messages.send(player, "attend-loading")
        if (pd.pending.isEmpty()) return
        val waiting = pd.pending.toList()
        pd.pending.clear()
        // 비운 목록을 먼저 쓴다 — 주다가 꺼지면 또 받는 것보다 잃는 쪽(출석과 같은 원칙). 남은 것은 아래에서 다시 쓴다.
        write(pd)
        var given = 0
        for (stack in waiting) {
            val left = player.inventory.addItem(stack).values
            if (left.isEmpty()) given++ else pd.pending += left
        }
        write(pd)
        hub.messages.send(player, "attend-claimed", Ph.of().count(given))
        if (pd.pending.isNotEmpty()) hub.messages.send(player, "attend-claim-left", Ph.of().count(pd.pending.size))
    }

    private fun give(player: Player, pd: PlayerData, reward: Reward?) {
        if (reward == null || reward.isEmpty()) return
        for (entry in reward.items) {
            var left = entry.amount
            var built = 0
            while (left > 0 && built < MAX_STACKS) {
                val stack = hub.resolver.create(entry.item, left) ?: break
                if (stack.amount <= 0) break
                left -= stack.amount
                built++
                pd.pending += player.inventory.addItem(stack).values
            }
        }
        if (reward.money > 0) hub.economy.deposit(player, reward.money.toDouble())
        for (template in reward.commands) {
            val command = Text.substituteOnly(template, Ph.of().player(player.name), player).trim().removePrefix("/")
            if (command.isNotEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        }
    }

    private fun write(pd: PlayerData) {
        runCatching { pd.write(file(pd.id)) }.onFailure { hub.logger.severe("출석 기록을 쓰지 못했습니다(${pd.id}): ${it.message}") }
    }

    /** 끌 때 — 접속한 사람의 접속 분을 쓴다(기록은 이미 써 두었다). */
    fun shutdown() {
        for (pd in data.values) write(pd)
        data.clear()
    }

    /** 화면용 — 못 받은 보상. */
    fun pending(player: UUID): List<ItemStack> = data[player]?.pending.orEmpty()

    private companion object {
        const val MAX_STACKS = 64
    }
}

/** 하루 경계 — 시간대의 그 시각(0~23시)에 날이 바뀐다. 순수(`AttendanceTest`). */
object Days {
    fun today(now: Instant, zone: ZoneId, resetHour: Int): LocalDate =
        now.atZone(zone).minusHours(resetHour.coerceIn(0, 23).toLong()).toLocalDate()
}
