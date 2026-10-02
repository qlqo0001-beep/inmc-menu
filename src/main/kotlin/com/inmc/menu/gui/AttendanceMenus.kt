package com.inmc.menu.gui

import com.inmc.menu.Hub
import com.inmc.menu.attend.AfterEnd
import com.inmc.menu.attend.Board
import com.inmc.menu.attend.CellState
import com.inmc.menu.attend.Claim
import com.inmc.menu.attend.Mode
import com.inmc.menu.attend.Record
import com.inmc.menu.attend.Reward
import com.inmc.menu.attend.RewardItem
import com.inmc.menu.menu.Actions
import com.inmc.menu.menu.ButtonDef
import com.inmc.menu.pack.MenuArt
import com.inmc.menu.util.Ph
import kr.inmc.core.gui.ConfirmMenu
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import java.time.YearMonth

/** 출석 화면 열기 — 판이 하나면 바로 그 판, 여럿이면 고르기. */
object AttendanceScreens {

    fun open(hub: Hub, player: Player, back: (() -> Unit)? = { Actions.openMain(hub, player) }) {
        val boards = hub.attendance.enabled()
        when (boards.size) {
            0 -> hub.messages.send(player, "attend-none")
            1 -> AttendanceMenu(hub, player, boards.single().id, back).show()
            else -> AttendanceListMenu(hub, player, back).show()
        }
    }

    /** 칸 이름 — 달력형 n일 · 연속형 n일째 · 누적형 n번째. */
    fun cellName(board: Board, n: Int): String = when (board.mode) {
        Mode.CALENDAR -> "${n}일"
        Mode.STREAK -> "${n}일째"
        Mode.TOTAL -> "${n}번째"
    }

    /** 보상 설명 줄. */
    fun rewardLines(hub: Hub, reward: Reward?): List<String> {
        if (reward == null || reward.isEmpty()) return listOf("<dark_gray>보상 없음</dark_gray>")
        return buildList {
            for (entry in reward.items) add("<gray>· <white>${entry.item.label()}</white> x${entry.amount}</gray>")
            if (reward.money > 0) add("<gray>· <gold>${hub.economy.format(reward.money.toDouble())}</gold></gray>")
            if (reward.commands.isNotEmpty()) add("<gray>· 특별 보상 ${reward.commands.size}개</gray>")
        }
    }

    /** 보상의 대표 그림 — 첫 아이템, 돈만이면 금괴, 명령어만이면 종이. */
    fun rewardIcon(hub: Hub, reward: Reward?): ItemStack? {
        if (reward == null || reward.isEmpty()) return null
        reward.items.firstOrNull()?.let { return hub.resolver.icon(it.item).stack.clone() }
        return ItemStack(if (reward.money > 0) Material.GOLD_INGOT else Material.PAPER)
    }
}

/** `2026년 11월`. */
internal fun monthLabel(month: YearMonth): String = "${month.year}년 ${month.monthValue}월"

/** 입력창의 달(`2026-11`) — 비우면 늘(null), 틀리면 실패. */
internal fun parseMonth(raw: String): Result<YearMonth?> =
    if (raw.isBlank()) Result.success(null) else runCatching { YearMonth.parse(raw.trim()) }

