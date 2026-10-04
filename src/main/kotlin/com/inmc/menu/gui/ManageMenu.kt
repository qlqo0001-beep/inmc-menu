package com.inmc.menu.gui

import com.inmc.menu.Hub
import com.inmc.menu.menu.ButtonDef
import com.inmc.menu.menu.MenuDef
import com.inmc.menu.util.Ph
import kr.inmc.core.gui.ConfirmMenu
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player

/**
 * `/메뉴 관리` — 메뉴 목록(편집·첫 메뉴·지우기·새 메뉴)과 서버 설정(Shift+F·리소스팩 화면·숨기기 월드).
 */
class ManageMenu(hub: Hub, viewer: Player, private var page: Int = 0) : Menu(hub, viewer, SIZE, "<dark_gray>메뉴 관리</dark_gray>") {

    override fun draw() {
        clear()
        val menus = hub.menus.all().sortedBy { it.id }
        page = Paging.clamp(page, menus.size, PER_PAGE)
        for ((slot, def) in Paging.slice(menus, page, PER_PAGE).withIndex()) {
            val main = def.id == hub.config.mainMenu
            set(slot, Icon.of(if (main) Material.ENCHANTED_BOOK else Material.BOOK, "<white>${def.id}</white>" + if (main) " <gold>(첫 메뉴)</gold>" else "",
                "<gray>제목: </gray>${def.title}", "<gray>${def.rows}줄 · 버튼 ${def.buttons.size}개</gray>", "",
                "<yellow>▶ 클릭: 편집</yellow>", "<yellow>▶ 우클릭: 열어 보기</yellow>", "<yellow>▶ Shift+좌클릭: 첫 메뉴로</yellow>", "<red>▶ Shift+우클릭: 지우기</red>")) { event ->
                when {
                    event.isShiftClick && event.isLeftClick -> {
                        hub.updateConfig { it.copy(mainMenu = def.id) }
                        refresh()
                    }
                    event.isShiftClick && event.isRightClick -> delete(def)
                    event.isRightClick -> MenuView(hub, viewer, def).show()
                    else -> EditorMenu(hub, viewer, def.id).show()
                }
            }
        }
        set(SLOT_NEW, Icon.of(Material.LIME_DYE, "<green>새 메뉴</green>", "<gray>하위 메뉴를 만들고 버튼의 \"메뉴 열기\" 동작으로 잇습니다.</gray>")) { create() }

        val c = hub.config
        set(SLOT_HOTKEY, toggleIcon("Shift+F 메뉴(서버 전체)", c.hotkey, "<gray>끄면 /메뉴 만. 사람마다 끄는 것은 개인 설정.</gray>")) {
            hub.updateConfig { it.copy(hotkey = !it.hotkey) }
            refresh()
        }
        set(SLOT_PACK, toggleIcon("리소스팩 화면", c.packEnabled, "<gray>팩을 받은 사람에게 그린 화면을 보입니다.</gray>", "<gray>끄면 모두 일반 아이콘 화면.</gray>")) {
            hub.updateConfig { it.copy(packEnabled = !it.packEnabled) }
            refresh()
        }
        set(SLOT_HIDE, Icon.of(Material.ENDER_EYE, "<yellow>숨기기 월드</yellow>",
            listOf("<gray>\"다른 플레이어 숨기기\"가 동작하는 월드.</gray>") +
                (if (c.hideWorlds.isEmpty()) listOf("<dark_gray>(없음 — 어디서도 안 숨김)</dark_gray>") else c.hideWorlds.map { "<gray>· <white>$it</white></gray>" }) +
                listOf("", "<yellow>▶ 클릭</yellow>"))) { HideWorldsMenu(hub, viewer).show() }

        set(SLOT_ART, artIcon()) {
            if (!hub.art.refresh(viewer)) hub.messages.send(viewer, "art-busy")
            refresh()
        }
        set(SLOT_HUB, Icon.of(Material.COMPASS, "<gold>어드민 메뉴로</gold>", "<gray>각 플러그인 설정 허브로 돌아갑니다.</gray>")) {
            viewer.performCommand("메뉴 어드민")
        }

        fillEmpty(Icon.FILLER)
        pager(page, menus.size, PER_PAGE) { page = it; refresh() }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun artIcon(): org.bukkit.inventory.ItemStack {
        val stale = hub.art.stale()
        val state = when {
            hub.art.busy -> "<yellow>그리는 중 · 리소스팩 만드는 중…</yellow>"
            !hub.artReady -> "<red>리소스팩에 아직 없음</red>"
            stale.isEmpty() -> "<green>모든 메뉴가 그림 화면</green>"
            else -> "<yellow>고친 뒤 안 그린 메뉴 ${stale.size}개</yellow> <gray>(${stale.take(4).joinToString(", ")}${if (stale.size > 4) " …" else ""})</gray>"
        }
        return Icon.of(Material.PAINTING, "<aqua>그림 다시 만들기</aqua>", listOf(
            state,
            "<gray>메뉴의 배치·이름·그림을 바꾸면 다시 그릴 때까지</gray>",
            "<gray>그 메뉴는 일반 아이콘 화면입니다.</gray>",
            "",
            "<gray>그린 것을 커스텀아이템 팩 소스에 넣고 팩을 다시 만듭니다.</gray>",
            "<gray>접속한 사람은 팩을 다시 받아야 그림 화면입니다.</gray>",
            "<dark_gray>직접 그린 그림: plugins/inmc-menu/art/<이름>.png (16×16)</dark_gray>",
            "",
            "<yellow>▶ 클릭: 다시 그리기</yellow>",
        ))
    }

    private fun create() {
        ask(DialogForm("<green>새 메뉴</green>").line("<gray>이름은 버튼의 \"메뉴 열기\" 값에 씁니다. 소문자 영문·숫자·한글·_-</gray>")
            .text("id", "이름", "").text("title", "제목", "새 메뉴").long("rows", "줄 수(1~6)", 3, 1, 6)) { v ->
            val id = v.text("id").trim().lowercase()
            if (!ButtonDef.ID.matches(id)) {
                hub.messages.send(viewer, "invalid-id", Ph.of().value(id))
                return@ask
            }
            if (hub.menus.get(id) != null) {
                hub.messages.send(viewer, "already-exists", Ph.of().value(id))
                return@ask
            }
            hub.menus.put(MenuDef(id, v.text("title"), (v.long("rows") ?: 3).toInt(), emptyList()))
            hub.messages.send(viewer, "menu-created", Ph.of().value(id))
            EditorMenu(hub, viewer, id).show()
        }
    }

    private fun delete(def: MenuDef) {
        if (def.id == hub.config.mainMenu) {
            hub.messages.send(viewer, "main-undeletable")
            return
        }
        ConfirmMenu(hub, "<red>'${def.id}' 메뉴를 지울까요?</red>", listOf("<gray>버튼 ${def.buttons.size}개가 같이 사라집니다.</gray>"),
            onConfirm = {
                hub.menus.delete(def.id)
                hub.messages.send(viewer, "menu-deleted", Ph.of().value(def.id))
                ManageMenu(hub, viewer, page).show()
            },
            onCancel = { ManageMenu(hub, viewer, page).show() }).open(viewer)
    }

    companion object {
        const val SIZE = 54
        const val PER_PAGE = 36
        const val SLOT_NEW = 45
        const val SLOT_HOTKEY = 48
        const val SLOT_PACK = 49
        const val SLOT_HIDE = 50
        const val SLOT_ART = 51
        const val SLOT_HUB = 52
    }
}

/** 숨기기 월드 고르기 — 서버의 월드를 눌러 넣고 뺀다. */
class HideWorldsMenu(hub: Hub, viewer: Player) : Menu(hub, viewer, SIZE, "<dark_gray>숨기기 월드</dark_gray>") {

    override val back: () -> Unit = { ManageMenu(hub, viewer).show() }

    override fun draw() {
        clear()
        val worlds = Bukkit.getWorlds().take(Paging.PER_PAGE)
        for ((slot, world) in worlds.withIndex()) {
            val on = world.name in hub.config.hideWorlds
            val stack = Icon.of(iconOf(world), "<white>${world.name}</white>", if (on) "<green>▶ 숨기기 동작함</green>" else "<gray>숨기기 안 함</gray>", "", "<yellow>▶ 클릭해서 바꾸기</yellow>")
            if (on) stack.editMeta { it.setEnchantmentGlintOverride(true) }
            set(slot, stack) {
                hub.updateConfig { c -> c.copy(hideWorlds = if (on) c.hideWorlds - world.name else c.hideWorlds + world.name) }
                hub.visibility.refreshAll()
                refresh()
            }
        }
        fillEmpty(Icon.FILLER)
        navigation()
    }

    private fun iconOf(world: World): Material = when (world.environment) {
        World.Environment.NETHER -> Material.NETHERRACK
        World.Environment.THE_END -> Material.END_STONE
        else -> Material.GRASS_BLOCK
    }

    companion object {
        const val SIZE = 54
    }
}
