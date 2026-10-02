package com.inmc.menu.gui

import com.inmc.menu.Hub
import com.inmc.menu.menu.Action
import com.inmc.menu.menu.ButtonDef
import com.inmc.menu.menu.MenuDef
import com.inmc.menu.menu.Special
import com.inmc.menu.util.Ph
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 메뉴 편집 — 메뉴를 **그대로** 보여 주고 칸을 눌러 고친다. 버튼을 누르면 그 버튼의 설정, 빈 칸을 누르면 거기에 새 버튼(손에 든 것이 아이콘),
 * 빈 칸을 Shift+클릭하면 메뉴 설정(제목·줄 수).
 *
 * 편집 화면은 정의가 아니라 **id 를 들고 있다** — 정의는 고칠 때마다 새 객체라, 옛 객체를 붙들면 두 번째 편집이 첫 번째를 지운다(커스텀아이템 규칙 24).
 */
class EditorMenu(hub: Hub, viewer: Player, private val menuId: String) :
    Menu(hub, viewer, ((hub.menus.get(menuId)?.rows ?: 6) * 9), "<dark_gray>편집: </dark_gray><white>$menuId</white>") {

    override fun draw() {
        clear()
        val def = hub.menus.get(menuId) ?: return
        val ph = Ph.of().player(viewer.name)
        for (button in def.buttons) {
            val lore = button.lore + listOf(
                "",
                "<dark_gray>${button.id} · 칸 ${button.col + 1},${button.row + 1} · ${button.width}×${button.height}</dark_gray>",
                "<dark_gray>좌클릭: ${button.left.size}개 · 우클릭: ${button.right.size}개</dark_gray>",
                "<yellow>▶ 클릭: 이 버튼 편집</yellow>",
            )
            for (slot in button.slots()) {
                val stack = if (slot == button.anchor) hub.resolver.icon(button.icon).stack.clone().also { it.amount = 1 }
                else ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                set(slot, named(stack, button.name, lore, ph, viewer)) { ButtonEditor(hub, viewer, menuId, button.id).show() }
            }
        }
        for (slot in 0 until def.rows * 9) {
            if (def.buttonAt(slot) != null) continue
            set(slot, Icon.of(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray>빈 칸 (${slot % 9 + 1}, ${slot / 9 + 1})</dark_gray>",
                "<yellow>▶ 클릭: 여기에 새 버튼</yellow>", "<gray>손에 든 아이템이 아이콘이 됩니다.</gray>", "<yellow>▶ Shift+클릭: 메뉴 설정</yellow>")) { event ->
                if (event.isShiftClick) settings(def) else create(def, slot)
            }
        }
    }

    private fun create(def: MenuDef, slot: Int) {
        val hand = viewer.inventory.itemInMainHand
        val icon = if (hand.type.isAir) StoredItem(ItemRef.parse("minecraft:paper"), Material.PAPER) else hub.resolver.capture(hand)
        val id = generateSequence(1) { it + 1 }.map { "b$it" }.first { id -> def.buttons.none { it.id == id } }
        val button = ButtonDef(id = id, col = slot % 9, row = slot / 9, name = "<white>새 버튼</white>", icon = icon)
        hub.menus.put(def.with(button))
        ButtonEditor(hub, viewer, menuId, id).show()
    }

    private fun settings(def: MenuDef) {
        ask(DialogForm("<yellow>메뉴 설정 — $menuId</yellow>")
            .line("<gray>제목은 MiniMessage. 리소스팩 화면에서는 그림 위에 이 글이 그대로 보입니다.</gray>")
            .text("title", "제목", def.title)
            .long("rows", "줄 수(1~6)", def.rows.toLong(), MenuDef.MIN_ROWS.toLong(), MenuDef.MAX_ROWS.toLong())) { v ->
            val rows = (v.long("rows") ?: def.rows.toLong()).toInt()
            val current = hub.menus.get(menuId) ?: return@ask
            if (current.buttons.any { !it.fits(rows) }) {
                hub.messages.send(viewer, "rows-blocked")
                return@ask
            }
            hub.menus.put(current.copy(title = v.text("title"), rows = rows))
            // 줄 수가 바뀌면 창 크기가 바뀌므로 새로 연다.
            EditorMenu(hub, viewer, menuId).show()
        }
    }
}