/** 출석판 고르기(열린 판이 둘 이상일 때). */
class AttendanceListMenu(
    hub: Hub,
    viewer: Player,
    override val back: (() -> Unit)?,
    private val grid: Char? = hub.art.gridFor(viewer, SIZE / 9),
) : Menu(hub, viewer, SIZE, MenuArt.titled(grid, "출석", viewer)) {

    override fun draw() {
        clear()
        val boards = hub.attendance.enabled().take(9)
        val today = hub.attendance.today()
        val data = hub.attendance.data(viewer.uniqueId)
        for ((slot, board) in RtpMenu.slots(boards.size).zip(boards)) {
            val done = data?.record(board)?.last == today
            val lore = listOf(
                "<gray>${board.mode.label} · ${board.claim.label}</gray>",
                if (done) "<green>오늘 출석함 ✔</green>" else "<yellow>오늘 아직</yellow>",
                "", "<yellow>▶ 클릭: 열기</yellow>",
            )
            val stack = named(runCatching { ItemStack(board.icon) }.getOrElse { ItemStack(Material.CLOCK) }, board.name, lore, null, viewer)
            if (!done) stack.editMeta { it.setEnchantmentGlintOverride(true) }
            set(slot, stack) { AttendanceMenu(hub, viewer, board.id, back = { AttendanceListMenu(hub, viewer, back).show() }).show() }
        }
        if (grid == null) fillEmpty(Icon.FILLER)
        navigation(RtpMenu.SLOT_BACK, RtpMenu.SLOT_CLOSE)
    }

    companion object {
        const val SIZE = 27
    }
}

/**
 * 출석판 하나 — 칸마다 보상과 상태(받음·오늘·놓침·아직). **오늘 칸을 누르면 출석**(수동 판, 자동 판은 정한 분을 채웠으면).
 * 달력형은 아래 줄에 "이달 n일 출석" 보너스. 가방이 차서 못 받은 보상은 가운데 아래에서 받는다.
 */
