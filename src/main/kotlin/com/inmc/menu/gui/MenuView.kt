package com.inmc.menu.gui

import com.inmc.menu.Hub
import com.inmc.menu.menu.Actions
import com.inmc.menu.menu.ButtonDef
import com.inmc.menu.menu.MenuDef
import com.inmc.menu.menu.Special
import com.inmc.menu.pack.Glyphs
import com.inmc.menu.pack.MenuArt
import com.inmc.menu.util.Ph
import kr.inmc.core.gui.Icon
import kr.inmc.core.util.Durations
import org.bukkit.Material
import org.bukkit.Statistic
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

/**
 * 메뉴 하나를 그 사람에게 그린다.
 *
 * - **리소스팩 화면**(팩을 받은 사람): 제목이 배경 그림 글자이고, 버튼의 칸마다 투명 아이템에 이름·설명 — 그림 어디에 마우스를 올려도 같은 이름.
 * - **일반 화면**(안 받은 사람·그림 전): 버튼의 왼쪽 위 칸에 대표 아이템, 나머지 칸은 같은 이름의 유리.
 *
 * 권한이 없는 버튼은 숨기거나(`hide-locked`) 잠금 줄을 단다. 누르면 왼쪽·오른쪽 동작 목록을 돌린다.
 */
class MenuView(
    hub: Hub,
    viewer: Player,
    private val def: MenuDef,
    private val background: Char? = hub.art.backgroundOf(def.id)?.takeIf { hub.usesPack(viewer) },
) : Menu(hub, viewer, def.rows * 9, MenuArt.titled(background, def.title, viewer)) {

    override fun draw() {
        clear()
        val ph = Ph.of().player(viewer.name).playtime(playtime(viewer))
        for (button in def.buttons) {
            val allowed = button.permission.isBlank() || viewer.hasPermission(button.permission)
            if (!allowed && button.hideLocked) continue
            val lore = if (allowed) button.lore else button.lore + listOf("", "<red>🔒 권한이 없습니다</red>")
            val onClick: (org.bukkit.event.inventory.InventoryClickEvent) -> Unit = { event -> click(button, allowed, event.click) }
            for (slot in button.slots()) {
                val stack = when {
                    button.special == Special.PROFILE && slot == button.anchor -> head()
                    background != null -> hub.art.cellItem(def.id, slot) ?: Glyphs.blank()
                    slot == button.anchor -> hub.resolver.icon(button.icon).stack.clone().also { it.amount = 1 }
                    else -> ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                }
                set(slot, named(stack, button.name, lore, ph, viewer), onClick)
            }
        }
        if (background == null) fillEmpty(Icon.FILLER)
    }

    private fun click(button: ButtonDef, allowed: Boolean, click: ClickType) {
        if (!allowed) {
            hub.messages.send(viewer, "locked")
            return
        }
        Actions.run(hub, viewer, if (click.isRightClick && button.right.isNotEmpty()) button.right else button.left)
    }

    private fun head(): ItemStack = ItemStack(Material.PLAYER_HEAD).also { stack ->
        stack.editMeta(SkullMeta::class.java) { it.owningPlayer = viewer }
    }

    companion object {

        /** 플레이 시간 — 바닐라 통계(틱). 접속 중에만 읽힌다(ARCHITECTURE "서버 없이 못 부르는 API"). */
        fun playtime(player: Player): String =
            Durations.format(runCatching { player.getStatistic(Statistic.PLAY_ONE_MINUTE).toLong() / 20 }.getOrDefault(0L))
    }
}
