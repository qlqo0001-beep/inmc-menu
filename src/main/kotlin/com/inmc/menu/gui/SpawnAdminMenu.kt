package com.inmc.menu.gui

import com.inmc.menu.Hub
import com.inmc.menu.spawn.SpawnPoint
import com.inmc.menu.util.Ph
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Icon
import kr.inmc.core.util.Durations
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * `/스폰 관리` — 서버 스폰 자리 · 접속할 때 보내기 · `/스폰` 대기와 쿨타임 · **부활 순서**(눌러서 앞뒤로 옮기고 끄기).
 */
class SpawnAdminMenu(hub: Hub, viewer: Player) : Menu(hub, viewer, SIZE, "스폰 관리") {

    override fun draw() {
        clear()
        val c = hub.spawn.config
        set(SLOT_POINT, Icon.of(Material.RED_BED, "<yellow>서버 스폰</yellow>",
            "<gray>지금: <white>${c.point?.describe() ?: "정하지 않음 — 기본 월드의 스폰"}</white></gray>", "",
            "<yellow>▶ 클릭: 내가 선 곳으로 정하기</yellow>", "<yellow>▶ 우클릭: 그곳으로 가 보기</yellow>")) { event ->
            if (event.isRightClick) {
                viewer.closeInventory()
                viewer.teleportAsync(hub.spawn.target())
                return@set
            }
            hub.spawn.update { it.copy(point = SpawnPoint.of(viewer.location)) }
            hub.messages.send(viewer, "spawn-set", Ph.of().value(SpawnPoint.of(viewer.location).describe()))
            refresh()
        }
        set(SLOT_FIRST, toggleIcon("처음 접속 → 서버 스폰", c.firstJoin, "<gray>처음 들어온 사람을 서버 스폰에서 시작하게.</gray>")) {
            hub.spawn.update { it.copy(firstJoin = !it.firstJoin) }
            refresh()
        }
        set(SLOT_EVERY, toggleIcon("매 접속 → 서버 스폰", c.everyJoin, "<gray>들어올 때마다 서버 스폰에서.</gray>", "<gray>끄면 나간 자리에서.</gray>")) {
            hub.spawn.update { it.copy(everyJoin = !it.everyJoin) }
            refresh()
        }
        set(SLOT_TIMING, Icon.of(Material.CLOCK, "<yellow>/스폰 대기·쿨타임</yellow>",
            "<gray>대기: <white>${c.wait}초</white> <dark_gray>(움직이거나 맞으면 취소)</dark_gray></gray>",
            "<gray>쿨타임: <white>${if (c.cooldown > 0) Durations.format(c.cooldown.toLong()) else "없음"}</white></gray>", "", "<yellow>▶ 클릭: 고치기</yellow>")) {
            ask(DialogForm("<yellow>/스폰 대기·쿨타임</yellow>")
                .long("wait", "대기(초, 0~10)", c.wait.toLong(), 0, 10)
                .long("cooldown", "쿨타임(초)", c.cooldown.toLong(), 0, 86_400)) { v ->
                hub.spawn.update { it.copy(wait = (v.long("wait") ?: it.wait.toLong()).toInt().coerceIn(0, 10), cooldown = (v.long("cooldown") ?: it.cooldown.toLong()).toInt().coerceIn(0, 86_400)) }
            }
        }

        set(SLOT_ORDER_INFO, Icon.of(Material.BOOK, "<aqua>부활 순서</aqua>",
            "<gray>죽으면 왼쪽부터 처음 되는 곳으로 부활합니다.</gray>",
            "<gray>꺼진 것은 건너뛰고, 다 안 되면 바닐라 그대로.</gray>",
            "<gray>엔드에서 돌아올 때는 건드리지 않습니다.</gray>", "",
            "<red>CMI 등 다른 플러그인의 스폰·부활 처리는 꺼 두세요.</red>"))
        for ((index, step) in c.respawn.withIndex()) {
            val material = when (step.source) {
                com.inmc.menu.spawn.Source.BED -> Material.RED_BED
                com.inmc.menu.spawn.Source.ANCHOR -> Material.RESPAWN_ANCHOR
                com.inmc.menu.spawn.Source.SERVER -> Material.BEACON
                com.inmc.menu.spawn.Source.WORLD -> Material.GRASS_BLOCK
            }
            val stack = Icon.of(material, "<white>${index + 1}. ${step.source.label}</white> " + Icon.toggle(step.enabled),
                "<gray>${step.source.description}</gray>", "",
                "<yellow>▶ 클릭: 앞으로</yellow>", "<yellow>▶ 우클릭: 뒤로</yellow>", "<yellow>▶ Shift+클릭: 켜기/끄기</yellow>")
            if (step.enabled) stack.editMeta { it.setEnchantmentGlintOverride(true) }
            set(SLOT_ORDER + index, stack) { event ->
                hub.spawn.update { when {
                    event.isShiftClick -> it.toggle(index)
                    event.isRightClick -> it.move(index, +1)
                    else -> it.move(index, -1)
                } }
                refresh()
            }
        }
        fillEmpty(Icon.FILLER)
        set(SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    companion object {
        const val SIZE = 36
        const val SLOT_POINT = 10
        const val SLOT_FIRST = 12
        const val SLOT_EVERY = 13
        const val SLOT_TIMING = 15
        const val SLOT_ORDER_INFO = 19
        const val SLOT_ORDER = 21
        const val SLOT_CLOSE = 35
    }
}