class AttendanceMenu(
    hub: Hub,
    viewer: Player,
    private val boardId: String,
    override val back: (() -> Unit)?,
    private val grid: Char? = hub.art.gridFor(viewer, SIZE / 9),
) : Menu(hub, viewer, SIZE, MenuArt.titled(grid, hub.attendance.board(boardId)?.name ?: boardId, viewer)) {

    override fun draw() {
        clear()
        val board = hub.attendance.board(boardId)
        if (board == null) {
            set(22, Icon.of(Material.BARRIER, "<red>출석판이 없습니다.</red>"))
            navigation()
            return
        }
        val data = hub.attendance.data(viewer.uniqueId)
        val record = data?.record(board) ?: Record()
        val today = hub.attendance.today()
        for ((index, state) in board.states(record, today).withIndex()) {
            if (state == CellState.NONE || index >= CELLS) continue
            val n = index + 1
            set(index, cell(board, n, state, data?.minutes(today) ?: 0)) {
                if (state != CellState.TODAY) return@set
                hub.attendance.attend(viewer, board)
                refresh()
            }
        }
        if (board.mode == Mode.CALENDAR) {
            val month = YearMonth.from(today)
            val count = record.daysIn(month).size
            for ((slot, threshold) in (BONUS_ROW until BONUS_ROW + 9).zip(board.bonuses.keys.sorted())) {
                val claimed = threshold in record.bonusesIn(month)
                val reward = board.bonuses[threshold]
                val lore = AttendanceScreens.rewardLines(hub, reward) + "" +
                    (if (claimed) "<green>받음 ✔</green>" else "<gray>이달 <white>$count</white>/${threshold}일</gray>")
                val stack = named(AttendanceScreens.rewardIcon(hub, reward) ?: ItemStack(Material.CHEST), "<gold>이달 ${threshold}일 출석</gold>", lore, null, viewer)
                if (claimed) stack.editMeta { it.setEnchantmentGlintOverride(true) }
                set(slot, stack)
            }
        }
        set(SLOT_INFO, info(board, record, today))
        val pending = hub.attendance.pending(viewer.uniqueId)
        if (pending.isNotEmpty()) {
            set(SLOT_PENDING, Icon.of(Material.CHEST_MINECART, "<yellow>못 받은 보상 ${pending.size}개</yellow>",
                "<gray>가방이 가득 차서 보관해 둔 것.</gray>", "", "<yellow>▶ 클릭: 받기</yellow>")) {
                hub.attendance.claimPending(viewer)
                refresh()
            }
        }
        if (grid == null) fillEmpty(Icon.FILLER)
        navigation()
    }

    private fun cell(board: Board, n: Int, state: CellState, minutes: Int): ItemStack {
        val label = AttendanceScreens.cellName(board, n)
        val reward = board.rewards[n]
        val (stack, name) = when (state) {
            CellState.DONE -> ItemStack(Material.LIME_STAINED_GLASS_PANE) to "<green>$label ✔</green>"
            CellState.TODAY_DONE -> ItemStack(Material.LIME_STAINED_GLASS_PANE) to "<green>$label — 오늘 출석함 ✔</green>"
            CellState.MISSED -> ItemStack(Material.GRAY_STAINED_GLASS_PANE) to "<dark_gray>$label — 놓침</dark_gray>"
            CellState.TODAY -> (AttendanceScreens.rewardIcon(hub, reward) ?: ItemStack(Material.CHEST)) to "<yellow><bold>$label — 오늘</bold></yellow>"
            else -> (AttendanceScreens.rewardIcon(hub, reward) ?: ItemStack(Material.WHITE_STAINED_GLASS_PANE)) to "<white>$label</white>"
        }
        val lore = AttendanceScreens.rewardLines(hub, reward).toMutableList()
        if (state == CellState.TODAY) {
            lore += ""
            lore += when {
                board.claim == Claim.MANUAL -> "<yellow>▶ 클릭: 출석</yellow>"
                minutes >= board.autoMinutes -> "<yellow>▶ 곧 자동으로 출석됩니다</yellow>"
                else -> "<gray>${board.autoMinutes - minutes}분 더 접속하면 자동으로 출석됩니다</gray>"
            }
        }
        val out = named(stack, name, lore, null, viewer)
        out.amount = n.coerceIn(1, out.maxStackSize.coerceAtLeast(1))
        if (state == CellState.TODAY) out.editMeta { it.setEnchantmentGlintOverride(true) }
        return out
    }

    private fun info(board: Board, record: Record, today: java.time.LocalDate): ItemStack {
        val progress = when (board.mode) {
            Mode.CALENDAR -> "<gray>이달 출석: <white>${record.daysIn(YearMonth.from(today)).size}일</white></gray>"
            Mode.STREAK -> "<gray>연속: <white>${record.liveStreak(today)}일</white></gray>"
            Mode.TOTAL -> "<gray>총 출석: <white>${record.total}번</white></gray>"
        }
        val claim = if (board.claim == Claim.AUTO && board.autoMinutes > 0) "${board.claim.label} — 접속 ${board.autoMinutes}분 뒤" else "${board.claim.label} — ${board.claim.help}"
        return named(ItemStack(Material.BOOK), board.name, listOf(
            "<gray>방식: <white>${board.mode.label}</white> <dark_gray>${board.mode.help}</dark_gray></gray>",
            "<gray>받는 법: <white>$claim</white></gray>",
            progress,
        ), null, viewer)
    }

    companion object {
        const val SIZE = 54
        const val CELLS = 45
        const val BONUS_ROW = 36
        const val SLOT_INFO = 48
        const val SLOT_PENDING = 49
    }
}

// --- 관리 ---------------------------------------------------------------------------------------

/** `/출석 관리` — 출석판 목록·새 판·지우기·하루 경계. */
class AttendanceAdminMenu(hub: Hub, viewer: Player) : Menu(hub, viewer, SIZE, "출석 관리") {