/** 버튼 하나의 설정. */
class ButtonEditor(hub: Hub, viewer: Player, private val menuId: String, private val buttonId: String) :
    Menu(hub, viewer, SIZE, "<dark_gray>버튼 편집: </dark_gray><white>$buttonId</white>") {

    override val back: () -> Unit = { EditorMenu(hub, viewer, menuId).show() }

    private fun current(): Pair<MenuDef, ButtonDef>? {
        val def = hub.menus.get(menuId) ?: return null
        val button = def.buttons.firstOrNull { it.id == buttonId } ?: return null
        return def to button
    }

    private fun save(change: (ButtonDef) -> ButtonDef): Boolean {
        val (def, button) = current() ?: return false
        val changed = change(button)
        if (!def.canPlace(changed)) {
            hub.messages.send(viewer, "editor-blocked")
            return false
        }
        hub.menus.put(def.with(changed))
        return true
    }

    override fun draw() {
        clear()
        val (_, button) = current() ?: return
        val ph = Ph.of().player(viewer.name)
        set(SLOT_PREVIEW, named(hub.resolver.icon(button.icon).stack.clone().also { it.amount = 1 }, button.name, button.lore, ph, viewer))

        set(SLOT_NAME, valueIcon(Material.NAME_TAG, "이름", button.name, "<gray>MiniMessage. {플레이어} 와 PlaceholderAPI 를 씁니다.</gray>")) {
            ask(DialogForm("<yellow>버튼 이름</yellow>").text("name", "이름", button.name)) { v -> save { it.copy(name = v.text("name")) } }
        }
        set(SLOT_LORE, Icon.of(Material.WRITABLE_BOOK, "<yellow>설명</yellow>", button.lore.ifEmpty { listOf("<dark_gray>(없음)</dark_gray>") } + listOf("", "<gray>클릭해서 바꾸기 — 한 줄에 한 줄</gray>"))) {
            ask(DialogForm("<yellow>버튼 설명</yellow>").line("<gray>줄마다 한 줄. 마우스를 올리면 보입니다.</gray>")
                .text("lore", "설명", button.lore.joinToString("\n"), maxLength = 2000, multiline = true)) { v ->
                save { it.copy(lore = v.text("lore").lines().map(String::trimEnd).dropLastWhile(String::isEmpty)) }
            }
        }
        set(SLOT_ICON, Icon.of(button.icon.material, "<yellow>아이콘</yellow>", "<gray>리소스팩을 안 받은 사람에게 보이는 아이템.</gray>", "", "<yellow>▶ 손에 들고 클릭</yellow>")) {
            val hand = viewer.inventory.itemInMainHand
            if (hand.type.isAir) {
                hub.messages.send(viewer, "hand-empty")
                return@set
            }
            if (save { it.copy(icon = hub.resolver.capture(hand)) }) refresh()
        }
        set(SLOT_ART, valueIcon(Material.PAINTING, "그림", button.art.ifBlank { "(판만)" },
            "<gray>리소스팩 화면의 배경에 그릴 그림 이름.</gray>", "<gray>기본 그림 이름이나 plugins/inmc-menu/art/ 의 png 이름.</gray>",
            "<dark_gray>그림을 바꾸면 /메뉴 관리 → 그림 다시 만들기</dark_gray>")) {
            ask(DialogForm("<yellow>그림</yellow>").text("art", "그림 이름(비우면 판만)", button.art)) { v -> save { it.copy(art = v.text("art").trim()) } }
        }
        set(SLOT_PERMISSION, Icon.of(Material.IRON_BARS, "<yellow>권한</yellow>",
            "<gray>지금: <white>${button.permission.ifBlank { "(누구나)" }}</white></gray>",
            "<gray>권한이 없으면: <white>${if (button.hideLocked) "숨김" else "잠금 표시"}</white></gray>",
            "", "<yellow>▶ 좌클릭: 권한 · 우클릭: 숨김/잠금</yellow>")) { event ->
            if (event.isRightClick) {
                if (save { it.copy(hideLocked = !it.hideLocked) }) refresh()
                return@set
            }
            ask(DialogForm("<yellow>권한</yellow>").text("permission", "권한(비우면 누구나)", button.permission)) { v -> save { it.copy(permission = v.text("permission").trim()) } }
        }
        val specials = listOf<Special?>(null) + Special.entries
        set(SLOT_SPECIAL, Icon.of(Material.PLAYER_HEAD, "<yellow>특별: <white>${button.special?.label ?: "없음"}</white></yellow>",
            listOf("<gray>내 정보 = 그 칸에 누른 사람의 머리를 그린다.</gray>", "") + Editors.optionList(specials, button.special) { it?.label ?: "없음" } + Editors.cycleHint)) { event ->
            if (save { it.copy(special = Editors.cycle(event, specials, it.special)) }) refresh()
        }
        set(SLOT_PLACE, Icon.of(Material.COMPASS, "<yellow>자리 · 크기</yellow>",
            "<gray>칸 <white>${button.col + 1}, ${button.row + 1}</white> · 크기 <white>${button.width}×${button.height}</white></gray>",
            "<gray>큰 버튼(2×2 등)은 리소스팩 그림도 그만큼 큽니다.</gray>", "", "<yellow>▶ 클릭</yellow>")) {
            val rows = hub.menus.get(menuId)?.rows ?: 6
            ask(DialogForm("<yellow>자리 · 크기</yellow>").line("<gray>칸은 왼쪽 위 기준, 1부터.</gray>")
                .long("col", "열(1~9)", (button.col + 1).toLong(), 1, 9)
                .long("row", "줄(1~$rows)", (button.row + 1).toLong(), 1, rows.toLong())
                .long("width", "너비", button.width.toLong(), 1, 9)
                .long("height", "높이", button.height.toLong(), 1, rows.toLong())) { v ->
                save {
                    it.copy(
                        col = (v.long("col") ?: 1).toInt() - 1, row = (v.long("row") ?: 1).toInt() - 1,
                        width = (v.long("width") ?: 1).toInt(), height = (v.long("height") ?: 1).toInt(),
                    )
                }
            }
        }
        set(SLOT_LEFT, Icon.of(Material.LIME_DYE, "<green>좌클릭 동작</green>", actionLore(button.left))) { ActionListMenu(hub, viewer, menuId, buttonId, right = false).show() }
        set(SLOT_RIGHT, Icon.of(Material.ORANGE_DYE, "<gold>우클릭 동작</gold>", actionLore(button.right) + listOf("<dark_gray>비우면 우클릭도 좌클릭 동작</dark_gray>"))) {
            ActionListMenu(hub, viewer, menuId, buttonId, right = true).show()
        }
        set(SLOT_DELETE, Icon.of(Material.TNT, "<red>이 버튼 지우기</red>", "", "<red>▶ Shift+클릭</red>")) { event ->
            if (!event.isShiftClick) return@set
            val def = hub.menus.get(menuId) ?: return@set
            hub.menus.put(def.without(buttonId))
            hub.messages.send(viewer, "button-deleted")
            back()
        }
        fillEmpty(Icon.FILLER)
        navigation(SLOT_BACK, SLOT_CLOSE)
    }

    private fun actionLore(actions: List<Action>): List<String> =
        (if (actions.isEmpty()) listOf("<dark_gray>(없음)</dark_gray>") else actions.map { "<gray>· ${it.describe()}</gray>" }) + listOf("", "<yellow>▶ 클릭: 고치기</yellow>")

    companion object {
        const val SIZE = 27
        const val SLOT_PREVIEW = 4
        const val SLOT_NAME = 9
        const val SLOT_LORE = 10
        const val SLOT_ICON = 11
        const val SLOT_ART = 12
        const val SLOT_PERMISSION = 13
        const val SLOT_SPECIAL = 14
        const val SLOT_PLACE = 15
        const val SLOT_LEFT = 16
        const val SLOT_RIGHT = 17
        const val SLOT_BACK = 18
        const val SLOT_DELETE = 22
        const val SLOT_CLOSE = 26
    }
}

