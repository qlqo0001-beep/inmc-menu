package com.inmc.menu.gui

import com.inmc.menu.Hub
import com.inmc.menu.menu.Actions
import com.inmc.menu.pack.MenuArt
import com.inmc.menu.rtp.RtpWorld
import com.inmc.menu.rtp.Shape
import com.inmc.menu.util.Ph
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Durations
import kr.inmc.core.util.Text
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** 아이콘 — 아이템이 아닌 재질(블록 전용 …)이면 풀 블록. */
private fun stackOf(material: Material): ItemStack = runCatching { ItemStack(material) }.getOrElse { ItemStack(Material.GRASS_BLOCK) }

/**
 * `/야생` — 갈 월드 고르기. 월드는 가운데 줄에 모아 놓는다. 누르면 화면을 닫고 3·2·1(그동안 자리를 찾는다).
 */
class RtpMenu(
    hub: Hub,
    viewer: Player,
    override val back: (() -> Unit)? = { Actions.openMain(hub, viewer) },
    private val grid: Char? = hub.art.gridFor(viewer, SIZE / 9),
) : Menu(hub, viewer, SIZE, MenuArt.titled(grid, "랜덤 이동", viewer)) {

    override fun draw() {
        clear()
        val worlds = hub.rtpWorlds.usable().filter { it.permission.isBlank() || viewer.hasPermission(it.permission) }.take(9)
        if (worlds.isEmpty()) set(SLOT_EMPTY, Icon.of(Material.BARRIER, "<gray>지금 갈 수 있는 월드가 없습니다.</gray>"))
        for ((slot, world) in slots(worlds.size).zip(worlds)) {
            set(slot, icon(world)) { hub.rtp.request(viewer, world) }
        }
        if (grid == null) fillEmpty(Icon.FILLER)
        navigation(SLOT_BACK, SLOT_CLOSE)
    }

    private fun icon(world: RtpWorld): ItemStack {
        val bypass = viewer.hasPermission(Hub.RTP_BYPASS)
        val left = if (bypass) 0 else hub.rtp.cooldownLeft(viewer.uniqueId, world)
        val lore = buildList {
            add("<gray>거리: <white>${world.min} ~ ${world.max}</white> 블록</gray>")
            add("<gray>비용: <white>${if (world.cost <= 0 || bypass) "무료" else hub.rtp.formatCost(world)}</white></gray>")
            if (world.cooldown > 0) add("<gray>쿨타임: <white>${Durations.format(world.cooldown.toLong())}</white></gray>")
            add("")
            add(if (left > 0) "<red>${Durations.format(left)} 뒤에 다시 쓸 수 있습니다</red>" else "<yellow>▶ 클릭: 이동</yellow> <dark_gray>(${world.countdown}초 동안 움직이지 마세요)</dark_gray>")
        }
        return named(stackOf(world.icon), world.name, lore, null, viewer)
    }

    companion object {
        const val SIZE = 27
        const val SLOT_EMPTY = 13
        const val SLOT_BACK = 18
        const val SLOT_CLOSE = 26

        /** 가운데 줄 — 다섯 개까지는 한 칸씩 띄우고 그보다 많으면 붙여서, 가운데 맞춤. */
        fun slots(count: Int): List<Int> = when {
            count <= 0 -> emptyList()
            count <= 5 -> (0 until count).map { 9 + (9 - (2 * count - 1)) / 2 + 2 * it }
            else -> (0 until count.coerceAtMost(9)).map { 9 + (9 - count.coerceAtMost(9)) / 2 + it }
        }
    }
}

/**
 * `/야생 관리` — 서버의 월드마다 랜덤 TP 설정. 서버 전체 한도(동시 찾기 수 …)도 여기서.
 */
class RtpAdminMenu(hub: Hub, viewer: Player) : Menu(hub, viewer, SIZE, "랜덤 TP 관리") {