    override fun draw() {
        clear()
        val today = hub.attendance.today()
        for ((slot, board) in hub.attendance.boards.take(Paging.PER_PAGE).withIndex()) {
            val state = when {
                !board.enabled -> "<red>꺼짐</red>"
                board.openOn(today) -> "<green>켜짐 — 지금 열림</green>"
                else -> "<yellow>켜짐 — 지금은 그 달이 아님</yellow>"
            }
            val stack = named(runCatching { ItemStack(board.icon) }.getOrElse { ItemStack(Material.CLOCK) }, board.name, listOfNotNull(
                "<dark_gray>${board.id}</dark_gray>",
                state,
                board.month?.let { "<gray>사용할 달: <white>${monthLabel(it)}</white></gray>" },
                "<gray>${board.mode.label} · ${board.claim.label} · 칸 ${board.cells}개</gray>",
                "<gray>보상 넣은 칸 <white>${board.rewards.count { !it.value.isEmpty() }}</white>개</gray>",
                "", "<yellow>▶ 클릭: 고치기</yellow>", "<aqua>▶ 우클릭: 복사</aqua>", "<red>▶ Shift+우클릭: 지우기</red>",
            ), null, viewer)
            set(slot, stack) { event ->
                when {
                    event.isShiftClick && event.isRightClick -> delete(board)
                    event.isRightClick -> copy(board)
                    else -> BoardEditor(hub, viewer, board.id).show()
                }
            }
        }
        set(SLOT_NEW, Icon.of(Material.LIME_DYE, "<green>새 출석판</green>")) { create() }
        val c = hub.config.attendance
        set(SLOT_DAY, Icon.of(Material.CLOCK, "<aqua>하루 경계</aqua>",
            "<gray>시간대: <white>${c.timezone}</white></gray>", "<gray>날이 바뀌는 시각: <white>${c.resetHour}시</white></gray>",
            "<gray>수동 판 안내: </gray>" + Icon.toggle(c.remind), "", "<yellow>▶ 클릭: 고치기</yellow>")) { editDay() }
        fillEmpty(Icon.FILLER)
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun create() {
        ask(DialogForm("<green>새 출석판</green>")
            .text("id", "이름(소문자 영문·숫자·한글·_-)", "")
            .text("name", "보이는 이름(MiniMessage)", "<gold>출석</gold>")
            .choice("mode", "방식", Mode.entries.map { it.name to "${it.label} — ${it.help}" }, Mode.CALENDAR.name)
            .choice("claim", "받는 법", Claim.entries.map { it.name to "${it.label} — ${it.help}" }, Claim.AUTO.name)) { v ->
            val id = v.text("id").trim().lowercase()
            if (!ButtonDef.ID.matches(id)) return@ask hub.messages.send(viewer, "invalid-id", Ph.of().value(id))
            if (hub.attendance.board(id) != null) return@ask hub.messages.send(viewer, "already-exists", Ph.of().value(id))
            val mode = Mode.entries.firstOrNull { it.name == v.choice("mode") } ?: Mode.CALENDAR
            hub.attendance.put(Board(id, v.text("name").ifBlank { id }, mode = mode,
                claim = Claim.entries.firstOrNull { it.name == v.choice("claim") } ?: Claim.AUTO,
                bonuses = if (mode == Mode.CALENDAR) Board.DEFAULT_BONUSES.associateWith { Reward() } else emptyMap()))
            hub.messages.send(viewer, "attend-board-created", Ph.of().value(id))
            BoardEditor(hub, viewer, id).show()
        }
    }

    /** 보상·설정을 그대로 새 판으로(기록은 새로). 달을 정한 판이면 다음 달·이름의 달까지 바꿔 둔 채로 묻는다. */
    private fun copy(board: Board) {
        val draft = board.copyDraft()
        ask(DialogForm("<aqua>출석판 복사: ${board.id}</aqua>")
            .line("<gray>보상·설정을 그대로 옮깁니다. 사람들의 기록은 옮기지 않습니다.</gray>")
            .text("id", "새 이름(소문자 영문·숫자·한글·_-)", draft.id)
            .text("name", "보이는 이름(MiniMessage)", draft.name)
            .text("month", "사용할 달(예: 2026-11, 비우면 늘)", draft.month?.toString().orEmpty())) { v ->
            val id = v.text("id").trim().lowercase()
            if (!ButtonDef.ID.matches(id)) return@ask hub.messages.send(viewer, "invalid-id", Ph.of().value(id))
            if (hub.attendance.board(id) != null) return@ask hub.messages.send(viewer, "already-exists", Ph.of().value(id))
            val month = parseMonth(v.text("month")).getOrElse { return@ask hub.messages.send(viewer, "attend-month-invalid", Ph.of().value(v.text("month"))) }
            hub.attendance.put(draft.copy(id = id, name = v.text("name").ifBlank { id }, month = month))
            hub.messages.send(viewer, "attend-board-copied", Ph.of().value(id))
            BoardEditor(hub, viewer, id).show()
        }
    }

    private fun delete(board: Board) {
        ConfirmMenu(hub, "<red>'${board.id}' 출석판을 지울까요?</red>", listOf("<gray>보상 설정이 사라집니다. 사람들의 기록은 남습니다.</gray>"),
            onConfirm = {
                hub.attendance.delete(board.id)
                hub.messages.send(viewer, "attend-board-deleted", Ph.of().value(board.id))
                AttendanceAdminMenu(hub, viewer).show()
            },
            onCancel = { AttendanceAdminMenu(hub, viewer).show() }).open(viewer)
    }

    private fun editDay() {
        val c = hub.config.attendance
        ask(DialogForm("<aqua>출석 하루 경계</aqua>")
            .text("zone", "시간대(예: Asia/Seoul)", c.timezone)
            .long("hour", "날이 바뀌는 시각(0~23시)", c.resetHour.toLong(), 0, 23)
            .toggle("remind", "수동 판을 안 눌렀으면 알리기", c.remind)) { v ->
            val zone = v.text("zone").trim().takeIf { runCatching { java.time.ZoneId.of(it) }.isSuccess } ?: c.timezone
            hub.updateConfig { it.copy(attendance = it.attendance.copy(timezone = zone, resetHour = (v.long("hour") ?: 0).toInt().coerceIn(0, 23), remind = v.bool("remind"))) }
        }
    }

    companion object {
        const val SIZE = 54
        const val SLOT_NEW = 45
        const val SLOT_DAY = 49
    }
}

/** 출석판 하나 고치기. */
class BoardEditor(hub: Hub, viewer: Player, private val boardId: String) : Menu(hub, viewer, SIZE, "출석판: $boardId") {