/** 버튼의 동작 목록 — 누르면 빼고, [추가]로 넣는다. 위에서부터 차례로 돈다. */
class ActionListMenu(hub: Hub, viewer: Player, private val menuId: String, private val buttonId: String, private val right: Boolean) :
    Menu(hub, viewer, SIZE, "<dark_gray>${if (right) "우클릭" else "좌클릭"} 동작</dark_gray>") {

    override val back: () -> Unit = { ButtonEditor(hub, viewer, menuId, buttonId).show() }

    private fun actions(): List<Action> {
        val button = hub.menus.get(menuId)?.buttons?.firstOrNull { it.id == buttonId } ?: return emptyList()
        return if (right) button.right else button.left
    }

    private fun save(actions: List<Action>) {
        val def = hub.menus.get(menuId) ?: return
        val button = def.buttons.firstOrNull { it.id == buttonId } ?: return
        hub.menus.put(def.with(if (right) button.copy(right = actions) else button.copy(left = actions)))
    }

    override fun draw() {
        clear()
        val list = actions()
        for ((index, action) in list.take(MAX).withIndex()) {
            set(index, Icon.of(Material.PAPER, "<white>${index + 1}. ${action.type.label}</white>",
                "<gray>${action.value().ifBlank { "-" }}</gray>", "", "<red>▶ 클릭: 빼기</red>")) {
                save(list.filterIndexed { i, _ -> i != index })
                refresh()
            }
        }
        set(SLOT_ADD, Icon.of(Material.LIME_DYE, "<green>동작 추가</green>", Action.Type.entries.map { "<gray>· ${it.label} — ${it.hint}</gray>" })) {
            ask(DialogForm("<green>동작 추가</green>")
                .choice("type", "종류", Action.Type.entries.map { it.id to it.label }, Action.Type.PLAYER.id)
                .text("value", "값", "")) { v ->
                val type = Action.Type.parse(v.choice("type")) ?: return@ask
                val action = Action.of(type, v.text("value")) ?: return@ask
                save(actions() + action)
            }
        }
        fillEmpty(Icon.FILLER)
        navigation(SLOT_BACK, SLOT_CLOSE)
    }

    companion object {
        const val SIZE = 27
        const val MAX = 18
        const val SLOT_BACK = 18
        const val SLOT_ADD = 22
        const val SLOT_CLOSE = 26
    }
}