    override fun draw() {
        clear()
        val names = (Bukkit.getWorlds().map { it.name } + hub.rtpWorlds.worlds.map { it.world }).distinct()
        for ((slot, name) in names.take(PER_PAGE).withIndex()) {
            val loaded = Bukkit.getWorld(name)
            val settings = hub.rtpWorlds.get(name) ?: loaded?.let { hub.rtpWorlds.orDefault(it) } ?: continue
            set(slot, icon(settings, loaded)) { event ->
                when {
                    event.isShiftClick && event.isLeftClick -> {
                        val hand = viewer.inventory.itemInMainHand
                        if (hand.type.isAir) return@set hub.messages.send(viewer, "hand-empty")
                        save(settings.copy(icon = hand.type))
                    }
                    event.isShiftClick && event.isRightClick -> {
                        hub.rtp.clearMarks(name)
                        hub.messages.send(viewer, "rtp-marks-cleared", Ph.of().world(name))
                        refresh()
                    }
                    event.isRightClick -> save(settings.copy(enabled = !settings.enabled))
                    else -> edit(settings)
                }
            }
        }
        val c = hub.config.rtp
        set(SLOT_LIMITS, Icon.of(Material.CLOCK, "<aqua>서버 전체 한도</aqua>",
            "<gray>동시 찾기: <white>${c.maxSearches}</white> · 대기열: <white>${c.queueSize}</white></gray>",
            "<gray>바쁨 기준: <white>${if (c.busyMspt > 0) "${c.busyMspt}ms" else "안 봄"}</white> · 찾기마다 청크: <white>${c.chunkTries}</white></gray>",
            "<gray>지금 찾는 중 <white>${hub.rtp.searching}</white> · 대기 <white>${hub.rtp.waiting}</white></gray>",
            "", "<yellow>▶ 클릭: 고치기</yellow>", "<dark_gray>/야생 상태 — 월드마다 걸린 시간·성공률</dark_gray>")) { editLimits() }
        fillEmpty(Icon.FILLER)
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun icon(settings: RtpWorld, loaded: World?): ItemStack {
        val lore = buildList {
            add(if (settings.enabled) "<green>켜짐</green>" else "<red>꺼짐</red>")
            if (loaded == null) add("<red>서버에 열려 있지 않은 월드</red>")
            add("<gray>이름: </gray>${settings.name}")
            add("<gray>거리: <white>${settings.min} ~ ${settings.max}</white> (${settings.shape.label}) · 중심 <white>${settings.centerX}, ${settings.centerZ}</white></gray>")
            add("<gray>카운트다운 <white>${settings.countdown}초</white> · 쿨타임 <white>${Durations.format(settings.cooldown.toLong())}</white></gray>")
            add("<gray>비용: <white>${if (settings.cost <= 0) "무료" else "${settings.cost} (${settings.currency.ifBlank { "기본 화폐" }})"}</white></gray>")
            add("<gray>권한: <white>${settings.permission.ifBlank { "누구나" }}</white></gray>")
            add("<gray>피할 생물군계 <white>${settings.avoidBiomes.size}</white>개 · 실패 구역 <white>${hub.rtp.marksCount(settings.world)}</white>청크</gray>")
            add("")
            add("<yellow>▶ 클릭: 고치기</yellow>")
            add("<yellow>▶ 우클릭: 켜기/끄기</yellow>")
            add("<yellow>▶ Shift+좌클릭: 손에 든 것을 그림으로</yellow>")
            add("<red>▶ Shift+우클릭: 실패 구역 지우기</red> <dark_gray>(지형을 바꿨을 때)</dark_gray>")
        }
        val stack = named(stackOf(settings.icon), "<white>${settings.world}</white>", lore, null, viewer)
        if (settings.enabled) stack.editMeta { it.setEnchantmentGlintOverride(true) }
        return stack
    }

    private fun save(settings: RtpWorld) {
        hub.rtpWorlds.put(settings)
        refresh()
    }

    private fun edit(w: RtpWorld) {
        val form = DialogForm("<yellow>${w.world} 랜덤 TP</yellow>")
            .line("<gray>누를 때마다 이 고리 안의 새 무작위 자리로 갑니다. 거리는 중심에서의 블록 수.</gray>")
            .text("name", "이름(MiniMessage)", w.name)
            .long("min", "최소 거리", w.min.toLong(), 0, RtpWorld.MAX_RANGE.toLong())
            .long("max", "최대 거리", w.max.toLong(), RtpWorld.MIN_RANGE.toLong(), RtpWorld.MAX_RANGE.toLong())
            .long("cx", "중심 X", w.centerX.toLong())
            .long("cz", "중심 Z", w.centerZ.toLong())
            .choice("shape", "모양", Shape.entries.map { it.name to it.label }, w.shape.name)
            .long("countdown", "카운트다운(초, 0~5)", w.countdown.toLong(), 0, 5)
            .long("cooldown", "쿨타임(초)", w.cooldown.toLong(), 0, 86_400)
            .long("cost", "비용(0 = 무료)", w.cost, 0)
            .text("currency", "화폐 id(빈 칸 = 기본 화폐)", w.currency)
            .text("permission", "권한(빈 칸 = 누구나)", w.permission)
            .text("biomes", "피할 생물군계(쉼표로 · 예: ocean, river)", w.avoidBiomes.joinToString(", "), maxLength = 4096, multiline = true)
        ask(form) { v ->
            val updated = w.copy(
                name = v.text("name").ifBlank { w.world },
                min = (v.long("min") ?: w.min.toLong()).toInt(),
                max = (v.long("max") ?: w.max.toLong()).toInt(),
                centerX = (v.long("cx") ?: w.centerX.toLong()).toInt(),
                centerZ = (v.long("cz") ?: w.centerZ.toLong()).toInt(),
                shape = Shape.entries.firstOrNull { it.name == v.choice("shape") } ?: w.shape,
                countdown = (v.long("countdown") ?: w.countdown.toLong()).toInt(),
                cooldown = (v.long("cooldown") ?: w.cooldown.toLong()).toInt(),
                cost = v.long("cost") ?: w.cost,
                currency = v.text("currency").trim(),
                permission = v.text("permission").trim(),
                avoidBiomes = v.text("biomes").split(',', '\n').map(String::trim).filter(String::isNotEmpty),
            )
            hub.rtpWorlds.put(updated)
            hub.messages.send(viewer, "rtp-saved", Ph.of().world(w.world))
        }
    }

    private fun editLimits() {
        val c = hub.config.rtp
        ask(DialogForm("<aqua>랜덤 TP 서버 전체 한도</aqua>")
            .line("<gray>동시에 자리를 찾는 수를 넘으면 줄을 섭니다 — 한꺼번에 몰려도 청크 생성이 쌓이지 않게.</gray>")
            .long("max", "동시 찾기 수(1~16)", c.maxSearches.toLong(), 1, 16)
            .long("queue", "대기열 길이(0~200)", c.queueSize.toLong(), 0, 200)
            .decimal("mspt", "바쁨 기준 평균 틱(ms, 0 = 안 봄)", c.busyMspt, 0.0, 1000.0)
            .long("tries", "찾기마다 읽어 볼 청크 수(1~64)", c.chunkTries.toLong(), 1, 64)) { v ->
            hub.updateConfig {
                it.copy(rtp = it.rtp.copy(
                    maxSearches = (v.long("max") ?: c.maxSearches.toLong()).toInt().coerceIn(1, 16),
                    queueSize = (v.long("queue") ?: c.queueSize.toLong()).toInt().coerceIn(0, 200),
                    busyMspt = (v.decimal("mspt") ?: c.busyMspt).coerceAtLeast(0.0),
                    chunkTries = (v.long("tries") ?: c.chunkTries.toLong()).toInt().coerceIn(1, 64),
                ))
            }
        }
    }

    companion object {
        const val SIZE = 54
        const val PER_PAGE = 45
        const val SLOT_LIMITS = 49
    }
}

/** `/야생 상태` 의 줄 — 관리자용(메시지 파일이 아니라 여기서 그린다). */
object RtpStatus {