    override val back: (() -> Unit) = { AttendanceAdminMenu(hub, viewer).show() }

    private fun board(): Board? = hub.attendance.board(boardId)

    private fun save(change: (Board) -> Board) {
        board()?.let { hub.attendance.put(change(it)) }
        refresh()
    }

    override fun draw() {
        clear()
        val b = board() ?: return navigation()
        set(10, Icon.of(Material.NAME_TAG, "<yellow>이름</yellow>", "<gray>지금: </gray>${b.name}", "", "<yellow>▶ 클릭: 바꾸기</yellow>")) {
            ask(DialogForm("<yellow>출석판 이름</yellow>").text("name", "보이는 이름(MiniMessage)", b.name)) { v -> save { it.copy(name = v.text("name").ifBlank { it.id }) } }
        }
        set(11, named(runCatching { ItemStack(b.icon) }.getOrElse { ItemStack(Material.CLOCK) }, "<yellow>그림</yellow>",
            listOf("<gray>고르기 화면의 그림.</gray>", "", "<yellow>▶ 클릭: 손에 든 것으로</yellow>"), null, viewer)) {
            val hand = viewer.inventory.itemInMainHand
            if (hand.type.isAir) return@set hub.messages.send(viewer, "hand-empty")
            save { it.copy(icon = hand.type) }
        }
        set(12, toggleIcon("켜짐", b.enabled, "<gray>끄면 화면에도 안 나오고 출석도 안 됩니다.</gray>")) { save { it.copy(enabled = !it.enabled) } }
        set(13, valueIcon(Material.COMPASS, "방식", b.mode.label, "<gray>${b.mode.help}</gray>")) {
            save { it.copy(mode = Mode.entries[(it.mode.ordinal + 1) % Mode.entries.size]) }
        }
        set(14, valueIcon(Material.LEVER, "받는 법", b.claim.label, "<gray>${b.claim.help}</gray>")) {
            save { it.copy(claim = Claim.entries[(it.claim.ordinal + 1) % Claim.entries.size]) }
        }
        if (b.mode != Mode.CALENDAR) {
            set(15, valueIcon(Material.LADDER, "칸 수", "${b.length}칸", "<gray>1~${Board.MAX_LENGTH}</gray>")) {
                ask(DialogForm("<yellow>칸 수</yellow>").long("length", "칸 수(1~${Board.MAX_LENGTH})", b.length.toLong(), 1, Board.MAX_LENGTH.toLong())) { v ->
                    save { it.copy(length = (v.long("length") ?: it.length.toLong()).toInt().coerceIn(1, Board.MAX_LENGTH)) }
                }
            }
            set(16, valueIcon(Material.REPEATER, "끝난 뒤", b.afterEnd.label, "<gray>${b.length}칸을 다 채운 다음 날.</gray>")) {
                save { it.copy(afterEnd = AfterEnd.entries[(it.afterEnd.ordinal + 1) % AfterEnd.entries.size]) }
            }
        } else {
            set(15, valueIcon(Material.GOLD_INGOT, "이달 출석 보너스 기준", b.bonuses.keys.sorted().joinToString(", ") { "${it}일" }.ifEmpty { "없음" },
                "<gray>이달 그만큼 출석하면 따로 받는 보상.</gray>")) {
                ask(DialogForm("<yellow>이달 출석 보너스 기준</yellow>").text("days", "일 수(쉼표로, 1~31)", b.bonuses.keys.sorted().joinToString(", "))) { v ->
                    val days = v.text("days").split(',', ' ').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..31 }.distinct().take(9)
                    save { it.copy(bonuses = days.associateWith { d -> it.bonuses[d] ?: Reward() }) }
                }
            }
        }
        if (b.claim == Claim.AUTO) {
            set(19, valueIcon(Material.CLOCK, "자동 출석 대기", if (b.autoMinutes > 0) "오늘 ${b.autoMinutes}분 접속한 뒤" else "들어오자마자")) {
                ask(DialogForm("<yellow>자동 출석 대기</yellow>").long("minutes", "오늘 접속한 분(0 = 들어오자마자)", b.autoMinutes.toLong(), 0, 1440)) { v ->
                    save { it.copy(autoMinutes = (v.long("minutes") ?: 0).toInt().coerceIn(0, 1440)) }
                }
            }
        }
        set(20, valueIcon(Material.FILLED_MAP, "사용할 달", b.month?.let { monthLabel(it) + "만" } ?: "늘 (정하지 않음)",
            "<gray>정하면 그 달에만 화면에 나오고 출석됩니다.</gray>", "<gray>다음 달 판을 미리 만들어 두면 1일에 저절로 바뀝니다.</gray>")) {
            ask(DialogForm("<yellow>사용할 달</yellow>").text("month", "달(예: 2026-11, 비우면 늘)", b.month?.toString().orEmpty())) { v ->
                val month = parseMonth(v.text("month")).getOrElse { return@ask hub.messages.send(viewer, "attend-month-invalid", Ph.of().value(v.text("month"))) }
                save { it.copy(month = month) }
            }
        }
        set(31, Icon.of(Material.TNT, "<red>초기화</red>",
            "<gray>모든 사람의 이 판 기록을 처음부터 — 접속하지 않은 사람도.</gray>",
            "<gray>오늘 이미 출석한 사람도 다시 출석해 보상을 받습니다.</gray>",
            "<gray>보관 중인 못 받은 보상은 그대로입니다.</gray>",
            "<dark_gray>한 사람만: /출석 초기화 <플레이어> <판></dark_gray>",
            "", "<red>▶ 클릭: 초기화</red>")) { reset(b) }
        set(22, Icon.of(Material.CHEST, "<gold>보상 고치기</gold>", "<gray>칸마다 아이템·돈·명령어.</gray>", "", "<yellow>▶ 클릭</yellow>")) {
            RewardGridMenu(hub, viewer, boardId).show()
        }
        set(24, Icon.of(Material.SPYGLASS, "<aqua>미리 보기</aqua>", "<gray>플레이어가 보는 화면(내 기록으로).</gray>")) {
            AttendanceMenu(hub, viewer, boardId, back = { BoardEditor(hub, viewer, boardId).show() }).show()
        }
        fillEmpty(Icon.FILLER)
        navigation()
    }

    private fun reset(board: Board) {
        ConfirmMenu(hub, "<red>'${board.id}' 출석판을 초기화할까요?</red>", listOf("<gray>모든 사람의 이 판 기록이 처음부터입니다. 되돌릴 수 없습니다.</gray>"),
            onConfirm = {
                hub.attendance.board(board.id)?.let { hub.attendance.resetBoard(it) }
                hub.messages.send(viewer, "attend-board-reset", Ph.of().value(board.id))
                BoardEditor(hub, viewer, board.id).show()
            },
            onCancel = { BoardEditor(hub, viewer, board.id).show() }).open(viewer)
    }

    companion object {
        const val SIZE = 54
    }
}

/** 칸마다의 보상 목록 — 눌러서 그 칸의 보상을 고친다. 달력형은 아래 줄이 이달 보너스. */
class RewardGridMenu(hub: Hub, viewer: Player, private val boardId: String) : Menu(hub, viewer, SIZE, "보상: $boardId") {

    override val back: (() -> Unit) = { BoardEditor(hub, viewer, boardId).show() }

    override fun draw() {
        clear()
        val b = hub.attendance.board(boardId) ?: return navigation()
        for (n in 1..b.cells.coerceAtMost(AttendanceMenu.CELLS)) {
            set(n - 1, icon(AttendanceScreens.cellName(b, n), b.rewards[n], n)) { RewardEditor(hub, viewer, boardId, n, bonus = false).show() }
        }
        if (b.mode == Mode.CALENDAR) {
            for ((slot, threshold) in (AttendanceMenu.BONUS_ROW until AttendanceMenu.BONUS_ROW + 9).zip(b.bonuses.keys.sorted())) {
                set(slot, icon("<gold>이달 ${threshold}일 출석 보너스</gold>", b.bonuses[threshold], threshold)) {
                    RewardEditor(hub, viewer, boardId, threshold, bonus = true).show()
                }
            }
        }
        fillEmpty(Icon.FILLER)
        navigation()
    }