    fun lines(hub: Hub): List<String> = buildList {
        val c = hub.config.rtp
        add("<gray>────── <aqua>랜덤 TP</aqua> ──────</gray>")
        add("<gray>찾는 중 <white>${hub.rtp.searching}</white>/${c.maxSearches} · 대기 <white>${hub.rtp.waiting}</white>/${c.queueSize} · " +
            "평균 틱 <white>${"%.1f".format(Bukkit.getAverageTickTime())}</white>ms" + (if (c.busyMspt > 0) " (바쁨 기준 ${c.busyMspt})" else "") + "</gray>")
        for (w in hub.rtpWorlds.worlds) {
            val s = hub.rtp.stats.of(w.world)
            val done = s.success + s.failed
            val rate = if (done > 0) " (${s.success * 100 / done}%)" else ""
            add("${Text.plain(w.name).let { "<white>$it</white>" }} <dark_gray>${w.world}${if (w.enabled) "" else " · 꺼짐"}</dark_gray> " +
                "<gray>— 요청 <white>${s.requests}</white> · 성공 <white>${s.success}</white>$rate · 실패 <white>${s.failed}</white> · 취소 <white>${s.cancelled}</white></gray>")
            val avg = if (s.found > 0) s.searchMillis / s.found else 0
            val perRequest = if (s.requests > 0) "%.1f".format(s.chunkLoads.toDouble() / s.requests) else "0"
            add("  <gray>찾기 평균 <white>${avg}ms</white> · 최대 <white>${s.maxSearchMillis}ms</white> · 청크 읽기 <white>${s.chunkLoads}</white>번" +
                "(요청마다 $perRequest) · 실패 구역 <white>${hub.rtp.marksCount(w.world)}</white>청크</gray>")
        }
    }
}