    private fun icon(label: String, reward: Reward?, n: Int): ItemStack {
        val stack = AttendanceScreens.rewardIcon(hub, reward) ?: ItemStack(Material.WHITE_STAINED_GLASS_PANE)
        val out = named(stack, "<white>$label</white>", AttendanceScreens.rewardLines(hub, reward) + listOf("", "<yellow>▶ 클릭: 고치기</yellow>"), null, viewer)
        out.amount = n.coerceIn(1, out.maxStackSize.coerceAtLeast(1))
        return out
    }

    companion object {
        const val SIZE = 54
    }
}

/**
 * 칸 하나의 보상 — **위 네 줄에 아이템을 올려 두면 그것이 보상**(수량 그대로). 닫거나 나갈 때 저장한다. 명령어·돈은 아래 단추.
 * 올린 아이템은 돌려주지 않는다(보상으로 들어간다) — 견본을 올리세요.
 */
class RewardEditor(
    hub: Hub,
    viewer: Player,
    private val boardId: String,
    private val cell: Int,
    private val bonus: Boolean,
) : Menu(hub, viewer, SIZE, "보상 고치기: ${if (bonus) "이달 ${cell}일 보너스" else "${cell}칸"}") {

    override val back: (() -> Unit) = { RewardGridMenu(hub, viewer, boardId).show() }

    private fun reward(): Reward {
        val b = hub.attendance.board(boardId) ?: return Reward()
        return (if (bonus) b.bonuses[cell] else b.rewards[cell]) ?: Reward()
    }

    private fun store(reward: Reward) {
        val b = hub.attendance.board(boardId) ?: return
        hub.attendance.put(if (bonus) b.copy(bonuses = b.bonuses + (cell to reward)) else b.copy(rewards = b.rewards + (cell to reward)))
    }

    /** 지금 칸에 올라 있는 아이템을 보상의 아이템으로. */
    private fun persist() {
        val items = (0 until INPUT_END).mapNotNull { slot ->
            inventory.getItem(slot)?.takeIf { !it.type.isAir }?.let { RewardItem(hub.resolver.capture(it), it.amount) }
        }
        store(reward().copy(items = items))
    }

    override fun draw() {
        clear()
        val reward = reward()
        var slot = 0
        for (entry in reward.items) {
            var left = entry.amount
            while (left > 0 && slot < INPUT_END) {
                val stack = hub.resolver.create(entry.item, left) ?: break
                if (stack.amount <= 0) break
                inventory.setItem(slot++, stack)
                left -= stack.amount
            }
        }
        for (s in INPUT_END until SIZE) set(s, Icon.EDGE)
        set(SLOT_COMMANDS, Icon.of(Material.COMMAND_BLOCK, "<yellow>명령어</yellow>",
            (if (reward.commands.isEmpty()) listOf("<dark_gray>(없음)</dark_gray>") else reward.commands.map { "<gray>/ $it</gray>" }) +
                listOf("", "<gray>콘솔이 돌린다 · {player} = 받는 사람</gray>", "<yellow>▶ 클릭: 고치기</yellow>"))) {
            ask(DialogForm("<yellow>명령어</yellow>").line("<gray>한 줄에 하나. 콘솔이 돌립니다. {player} = 받는 사람.</gray>")
                .text("commands", "명령어", reward.commands.joinToString("\n"), maxLength = 4096, multiline = true)) { v ->
                store(reward().copy(commands = v.text("commands").lines().map(String::trim).map { it.removePrefix("/") }.filter(String::isNotEmpty)))
            }
        }
        set(SLOT_MONEY, Icon.of(Material.GOLD_INGOT, "<yellow>돈</yellow>",
            "<gray>지금: <white>${if (reward.money > 0) hub.economy.format(reward.money.toDouble()) else "없음"}</white> <dark_gray>(기본 화폐)</dark_gray></gray>", "", "<yellow>▶ 클릭: 고치기</yellow>")) {
            ask(DialogForm("<yellow>돈</yellow>").long("money", "금액(0 = 없음)", reward.money, 0)) { v -> store(reward().copy(money = (v.long("money") ?: 0).coerceAtLeast(0))) }
        }
        set(SLOT_HELP, Icon.of(Material.PAPER, "<yellow>도움말</yellow>",
            "<gray>위 네 줄에 아이템을 올려 두면 그것이 보상입니다(수량 그대로).</gray>",
            "<gray>닫거나 나갈 때 저장합니다. 올린 아이템은 돌려주지 않습니다 — 견본을 올리세요.</gray>"))
        set(SLOT_CLEAR, Icon.of(Material.LAVA_BUCKET, "<red>비우기</red>", "<gray>아이템·명령어·돈 모두.</gray>")) {
            for (s in 0 until INPUT_END) inventory.setItem(s, null)
            store(Reward())
            refresh()
        }
        set(SLOT_BACK, Icon.back()) { back() }
        set(SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    override fun isSlotEditable(slot: Int): Boolean = slot < INPUT_END

    override fun acceptsShiftInsert(): Boolean = true

    override fun onClose(event: InventoryCloseEvent) = persist()

    companion object {
        const val SIZE = 54
        const val INPUT_END = 36
        const val SLOT_BACK = 45
        const val SLOT_COMMANDS = 47
        const val SLOT_MONEY = 48
        const val SLOT_HELP = 49
        const val SLOT_CLEAR = 51
        const val SLOT_CLOSE = 53
    }
}
